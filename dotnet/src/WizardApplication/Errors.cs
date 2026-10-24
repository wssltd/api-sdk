// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

using System;
using System.Collections.Generic;
using System.Text.Json.Nodes;

namespace WizardApplication
{
    /// <summary>Base class for every error the SDK throws.</summary>
    public class WizardApplicationException : Exception
    {
        /// <summary>HTTP status, or 0 when no response arrived.</summary>
        public int Status { get; }

        /// <summary>The API error code, for example "validation_error".</summary>
        public string? Code { get; }

        /// <summary>Field errors for a 422, e.g. title: ["Title is required"].</summary>
        public IReadOnlyDictionary<string, string[]> Errors { get; }

        /// <summary>Seconds to wait after a 429, when the API says.</summary>
        public int? RetryAfter { get; }

        /// <summary>The decoded response body, if any.</summary>
        public JsonNode? Body { get; }

        /// <summary>Creates the exception.</summary>
        public WizardApplicationException(string message, int status = 0, string? code = null,
            IReadOnlyDictionary<string, string[]>? errors = null, int? retryAfter = null, JsonNode? body = null,
            Exception? inner = null)
            : base(message, inner)
        {
            Status = status;
            Code = code;
            Errors = errors ?? new Dictionary<string, string[]>();
            RetryAfter = retryAfter;
            Body = body;
        }

        /// <inheritdoc />
        public override string ToString() => Status == 0 ? Message : $"[{Status} {Code}] {Message}";

        internal static WizardApplicationException ForStatus(int status, JsonNode? body, int? retryAfter)
        {
            string? code = null;
            var message = $"HTTP {status}";
            var errors = new Dictionary<string, string[]>();
            if (body is JsonObject obj)
            {
                if (obj["error"] is JsonValue c && c.TryGetValue<string>(out var codeText)) code = codeText;
                if (obj["message"] is JsonValue m && m.TryGetValue<string>(out var text) && text.Length > 0) message = text;
                if (obj["errors"] is JsonObject fields)
                {
                    foreach (var pair in fields)
                    {
                        var list = new List<string>();
                        if (pair.Value is JsonArray arr)
                        {
                            foreach (var item in arr)
                            {
                                if (item is JsonValue v && v.TryGetValue<string>(out var s)) list.Add(s);
                            }
                        }
                        errors[pair.Key] = list.ToArray();
                    }
                }
                if (retryAfter == null && obj["retry_after"] is JsonValue ra && ra.TryGetValue<int>(out var seconds)) retryAfter = seconds;
            }

            return status switch
            {
                400 => new BadRequestException(message, status, code, errors, retryAfter, body),
                401 => new AuthenticationException(message, status, code, errors, retryAfter, body),
                403 => new PermissionDeniedException(message, status, code, errors, retryAfter, body),
                404 => new NotFoundException(message, status, code, errors, retryAfter, body),
                422 => new ValidationException(message, status, code, errors, retryAfter, body),
                429 => new RateLimitException(message, status, code, errors, retryAfter, body),
                >= 500 => new ServerException(message, status, code, errors, retryAfter, body),
                _ => new WizardApplicationException(message, status, code, errors, retryAfter, body),
            };
        }
    }

    /// <summary>No response: network failure, DNS, timeout or a refused redirect.</summary>
    public class ApiConnectionException : WizardApplicationException
    {
        /// <summary>Creates the exception.</summary>
        public ApiConnectionException(string message, Exception? inner = null) : base(message, inner: inner) { }
    }

    /// <summary>400: the request was malformed.</summary>
    public class BadRequestException : WizardApplicationException
    {
        internal BadRequestException(string m, int s, string? c, IReadOnlyDictionary<string, string[]> e, int? r, JsonNode? b) : base(m, s, c, e, r, b) { }
    }

    /// <summary>401: the API key is missing, invalid or expired.</summary>
    public class AuthenticationException : WizardApplicationException
    {
        internal AuthenticationException(string m, int s, string? c, IReadOnlyDictionary<string, string[]> e, int? r, JsonNode? b) : base(m, s, c, e, r, b) { }
    }

    /// <summary>403: the key lacks a permission, or the plan has no API access (code "plan_upgrade_required").</summary>
    public class PermissionDeniedException : WizardApplicationException
    {
        internal PermissionDeniedException(string m, int s, string? c, IReadOnlyDictionary<string, string[]> e, int? r, JsonNode? b) : base(m, s, c, e, r, b) { }
    }

    /// <summary>404: the record or route does not exist.</summary>
    public class NotFoundException : WizardApplicationException
    {
        internal NotFoundException(string m, int s, string? c, IReadOnlyDictionary<string, string[]> e, int? r, JsonNode? b) : base(m, s, c, e, r, b) { }
    }

    /// <summary>422: validation failed; see Errors.</summary>
    public class ValidationException : WizardApplicationException
    {
        internal ValidationException(string m, int s, string? c, IReadOnlyDictionary<string, string[]> e, int? r, JsonNode? b) : base(m, s, c, e, r, b) { }
    }

    /// <summary>429: the per-minute or monthly limit was reached; see RetryAfter.</summary>
    public class RateLimitException : WizardApplicationException
    {
        internal RateLimitException(string m, int s, string? c, IReadOnlyDictionary<string, string[]> e, int? r, JsonNode? b) : base(m, s, c, e, r, b) { }
    }

    /// <summary>5xx: something went wrong on the server.</summary>
    public class ServerException : WizardApplicationException
    {
        internal ServerException(string m, int s, string? c, IReadOnlyDictionary<string, string[]> e, int? r, JsonNode? b) : base(m, s, c, e, r, b) { }
    }
}
