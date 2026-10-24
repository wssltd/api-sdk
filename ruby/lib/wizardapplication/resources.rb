# frozen_string_literal: true

# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

require "erb"

module WizardApplication
  # One page of a list response.
  class Page
    include Enumerable

    attr_reader :data, :page, :per_page, :total, :total_pages

    def initialize(payload)
      payload = {} unless payload.is_a?(Hash)
      @data = payload["data"].is_a?(Array) ? payload["data"] : []
      p = payload["pagination"].is_a?(Hash) ? payload["pagination"] : {}
      @page = (p["page"] || 1).to_i
      @per_page = (p["per_page"] || @data.size).to_i
      @total = (p["total"] || @data.size).to_i
      @total_pages = (p["total_pages"] || 1).to_i
    end

    def next?
      page < total_pages
    end

    def each(&block)
      data.each(&block)
    end

    def size
      data.size
    end
  end

  # Standard list, get, create, update and delete for one resource.
  class Resource
    def initialize(client, path)
      @client = client
      @path = "/#{path}"
    end

    # Escapes one path segment (an ID or slug).
    def self.segment(id)
      text = id.to_s
      raise ArgumentError, "An ID is required." if text.empty?

      ERB::Util.url_encode(text)
    end

    # One page of records. Pass filters (status:, search:) with page:,
    # per_page: (up to 100), sort: and order:.
    def list(**params)
      Page.new(@client.request(:get, @path, params: params))
    end

    # Every record across all pages, fetched a page at a time. Without a
    # block it returns an Enumerator.
    def iterate(per_page: 100, **filters)
      return enum_for(:iterate, per_page: per_page, **filters) unless block_given?

      n = 1
      loop do
        result = list(**filters, page: n, per_page: per_page)
        result.data.each { |item| yield item }
        break if !result.next? || result.data.empty?

        n += 1
      end
    end

    def get(id)
      unwrap(@client.request(:get, "#{@path}/#{Resource.segment(id)}"))
    end

    def create(data)
      unwrap(@client.request(:post, @path, body: data || {}))
    end

    def update(id, data)
      unwrap(@client.request(:put, "#{@path}/#{Resource.segment(id)}", body: data || {}))
    end

    def delete(id)
      @client.request(:delete, "#{@path}/#{Resource.segment(id)}")
      nil
    end

    private

    def unwrap(payload)
      return payload["data"] if payload.is_a?(Hash) && payload.key?("data")

      payload.is_a?(Hash) ? payload : {}
    end
  end

  # Leads, with conversion to a customer.
  class Leads < Resource
    # Converts a lead into a customer and returns the updated record.
    def convert(id)
      unwrap(@client.request(:post, "#{@path}/#{Resource.segment(id)}/convert"))
    end
  end

  # Content types (read only).
  class ContentTypes
    def initialize(client)
      @client = client
    end

    def list(**params)
      Page.new(@client.request(:get, "/content-types", params: params))
    end

    # One content type, with its fields.
    def get(id)
      payload = @client.request(:get, "/content-types/#{Resource.segment(id)}")
      payload.is_a?(Hash) && payload["data"].is_a?(Hash) ? payload["data"] : {}
    end
  end
end
