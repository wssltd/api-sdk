// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

using System;
using System.Collections.Generic;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;

namespace WizardApplication
{
    /// <summary>Settings for <see cref="WizardApplicationClient"/>.</summary>
    public sealed class WizardApplicationOptions
    {
        /// <summary>API key. Defaults to the WIZARDAPP_API_KEY environment variable.</summary>
        public string? ApiKey { get; set; }

        /// <summary>Your workspace subdomain, e.g. "acme". Defaults to WIZARDAPP_WORKSPACE.</summary>
        public string? Workspace { get; set; }

        /// <summary>Full API URL instead of Workspace. Must be https.</summary>
        public string? BaseUrl { get; set; }

        /// <summary>Per-request timeout. Default 30 seconds.</summary>
        public TimeSpan Timeout { get; set; } = TimeSpan.FromSeconds(30);

        /// <summary>Retries for a 429 and, on GET/PUT/DELETE, network errors and 502/503/504. Default 2.</summary>
        public int MaxRetries { get; set; } = 2;

        /// <summary>Allow a plain http BaseUrl. Local testing only.</summary>
        public bool AllowHttp { get; set; }
    }

    /// <summary>Rate limit headers from the latest response.</summary>
    public sealed record RateLimitInfo(int Limit, int Remaining, int Reset);

    /// <summary>Client for one workspace's Wizard Application API. Thread safe; reuse one instance.</summary>
    public sealed class WizardApplicationClient : IDisposable
    {
        /// <summary>SDK version.</summary>
        public const string Version = "1.0.0";

        private static readonly Regex WorkspaceRe = new("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$", RegexOptions.Compiled);
        private static readonly TimeSpan MaxRetryWait = TimeSpan.FromSeconds(60);

        private readonly string _apiKey;
        private readonly HttpClient _http;
        private readonly int _maxRetries;
        private RateLimitInfo? _lastRateLimit;

        /// <summary>The API URL requests go to.</summary>
        public string BaseUrl { get; }

        /// <summary>Rate limit headers from the latest response, or null.</summary>
        public RateLimitInfo? LastRateLimit => Volatile.Read(ref _lastRateLimit);

        /// <summary>Jobs.</summary>
        public Resource Jobs { get; }
        /// <summary>Candidates.</summary>
        public Resource Candidates { get; }
        /// <summary>Companies.</summary>
        public Resource Companies { get; }
        /// <summary>Bookings.</summary>
        public Resource Bookings { get; }
        /// <summary>Leads, with Convert.</summary>
        public LeadsResource Leads { get; }
        /// <summary>Content types (read only).</summary>
        public ContentTypesResource ContentTypes { get; }

        /// <summary>Creates a client for a workspace subdomain.</summary>
        public WizardApplicationClient(string apiKey, string workspace)
            : this(new WizardApplicationOptions { ApiKey = apiKey, Workspace = workspace }) { }

        /// <summary>Creates a client from options.</summary>
        public WizardApplicationClient(WizardApplicationOptions options)
        {
            if (options == null) throw new ArgumentNullException(nameof(options));
            var key = options.ApiKey ?? Environment.GetEnvironmentVariable("WIZARDAPP_API_KEY");
            if (string.IsNullOrEmpty(key)) throw new ArgumentException("An API key is required (set ApiKey or WIZARDAPP_API_KEY).");
            if (key.Any(char.IsWhiteSpace)) throw new ArgumentException("The API key must not contain whitespace.");
            _apiKey = key;
            BaseUrl = ResolveBaseUrl(options.Workspace ?? (options.BaseUrl == null ? Environment.GetEnvironmentVariable("WIZARDAPP_WORKSPACE") : null), options.BaseUrl, options.AllowHttp);
            _maxRetries = Math.Max(0, options.MaxRetries);

            // Redirects are refused so the API key is never sent to another URL.
            _http = new HttpClient(new HttpClientHandler { AllowAutoRedirect = false }) { Timeout = options.Timeout };
            _http.DefaultRequestHeaders.UserAgent.ParseAdd($"wizardapplication-dotnet/{Version}");
            _http.DefaultRequestHeaders.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

            Jobs = new Resource(this, "jobs");
            Candidates = new Resource(this, "candidates");
            Companies = new Resource(this, "companies");
            Bookings = new Resource(this, "bookings");
            Leads = new LeadsResource(this, "leads");
            ContentTypes = new ContentTypesResource(this);
        }

        /// <summary>Items of one content type, by its slug or ID (e.g. "blog-posts").</summary>
        public Resource Content(string contentType) => new(this, "content/" + Resource.Segment(contentType));

        /// <inheritdoc />
        public override string ToString() => $"WizardApplicationClient({BaseUrl})";

        /// <inheritdoc />
        public void Dispose() => _http.Dispose();

