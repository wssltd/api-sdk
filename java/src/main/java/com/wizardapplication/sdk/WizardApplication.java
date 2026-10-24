// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Client for one workspace's Wizard Application API. Thread safe; reuse one instance.
 *
 * <pre>{@code
 * WizardApplication client = WizardApplication.builder().apiKey("wa_...").workspace("acme").build();
 * Page page = client.jobs().list(Map.of("status", "published"));
 * }</pre>
 */
public final class WizardApplication {
    /** SDK version. */
    public static final String VERSION = "1.0.0";

    private static final Pattern WORKSPACE = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");
    private static final Set<String> IDEMPOTENT = Set.of("GET", "PUT", "DELETE");
    private static final int MAX_RETRY_WAIT = 60;

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final HttpClient http;
    private volatile RateLimit lastRateLimit;

    private final Resource jobs;
    private final Resource candidates;
    private final Resource companies;
    private final Resource bookings;
    private final Leads leads;
    private final ContentTypes contentTypes;

    /** Rate limit headers from a response. Reset is seconds until the per-minute window resets. */
    public static final class RateLimit {
        public final int limit;
        public final int remaining;
        public final int reset;

        RateLimit(int limit, int remaining, int reset) {
            this.limit = limit;
            this.remaining = remaining;
            this.reset = reset;
        }
    }

    /** Builds a client. apiKey defaults to WIZARDAPP_API_KEY and workspace to WIZARDAPP_WORKSPACE. */
    public static final class Builder {
        private String apiKey;
        private String workspace;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 2;
        private boolean allowHttp;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }

        /** Your workspace subdomain, e.g. "acme" for acme.wizardapp.io. */
        public Builder workspace(String workspace) { this.workspace = workspace; return this; }

