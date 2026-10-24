// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

// Package wizardapplication is the official Go SDK for the Wizard Application API.
//
//	client, err := wizardapplication.New("wa_...", wizardapplication.WithWorkspace("acme"))
//	page, err := client.Jobs.List(ctx, wizardapplication.Params{"status": "published"})
package wizardapplication

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Version is the SDK version.
const Version = "1.0.0"

const maxRetryWait = 60 * time.Second

var workspaceRE = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$`)

// Record is one API record as decoded JSON.
type Record = map[string]interface{}

// Params are query parameters: filters, page, per_page, sort and order.
type Params map[string]interface{}

// RateLimit holds the rate limit headers from the latest response.
type RateLimit struct {
	Limit     int
	Remaining int
	// Reset is the number of seconds until the per-minute window resets.
	Reset int
}

// Client talks to one workspace's API. It is safe for concurrent use.
type Client struct {
	apiKey     string
	baseURL    string
	httpClient *http.Client
	maxRetries int
	allowHTTP  bool
	workspace  string

	mu            sync.Mutex
	lastRateLimit *RateLimit

	Jobs         *Resource
	Candidates   *Resource
	Companies    *Resource
	Bookings     *Resource
	Leads        *Leads
	ContentTypes *ContentTypes
}

// Option configures a Client.
type Option func(*Client) error

// WithWorkspace sets your workspace subdomain, e.g. "acme" for acme.wizardapp.io.
func WithWorkspace(workspace string) Option {
	return func(c *Client) error { c.workspace = workspace; return nil }
}

// WithBaseURL sets the full API URL instead of a workspace. It must be https.
func WithBaseURL(baseURL string) Option {
	return func(c *Client) error { c.baseURL = baseURL; return nil }
}

// WithTimeout sets the per-request timeout (default 30s).
func WithTimeout(d time.Duration) Option {
	return func(c *Client) error { c.httpClient.Timeout = d; return nil }
}

// WithMaxRetries sets how often a 429, or a network error or 502/503/504 on
// GET, PUT and DELETE, is retried (default 2).
func WithMaxRetries(n int) Option {
	return func(c *Client) error {
		if n < 0 {
			n = 0
		}
		c.maxRetries = n
		return nil
	}
}

// WithHTTPClient uses your own *http.Client. Redirects are still refused.
func WithHTTPClient(h *http.Client) Option {
	return func(c *Client) error {
		copied := *h
		copied.CheckRedirect = refuseRedirect
		c.httpClient = &copied
		return nil
	}
}

// WithAllowHTTP allows a plain http base URL. Local testing only.
func WithAllowHTTP() Option {
	return func(c *Client) error { c.allowHTTP = true; return nil }
}

// New creates a client. An empty apiKey falls back to WIZARDAPP_API_KEY, and
// a missing workspace to WIZARDAPP_WORKSPACE.
func New(apiKey string, opts ...Option) (*Client, error) {
	if apiKey == "" {
		apiKey = os.Getenv("WIZARDAPP_API_KEY")
	}
	if apiKey == "" {
		return nil, errors.New("wizardapplication: an API key is required (pass it or set WIZARDAPP_API_KEY)")
	}
	if strings.ContainsAny(apiKey, " \t\r\n") {
		return nil, errors.New("wizardapplication: the API key must not contain whitespace")
	}
	c := &Client{
		apiKey:     apiKey,
		maxRetries: 2,
		httpClient: &http.Client{Timeout: 30 * time.Second, CheckRedirect: refuseRedirect},
	}
	for _, opt := range opts {
		if err := opt(c); err != nil {
			return nil, err
		}
	}
	if c.workspace == "" && c.baseURL == "" {
		c.workspace = os.Getenv("WIZARDAPP_WORKSPACE")
	}
	base, err := resolveBaseURL(c.workspace, c.baseURL, c.allowHTTP)
	if err != nil {
		return nil, err
	}
	c.baseURL = base
	c.Jobs = &Resource{client: c, path: "/jobs"}
	c.Candidates = &Resource{client: c, path: "/candidates"}
	c.Companies = &Resource{client: c, path: "/companies"}
	c.Bookings = &Resource{client: c, path: "/bookings"}
	c.Leads = &Leads{Resource{client: c, path: "/leads"}}
	c.ContentTypes = &ContentTypes{client: c}
	return c, nil
}

// BaseURL returns the API URL requests go to.
func (c *Client) BaseURL() string { return c.baseURL }

// String never includes the API key.
func (c *Client) String() string { return "wizardapplication.Client(" + c.baseURL + ")" }

// LastRateLimit returns the rate limit headers from the latest response, or nil.
func (c *Client) LastRateLimit() *RateLimit {
	c.mu.Lock()
	defer c.mu.Unlock()
	if c.lastRateLimit == nil {
		return nil
	}
	rl := *c.lastRateLimit
	return &rl
}

// Content returns the items of one content type, by its slug or ID.
func (c *Client) Content(contentType string) *Resource {
	return &Resource{client: c, path: "/content/" + url.PathEscape(contentType), emptyType: contentType == ""}
}

func refuseRedirect(req *http.Request, via []*http.Request) error {
	return errors.New("redirect refused: the API key is never sent to another URL")
}

func resolveBaseURL(workspace, baseURL string, allowHTTP bool) (string, error) {
	if baseURL != "" {
		u, err := url.Parse(baseURL)
		if err != nil || u.Host == "" || (u.Scheme != "https" && u.Scheme != "http") {
			return "", errors.New("wizardapplication: base URL must be a full URL, for example https://acme.wizardapp.io/api/v1")
		}
		host := u.Hostname()
		local := host == "localhost" || host == "127.0.0.1" || host == "::1"
		if u.Scheme == "http" && !allowHTTP && !local {
			return "", errors.New("wizardapplication: base URL must use https (your API key would be sent in clear text)")
		}
		if u.User != nil {
			return "", errors.New("wizardapplication: base URL must not contain credentials")
		}
		base := strings.TrimRight(baseURL, "/")
		if !strings.HasSuffix(base, "/api/v1") {
			base += "/api/v1"
		}
		return base, nil
	}
	ws := strings.ToLower(strings.TrimSpace(workspace))
	if ws == "" {
		return "", errors.New("wizardapplication: pass WithWorkspace (e.g. \"acme\") or WithBaseURL, or set WIZARDAPP_WORKSPACE")
	}
	if !workspaceRE.MatchString(ws) {
		return "", errors.New("wizardapplication: workspace must be your workspace subdomain, for example \"acme\" for acme.wizardapp.io")
	}
	return "https://" + ws + ".wizardapp.io/api/v1", nil
}

// Do sends a request and decodes the JSON response into out (which may be
// nil). Use it for anything the resource helpers don't cover.
func (c *Client) Do(ctx context.Context, method, path string, params Params, body interface{}, out interface{}) error {
	method = strings.ToUpper(method)
	target := c.baseURL + path
	if q := encodeParams(params); q != "" {
		target += "?" + q
	}
	var payload []byte
	if body != nil {
		var err error
		if payload, err = json.Marshal(body); err != nil {
			return fmt.Errorf("wizardapplication: encoding request body: %w", err)
		}
	}
	idempotent := method == http.MethodGet || method == http.MethodPut || method == http.MethodDelete

	for attempt := 0; ; attempt++ {
		var reader io.Reader
		if payload != nil {
			reader = bytes.NewReader(payload)
		}
		req, err := http.NewRequestWithContext(ctx, method, target, reader)
		if err != nil {
			return err
		}
		req.Header.Set("Authorization", "Bearer "+c.apiKey)
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", "wizardapplication-go/"+Version)
		if payload != nil {
			req.Header.Set("Content-Type", "application/json")
		}

		resp, err := c.httpClient.Do(req)
		if err != nil {
			if ctx.Err() == nil && attempt < c.maxRetries && idempotent && !isRedirectErr(err) {
				if werr := sleepCtx(ctx, backoff(attempt+1, -1)); werr != nil {
					return werr
				}
				continue
			}
			return &Error{Message: fmt.Sprintf("could not reach %s: %v", c.baseURL, err), RetryAfter: -1}
		}
		raw, readErr := io.ReadAll(io.LimitReader(resp.Body, 32<<20))
		resp.Body.Close()
		c.rememberRateLimit(resp.Header)
		if readErr != nil {
			return &Error{Message: "reading response: " + readErr.Error(), RetryAfter: -1}
		}

		var decoded interface{}
		if len(raw) > 0 {
			if json.Unmarshal(raw, &decoded) != nil {
				decoded = map[string]interface{}{"message": truncate(string(raw), 500)}
			}
		}
		if resp.StatusCode >= 200 && resp.StatusCode < 300 {
			if out != nil && len(raw) > 0 {
				return json.Unmarshal(raw, out)
			}
			return nil
		}

		apiErr := errorForStatus(resp.StatusCode, decoded, intHeader(resp.Header, "Retry-After", -1))
		retry := false
		if resp.StatusCode == 429 {
			retry = apiErr.Code != "monthly_limit_exceeded"
		} else if resp.StatusCode == 502 || resp.StatusCode == 503 || resp.StatusCode == 504 {
			retry = idempotent
		}
		if retry && attempt < c.maxRetries {
			if werr := sleepCtx(ctx, backoff(attempt+1, apiErr.RetryAfter)); werr != nil {
				return werr
			}
			continue
		}
		return apiErr
	}
}

func (c *Client) rememberRateLimit(h http.Header) {
	if h.Get("X-RateLimit-Limit") == "" {
		return
	}
	rl := &RateLimit{
		Limit:     intHeader(h, "X-RateLimit-Limit", 0),
		Remaining: intHeader(h, "X-RateLimit-Remaining", 0),
		Reset:     intHeader(h, "X-RateLimit-Reset", 0),
	}
	c.mu.Lock()
	c.lastRateLimit = rl
	c.mu.Unlock()
}

func isRedirectErr(err error) bool {
	return strings.Contains(err.Error(), "redirect refused")
}

func backoff(attempt, retryAfter int) time.Duration {
	if retryAfter >= 0 {
		d := time.Duration(retryAfter) * time.Second
		if d > maxRetryWait {
			d = maxRetryWait
		}
		return d
	}
	return time.Duration(math.Min(0.5*math.Pow(2, float64(attempt-1)), 8) * float64(time.Second))
}

func sleepCtx(ctx context.Context, d time.Duration) error {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-t.C:
		return nil
	}
}

func encodeParams(p Params) string {
	if len(p) == 0 {
		return ""
	}
	q := url.Values{}
	for k, v := range p {
		if v == nil {
			continue
		}
		q.Set(k, fmt.Sprint(v))
	}
	return q.Encode()
}

func intHeader(h http.Header, name string, fallback int) int {
	v := h.Get(name)
	if v == "" {
		return fallback
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return fallback
	}
	return n
}

func truncate(s string, n int) string {
	if len(s) > n {
		return s[:n]
	}
	return s
}