        private static string ResolveBaseUrl(string? workspace, string? baseUrl, bool allowHttp)
        {
            if (!string.IsNullOrEmpty(baseUrl))
            {
                if (!Uri.TryCreate(baseUrl, UriKind.Absolute, out var uri) || (uri.Scheme != "https" && uri.Scheme != "http"))
                    throw new ArgumentException("BaseUrl must be a full URL, for example https://acme.wizardapp.io/api/v1");
                if (uri.Scheme == "http" && !allowHttp && !uri.IsLoopback)
                    throw new ArgumentException("BaseUrl must use https (your API key would be sent in clear text).");
                if (!string.IsNullOrEmpty(uri.UserInfo))
                    throw new ArgumentException("BaseUrl must not contain credentials.");
                var url = baseUrl!.TrimEnd('/');
                return url.EndsWith("/api/v1", StringComparison.Ordinal) ? url : url + "/api/v1";
            }
            var ws = (workspace ?? "").Trim().ToLowerInvariant();
            if (ws.Length == 0)
                throw new ArgumentException("Set Workspace (e.g. \"acme\") or BaseUrl, or the WIZARDAPP_WORKSPACE variable.");
            if (!WorkspaceRe.IsMatch(ws))
                throw new ArgumentException("Workspace must be your workspace subdomain, for example \"acme\" for acme.wizardapp.io.");
            return $"https://{ws}.wizardapp.io/api/v1";
        }

        /// <summary>Sends a request and returns the decoded JSON body (null when empty).</summary>
        public async Task<JsonNode?> RequestAsync(HttpMethod method, string path, IDictionary<string, object?>? query = null,
            object? body = null, CancellationToken cancellationToken = default)
        {
            var url = BaseUrl + path + EncodeQuery(query);
            var payload = body == null ? null : JsonSerializer.Serialize(body);
            var idempotent = method == HttpMethod.Get || method == HttpMethod.Put || method == HttpMethod.Delete;

            for (var attempt = 0; ; attempt++)
            {
                using var request = new HttpRequestMessage(method, url);
                request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _apiKey);
                if (payload != null) request.Content = new StringContent(payload, Encoding.UTF8, "application/json");

                HttpResponseMessage response;
                try
                {
                    response = await _http.SendAsync(request, cancellationToken).ConfigureAwait(false);
                }
                catch (Exception e) when (e is HttpRequestException || (e is TaskCanceledException && !cancellationToken.IsCancellationRequested))
                {
                    if (attempt < _maxRetries && idempotent)
                    {
                        await Task.Delay(Backoff(attempt + 1, null), cancellationToken).ConfigureAwait(false);
                        continue;
                    }
                    throw new ApiConnectionException($"Could not reach {BaseUrl}: {e.Message}", e);
                }

                using (response)
                {
                    RememberRateLimit(response);
                    var text = await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);
                    JsonNode? node = null;
                    if (text.Length > 0)
                    {
                        try { node = JsonNode.Parse(text); }
                        catch (JsonException) { node = new JsonObject { ["message"] = text.Length > 500 ? text.Substring(0, 500) : text }; }
                    }

                    var status = (int)response.StatusCode;
                    if (status >= 200 && status < 300) return node;
                    if (status >= 300 && status < 400)
                        throw new ApiConnectionException("Redirect refused: the API key is never sent to another URL.");

                    int? retryAfter = response.Headers.RetryAfter?.Delta is TimeSpan d ? (int)d.TotalSeconds : null;
                    var error = WizardApplicationException.ForStatus(status, node, retryAfter);
                    var retry = status == 429
                        ? error.Code != "monthly_limit_exceeded"
                        : (status == 502 || status == 503 || status == 504) && idempotent;
                    if (retry && attempt < _maxRetries)
                    {
                        await Task.Delay(Backoff(attempt + 1, error.RetryAfter), cancellationToken).ConfigureAwait(false);
                        continue;
                    }
                    throw error;
                }
            }
        }

        private static TimeSpan Backoff(int attempt, int? retryAfter)
        {
            if (retryAfter is int s && s >= 0)
            {
                var wait = TimeSpan.FromSeconds(s);
                return wait > MaxRetryWait ? MaxRetryWait : wait;
            }
            return TimeSpan.FromSeconds(Math.Min(0.5 * Math.Pow(2, attempt - 1), 8));
        }

        private void RememberRateLimit(HttpResponseMessage response)
        {
            static int Header(HttpResponseMessage r, string name) =>
                r.Headers.TryGetValues(name, out var values) && int.TryParse(values.FirstOrDefault(), out var n) ? n : 0;
            if (!response.Headers.Contains("X-RateLimit-Limit")) return;
            Volatile.Write(ref _lastRateLimit, new RateLimitInfo(
                Header(response, "X-RateLimit-Limit"), Header(response, "X-RateLimit-Remaining"), Header(response, "X-RateLimit-Reset")));
        }

        private static string EncodeQuery(IDictionary<string, object?>? query)
        {
            if (query == null) return "";
            var parts = new List<string>();
            foreach (var pair in query)
            {
                if (pair.Value == null) continue;
                var value = pair.Value switch
                {
                    bool b => b ? "true" : "false",
                    IFormattable f => f.ToString(null, System.Globalization.CultureInfo.InvariantCulture),
                    _ => pair.Value.ToString() ?? "",
                };
                parts.Add(Uri.EscapeDataString(pair.Key) + "=" + Uri.EscapeDataString(value));
            }
            return parts.Count == 0 ? "" : "?" + string.Join("&", parts);
        }
    }
}
