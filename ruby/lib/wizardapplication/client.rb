# frozen_string_literal: true

# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

require "json"
require "net/http"
require "uri"

module WizardApplication
  # Client for one workspace's Wizard Application API.
  #
  #   client = WizardApplication::Client.new(api_key: "wa_...", workspace: "acme")
  #   page = client.jobs.list(status: "published")
  class Client
    WORKSPACE_RE = /\A[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\z/.freeze
    LOCAL_HOSTS = %w[localhost 127.0.0.1 ::1].freeze
    RETRY_STATUSES = [502, 503, 504].freeze
    IDEMPOTENT = %i[get put delete].freeze
    MAX_RETRY_WAIT = 60
    NETWORK_ERRORS = [IOError, EOFError, SocketError, SystemCallError, Timeout::Error, OpenSSL::SSL::SSLError].freeze

    attr_reader :base_url, :timeout, :max_retries, :jobs, :candidates, :companies, :bookings, :leads, :content_types
    # Rate limit headers from the latest response: { limit:, remaining:, reset: }.
    attr_reader :last_rate_limit

    # api_key defaults to ENV["WIZARDAPP_API_KEY"] and workspace to
    # ENV["WIZARDAPP_WORKSPACE"]. base_url replaces workspace and must be https;
    # allow_http permits a plain http base_url (local testing only).
    def initialize(api_key: nil, workspace: nil, base_url: nil, timeout: 30, max_retries: 2, allow_http: false)
      api_key ||= ENV.fetch("WIZARDAPP_API_KEY", nil)
      raise ArgumentError, "An API key is required (pass api_key: or set WIZARDAPP_API_KEY)." if api_key.nil? || api_key.empty?
      raise ArgumentError, "The API key must not contain whitespace." if api_key.match?(/\s/)

      @api_key = api_key
      workspace ||= ENV.fetch("WIZARDAPP_WORKSPACE", nil) if base_url.nil?
      @base_url = resolve_base_url(workspace, base_url, allow_http)
      @timeout = timeout
      @max_retries = [max_retries.to_i, 0].max
      @last_rate_limit = nil

      @jobs = Resource.new(self, "jobs")
      @candidates = Resource.new(self, "candidates")
      @companies = Resource.new(self, "companies")
      @bookings = Resource.new(self, "bookings")
      @leads = Leads.new(self, "leads")
      @content_types = ContentTypes.new(self)
    end

    # Items of one content type, by its slug or ID (e.g. "blog-posts").
    def content(content_type)
      Resource.new(self, "content/#{Resource.segment(content_type)}")
    end

    # Never shows the API key.
    def inspect
      "#<WizardApplication::Client base_url=#{@base_url.inspect}>"
    end

    # Sends a request and returns the decoded JSON body (nil when empty).
    # Retries a 429 after Retry-After, and network errors and 502/503/504 on
    # GET, PUT and DELETE, up to max_retries times.
    def request(method, path, params: nil, body: nil)
      method = method.to_s.downcase.to_sym
      uri = URI.parse(@base_url + path)
      query = encode_params(params)
      uri.query = query unless query.empty?
      payload = body.nil? ? nil : JSON.generate(body)
      idempotent = IDEMPOTENT.include?(method)
      attempt = 0

      loop do
        response = begin
          perform(method, uri, payload)
        rescue *NETWORK_ERRORS => e
          if attempt < @max_retries && idempotent
            attempt += 1
            sleep(backoff(attempt, nil))
            next
          end
          raise ConnectionError, "Could not reach #{@base_url}: #{e.message}"
        end

        remember_rate_limit(response)
        decoded = decode(response.body)
        status = response.code.to_i
        return decoded if status.between?(200, 299)
        raise ConnectionError, "Redirect refused: the API key is never sent to another URL." if status.between?(300, 399)

        retry_after = Integer(response["Retry-After"], exception: false)
        error = Error.for_status(status, decoded, retry_after)
        retry_it = status == 429 ? error.code != "monthly_limit_exceeded" : RETRY_STATUSES.include?(status) && idempotent
        if retry_it && attempt < @max_retries
          attempt += 1
          sleep(backoff(attempt, error.retry_after))
          next
        end
        raise error
      end
    end

    private

    def perform(method, uri, payload)
      klass = { get: Net::HTTP::Get, post: Net::HTTP::Post, put: Net::HTTP::Put, delete: Net::HTTP::Delete }.fetch(method)
      req = klass.new(uri)
      req["Authorization"] = "Bearer #{@api_key}"
      req["Accept"] = "application/json"
      req["User-Agent"] = "wizardapplication-ruby/#{VERSION}"
      if payload
        req["Content-Type"] = "application/json"
        req.body = payload
      elsif req.request_body_permitted?
        req["Content-Type"] = "application/json"
        req.body = ""
      end
      # Net::HTTP never follows redirects, so the key stays with this host.
      Net::HTTP.start(uri.host, uri.port, use_ssl: uri.scheme == "https", open_timeout: [@timeout, 10].min,
                                          read_timeout: @timeout, write_timeout: @timeout) do |http|
        http.request(req)
      end
    end

    def resolve_base_url(workspace, base_url, allow_http)
      if base_url && !base_url.empty?
        uri = URI.parse(base_url)
        raise ArgumentError, "base_url must be a full URL, for example https://acme.wizardapp.io/api/v1" unless %w[https http].include?(uri.scheme) && uri.host
        if uri.scheme == "http" && !allow_http && !LOCAL_HOSTS.include?(uri.host)
          raise ArgumentError, "base_url must use https (your API key would be sent in clear text)."
        end
        raise ArgumentError, "base_url must not contain credentials." if uri.userinfo

        url = base_url.sub(%r{/+\z}, "")
        return url.end_with?("/api/v1") ? url : "#{url}/api/v1"
      end
      ws = workspace.to_s.strip.downcase
      raise ArgumentError, "Pass workspace: (e.g. \"acme\") or base_url:, or set WIZARDAPP_WORKSPACE." if ws.empty?
      raise ArgumentError, "workspace must be your workspace subdomain, for example \"acme\" for acme.wizardapp.io." unless ws.match?(WORKSPACE_RE)

      "https://#{ws}.wizardapp.io/api/v1"
    rescue URI::InvalidURIError
      raise ArgumentError, "base_url must be a full URL, for example https://acme.wizardapp.io/api/v1"
    end

    def encode_params(params)
      return "" if params.nil? || params.empty?

      URI.encode_www_form(params.reject { |_, v| v.nil? }.map { |k, v| [k.to_s, v.to_s] })
    end

    def decode(raw)
      return nil if raw.nil? || raw.empty?

      JSON.parse(raw)
    rescue JSON::ParserError
      { "message" => raw[0, 500] }
    end

    def backoff(attempt, retry_after)
      return [retry_after, MAX_RETRY_WAIT].min if retry_after && retry_after >= 0

      [0.5 * (2**(attempt - 1)), 8].min
    end

    def remember_rate_limit(response)
      return unless response["X-RateLimit-Limit"]

      @last_rate_limit = {
        limit: response["X-RateLimit-Limit"].to_i,
        remaining: response["X-RateLimit-Remaining"].to_i,
        reset: response["X-RateLimit-Reset"].to_i
      }
    end
  end
end