        /** Full API URL instead of a workspace. Must be https. */
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }

        /** Per-request timeout. Default 30 seconds. */
        public Builder timeout(Duration timeout) { this.timeout = timeout; return this; }

        /** Retries for a 429 and, on GET/PUT/DELETE, network errors and 502/503/504. Default 2. */
        public Builder maxRetries(int maxRetries) { this.maxRetries = Math.max(0, maxRetries); return this; }

        /** Allow a plain http base URL. Local testing only. */
        public Builder allowHttp(boolean allowHttp) { this.allowHttp = allowHttp; return this; }

        public WizardApplication build() { return new WizardApplication(this); }
    }

    public static Builder builder() {
        return new Builder();
    }

    private WizardApplication(Builder b) {
        String key = b.apiKey != null ? b.apiKey : System.getenv("WIZARDAPP_API_KEY");
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("An API key is required (apiKey or WIZARDAPP_API_KEY).");
        if (key.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException("The API key must not contain whitespace.");
        this.apiKey = key;
        String ws = b.workspace != null ? b.workspace : (b.baseUrl == null ? System.getenv("WIZARDAPP_WORKSPACE") : null);
        this.baseUrl = resolveBaseUrl(ws, b.baseUrl, b.allowHttp);
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
        // Redirects are never followed, so the API key stays with this host.
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(b.timeout.compareTo(Duration.ofSeconds(10)) < 0 ? b.timeout : Duration.ofSeconds(10))
                .build();
        this.jobs = new Resource(this, "jobs");
        this.candidates = new Resource(this, "candidates");
        this.companies = new Resource(this, "companies");
        this.bookings = new Resource(this, "bookings");
        this.leads = new Leads(this, "leads");
        this.contentTypes = new ContentTypes(this);
    }

    public Resource jobs() { return jobs; }
    public Resource candidates() { return candidates; }
    public Resource companies() { return companies; }
    public Resource bookings() { return bookings; }
    public Leads leads() { return leads; }
    public ContentTypes contentTypes() { return contentTypes; }

    /** Items of one content type, by its slug or ID (e.g. "blog-posts"). */
    public Resource content(String contentType) {
        return new Resource(this, "content/" + Resource.segment(contentType));
    }

    /** The API URL requests go to. */
    public String getBaseUrl() { return baseUrl; }

    /** Rate limit headers from the latest response, or null. */
    public RateLimit getLastRateLimit() { return lastRateLimit; }

    /** Never includes the API key. */
    @Override
    public String toString() {
        return "WizardApplication(" + baseUrl + ")";
    }

    private static String resolveBaseUrl(String workspace, String baseUrl, boolean allowHttp) {
        if (baseUrl != null && !baseUrl.isEmpty()) {
            URI uri;
            try {
                uri = new URI(baseUrl);
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException("baseUrl must be a full URL, for example https://acme.wizardapp.io/api/v1");
            }
            String scheme = uri.getScheme();
            if (uri.getHost() == null || !("https".equals(scheme) || "http".equals(scheme))) {
                throw new IllegalArgumentException("baseUrl must be a full URL, for example https://acme.wizardapp.io/api/v1");
            }
            if ("http".equals(scheme) && !allowHttp && !LOCAL_HOSTS.contains(uri.getHost())) {
                throw new IllegalArgumentException("baseUrl must use https (your API key would be sent in clear text).");
            }
            if (uri.getUserInfo() != null) throw new IllegalArgumentException("baseUrl must not contain credentials.");
            String url = baseUrl.replaceAll("/+$", "");
            return url.endsWith("/api/v1") ? url : url + "/api/v1";
        }
        String ws = workspace == null ? "" : workspace.trim().toLowerCase(java.util.Locale.ROOT);
        if (ws.isEmpty()) throw new IllegalArgumentException("Set workspace (e.g. \"acme\") or baseUrl, or WIZARDAPP_WORKSPACE.");
        if (!WORKSPACE.matcher(ws).matches()) {
            throw new IllegalArgumentException("workspace must be your workspace subdomain, for example \"acme\" for acme.wizardapp.io.");
        }
        return "https://" + ws + ".wizardapp.io/api/v1";
    }

    /**
     * Sends a request and returns the decoded JSON body (null when empty).
     * Retries a 429 after Retry-After, and network errors and 502/503/504 on
     * GET, PUT and DELETE, up to maxRetries times.
     */
    public Object request(String method, String path, Map<String, ?> query, Object body) {
        method = method.toUpperCase(java.util.Locale.ROOT);
        URI uri = URI.create(baseUrl + path + encodeQuery(query));
        String payload = body == null ? null : Json.write(body);
        boolean idempotent = IDEMPOTENT.contains(method);

        for (int attempt = 0; ; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", "wizardapplication-java/" + VERSION);
            if (payload != null) {
                rb.header("Content-Type", "application/json");
                rb.method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            } else {
                rb.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> res;
            try {
                res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                if (attempt < maxRetries && idempotent) {
                    sleep(backoff(attempt + 1, null));
                    continue;
                }
                throw new ConnectionException("Could not reach " + baseUrl + ": " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ConnectionException("Interrupted while calling " + baseUrl, e);
            }

            rememberRateLimit(res);
            Object decoded = decode(res.body());
            int status = res.statusCode();
            if (status >= 200 && status < 300) return decoded;
            if (status >= 300 && status < 400) {
                throw new ConnectionException("Redirect refused: the API key is never sent to another URL.", null);
            }

            Integer retryAfter = res.headers().firstValue("Retry-After").map(WizardApplication::toInt).orElse(null);
            WizardApplicationException error = WizardApplicationException.forStatus(status, decoded, retryAfter);
            boolean retry = status == 429
                    ? !"monthly_limit_exceeded".equals(error.getCode())
                    : (status == 502 || status == 503 || status == 504) && idempotent;
            if (retry && attempt < maxRetries) {
                sleep(backoff(attempt + 1, error.getRetryAfter()));
                continue;
            }
            throw error;
        }
    }

    private void rememberRateLimit(HttpResponse<?> res) {
        if (res.headers().firstValue("X-RateLimit-Limit").isEmpty()) return;
        lastRateLimit = new RateLimit(
                header(res, "X-RateLimit-Limit"), header(res, "X-RateLimit-Remaining"), header(res, "X-RateLimit-Reset"));
    }

    private static int header(HttpResponse<?> res, String name) {
        Integer n = res.headers().firstValue(name).map(WizardApplication::toInt).orElse(null);
        return n == null ? 0 : n;
    }

    private static Integer toInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Object decode(String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            return Json.parse(text);
        } catch (IllegalArgumentException e) {
            return Map.of("message", text.length() > 500 ? text.substring(0, 500) : text);
        }
    }

    private static double backoff(int attempt, Integer retryAfter) {
        if (retryAfter != null && retryAfter >= 0) return Math.min(retryAfter, MAX_RETRY_WAIT);
        return Math.min(0.5 * Math.pow(2, attempt - 1), 8);
    }

    private static void sleep(double seconds) {
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConnectionException("Interrupted while waiting to retry", e);
        }
    }

    private static String encodeQuery(Map<String, ?> query) {
        if (query == null || query.isEmpty()) return "";
        StringJoiner j = new StringJoiner("&", "?", "");
        j.setEmptyValue("");
        for (Map.Entry<String, ?> e : query.entrySet()) {
            if (e.getValue() == null) continue;
            j.add(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8));
        }
        return j.toString();
    }
}
