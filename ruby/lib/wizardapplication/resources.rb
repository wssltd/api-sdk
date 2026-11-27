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

  # Team members: list, iterate, get, create and update. The API has no
  # delete. Creating a user adds a paid seat, so the key must have been
  # created with the per-seat charge accepted.
  class Users < Resource
    # Users can't be deleted through the API.
    def delete(_id)
      raise NotImplementedError, "Users can't be deleted through the API."
    end

    # Creates up to 100 users in one call. Returns "data" (one entry per row,
    # status created, skipped or failed) and "summary" (the counts). A row's
    # manager_email may name a user created earlier in the same call.
    def import_users(users)
      result = @client.request(:post, "#{@path}/import", body: { users: Array(users) })
      result.is_a?(Hash) ? result : {}
    end

    # Sends, or sends again, the invitation email to a user who hasn't signed in yet.
    def send_invitation(id)
      unwrap(@client.request(:post, "#{@path}/#{Resource.segment(id)}/invitation"))
    end
  end

  # The workspace's AI employees, through its MCP server. The key needs the
  # ai_employees.write permission.
  class AIEmployees
    # A job can take a few minutes; the server stops it at three.
    ASK_TIMEOUT = 200

    def initialize(client)
      @client = client
    end

    # Every AI employee that is switched on: id, name, purpose and paused.
    def list
      result = @client.call_tool("list_ai_employees", {})
      items = result.is_a?(Hash) && result["ai_employees"].is_a?(Array) ? result["ai_employees"] : []
      items.select { |item| item.is_a?(Hash) }
    end

    # Gives an AI employee (by id or name) a job in plain words and waits for
    # it, which can take a few minutes. Returns summary, changes_made and
    # awaiting_approval. Changes wait for one of the people it reports to
    # unless the workspace trusts it with that kind of change. The same job
    # asked twice on the same day doesn't queue its changes twice.
    def ask(ai_employee, instruction, context: nil)
      if ai_employee.to_s.empty? || instruction.to_s.empty?
        raise ArgumentError, "ai_employee and instruction are required."
      end

      args = { ai_employee: ai_employee, instruction: instruction }
      args[:context] = context unless context.to_s.empty?
      result = @client.call_tool("ask_ai_employee", args, timeout: ASK_TIMEOUT)
      result.is_a?(Hash) ? result : {}
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
