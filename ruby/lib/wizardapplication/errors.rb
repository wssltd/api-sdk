# frozen_string_literal: true

# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

module WizardApplication
  # Base class for every error the SDK raises.
  class Error < StandardError
    # HTTP status, or 0 when no response arrived.
    attr_reader :status
    # The API error code, for example "validation_error".
    attr_reader :code
    # Field errors for a 422, e.g. { "title" => ["Title is required"] }.
    attr_reader :errors
    # Seconds to wait after a 429, when the API says.
    attr_reader :retry_after
    # The decoded response body.
    attr_reader :body

    def initialize(message, status: 0, code: nil, errors: nil, retry_after: nil, body: nil)
      super(message)
      @status = status
      @code = code
      @errors = errors || {}
      @retry_after = retry_after
      @body = body
    end

    def to_s
      status.zero? ? super : "[#{status} #{code}] #{super}"
    end

    # Builds the right error for an HTTP status and decoded body.
    def self.for_status(status, body, retry_after)
      code = nil
      message = "HTTP #{status}"
      errors = {}
      if body.is_a?(Hash)
        code = body["error"] if body["error"].is_a?(String)
        message = body["message"] if body["message"].is_a?(String) && !body["message"].empty?
        if body["errors"].is_a?(Hash)
          body["errors"].each { |field, list| errors[field.to_s] = Array(list).grep(String) }
        end
        retry_after = body["retry_after"] if retry_after.nil? && body["retry_after"].is_a?(Integer)
      end
      klass = STATUS_ERRORS.fetch(status) { status >= 500 ? ServerError : Error }
      klass.new(message, status: status, code: code, errors: errors, retry_after: retry_after, body: body)
    end
  end

  # No response: network failure, DNS, timeout or a refused redirect.
  class ConnectionError < Error; end
  # 400: the request was malformed.
  class BadRequestError < Error; end
  # 401: the API key is missing, invalid or expired.
  class AuthenticationError < Error; end
  # 403: the key lacks a permission, or the plan has no API access (code "plan_upgrade_required").
  class PermissionDeniedError < Error; end
  # 404: the record or route does not exist.
  class NotFoundError < Error; end
  # 422: validation failed; see #errors.
  class ValidationError < Error; end
  # 429: the per-minute or monthly limit was reached; see #retry_after.
  class RateLimitError < Error; end
  # 5xx: something went wrong on the server.
  class ServerError < Error; end

  STATUS_ERRORS = {
    400 => BadRequestError,
    401 => AuthenticationError,
    403 => PermissionDeniedError,
    404 => NotFoundError,
    422 => ValidationError,
    429 => RateLimitError
  }.freeze
end
