// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

'use strict';

// Official JavaScript SDK for the Wizard Application API (Node.js 18+).
// Server side only: never ship an API key to a browser.

const VERSION = '1.0.0';
const WORKSPACE_RE = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]', '::1']);
const RETRY_STATUSES = new Set([502, 503, 504]);
const IDEMPOTENT = new Set(['GET', 'PUT', 'DELETE']);
const MAX_RETRY_WAIT = 60;

class WizardApplicationError extends Error {
  constructor(message, { status = 0, code = null, errors = {}, retryAfter = null, body = null } = {}) {
    super(message);
    this.name = this.constructor.name;
    this.status = status;
    this.code = code;
    this.errors = errors || {};
    this.retryAfter = retryAfter;
    this.body = body;
  }
}
class ConnectionError extends WizardApplicationError {}
class BadRequestError extends WizardApplicationError {}
class AuthenticationError extends WizardApplicationError {}
class PermissionDeniedError extends WizardApplicationError {}
class NotFoundError extends WizardApplicationError {}
class ValidationError extends WizardApplicationError {}
class RateLimitError extends WizardApplicationError {}
class ServerError extends WizardApplicationError {}

const BY_STATUS = {
  400: BadRequestError,
  401: AuthenticationError,
  403: PermissionDeniedError,
  404: NotFoundError,
  422: ValidationError,
  429: RateLimitError,
};

function errorForStatus(status, body, retryAfter) {
  let code = null;
  let message = `HTTP ${status}`;
  let errors = {};
  if (body && typeof body === 'object') {
    code = body.error || null;
    message = body.message || message;
    if (body.errors && typeof body.errors === 'object') errors = body.errors;
    if (retryAfter == null && Number.isInteger(body.retry_after)) retryAfter = body.retry_after;
  }
  const Cls = BY_STATUS[status] || (status >= 500 ? ServerError : WizardApplicationError);
  return new Cls(message, { status, code, errors, retryAfter, body });
}

function quoteSegment(value) {
  const text = value == null ? '' : String(value);
  if (!text) throw new TypeError('An ID is required.');
  return encodeURIComponent(text);
}

function resolveBaseUrl({ workspace, baseUrl, allowHttp }) {
  if (baseUrl) {
    let u;
    try {
      u = new URL(baseUrl);
    } catch {
      throw new TypeError('baseUrl must be a full URL, for example https://acme.wizardapp.io/api/v1');
    }
    if (u.protocol !== 'https:' && u.protocol !== 'http:') throw new TypeError('baseUrl must use https.');
    if (u.protocol === 'http:' && !(allowHttp || LOCAL_HOSTS.has(u.hostname))) {
      throw new TypeError('baseUrl must use https (your API key would be sent in clear text).');
    }
    if (u.username || u.password) throw new TypeError('baseUrl must not contain credentials.');
    let url = baseUrl.replace(/\/+$/, '');
    if (!url.endsWith('/api/v1')) url += '/api/v1';
    return url;
  }
  if (!workspace) {
    throw new TypeError("Pass workspace (your subdomain, e.g. 'acme') or baseUrl, or set WIZARDAPP_WORKSPACE.");
  }
  const ws = String(workspace).trim().toLowerCase();
  if (!WORKSPACE_RE.test(ws)) {
    throw new TypeError("workspace must be your workspace subdomain, for example 'acme' for acme.wizardapp.io.");
  }
  return `https://${ws}.wizardapp.io/api/v1`;
}

function encodeParams(params) {
  const q = new URLSearchParams();
  for (const [key, value] of Object.entries(params || {})) {
    if (value === undefined || value === null) continue;
    q.append(key, String(value));
  }
  const s = q.toString();
  return s ? `?${s}` : '';
}

function intHeader(headers, name) {
  const v = headers.get(name);
  if (v == null) return null;
  const n = parseInt(v, 10);
  return Number.isNaN(n) ? null : n;
}

const sleep = (s) => new Promise((resolve) => setTimeout(resolve, s * 1000));

class Page {
  constructor(payload) {
    const p = payload && typeof payload === 'object' ? payload : {};
    const pg = p.pagination || {};
    this.data = Array.isArray(p.data) ? p.data : [];
    this.page = Number(pg.page) || 1;
    this.perPage = Number(pg.per_page) || this.data.length;
    this.total = Number(pg.total) || this.data.length;
    this.totalPages = Number(pg.total_pages) || 1;
  }
  get hasNext() {
    return this.page < this.totalPages;
  }
  [Symbol.iterator]() {
    return this.data[Symbol.iterator]();
  }
}

function unwrap(payload) {
  if (payload && typeof payload === 'object' && 'data' in payload) return payload.data;
  return payload && typeof payload === 'object' ? payload : {};
}

class Resource {
  constructor(client, path) {
    this._client = client;
    this._path = `/${path}`;
  }

  /** One page of records. Extra keys are filters, e.g. { status: 'published' }. */
  async list({ page, perPage, sort, order, ...filters } = {}) {
    const params = { ...filters, page, per_page: perPage, sort, order };
    return new Page(await this._client.request('GET', this._path, { params }));
  }

  /** Every record across all pages: `for await (const job of client.jobs.iterate())`. */
  async *iterate({ perPage = 100, ...filters } = {}) {
    let page = 1;
    for (;;) {
      const result = await this.list({ ...filters, page, perPage });
      for (const item of result.data) yield item;
      if (!result.hasNext || result.data.length === 0) return;
      page += 1;
    }
  }

  async get(id) {
    return unwrap(await this._client.request('GET', `${this._path}/${quoteSegment(id)}`));
  }

  async create(data) {
    return unwrap(await this._client.request('POST', this._path, { body: data || {} }));
  }

  async update(id, data) {
    return unwrap(await this._client.request('PUT', `${this._path}/${quoteSegment(id)}`, { body: data || {} }));
  }

  async delete(id) {
    await this._client.request('DELETE', `${this._path}/${quoteSegment(id)}`);
  }
}

class Leads extends Resource {
  /** Converts a lead into a customer and returns the updated record. */
  async convert(id) {
    return unwrap(await this._client.request('POST', `${this._path}/${quoteSegment(id)}/convert`));
  }
}

class ContentTypes {
  constructor(client) {
    this._client = client;
  }
  async list({ page, perPage, ...filters } = {}) {
    const params = { ...filters, page, per_page: perPage };
    return new Page(await this._client.request('GET', '/content-types', { params }));
  }
  async get(id) {
    return unwrap(await this._client.request('GET', `/content-types/${quoteSegment(id)}`));
  }
}

class WizardApplication {
  /**
   * @param {object} [options]
   * @param {string} [options.apiKey] defaults to process.env.WIZARDAPP_API_KEY
   * @param {string} [options.workspace] your subdomain, defaults to process.env.WIZARDAPP_WORKSPACE
   * @param {string} [options.baseUrl] full API URL instead of workspace (https only)
   * @param {number} [options.timeout] seconds, default 30
   * @param {number} [options.maxRetries] default 2
   * @param {boolean} [options.allowHttp] allow a plain http baseUrl (local testing only)
   */
  constructor(options = {}) {
    const env = typeof process !== 'undefined' && process.env ? process.env : {};
    const apiKey = options.apiKey || env.WIZARDAPP_API_KEY;
    if (!apiKey) throw new TypeError('An API key is required (pass apiKey or set WIZARDAPP_API_KEY).');
    if (/\s/.test(apiKey)) throw new TypeError('The API key must not contain whitespace.');
    Object.defineProperty(this, '_apiKey', { value: apiKey, enumerable: false });
    this.baseUrl = resolveBaseUrl({
      workspace: options.workspace || env.WIZARDAPP_WORKSPACE,
      baseUrl: options.baseUrl,
      allowHttp: Boolean(options.allowHttp),
    });
    this.timeout = options.timeout ?? 30;
    this.maxRetries = Math.max(0, options.maxRetries ?? 2);
    this.lastRateLimit = null;

    this.jobs = new Resource(this, 'jobs');
    this.candidates = new Resource(this, 'candidates');
    this.companies = new Resource(this, 'companies');
    this.bookings = new Resource(this, 'bookings');
    this.leads = new Leads(this, 'leads');
    this.contentTypes = new ContentTypes(this);
  }

  /** Items of one content type, by its slug or ID (e.g. 'blog-posts'). */
  content(contentType) {
    return new Resource(this, `content/${quoteSegment(contentType)}`);
  }

  toJSON() {
    return { baseUrl: this.baseUrl };
  }

  /** Sends a request and returns the decoded JSON body. */
  async request(method, path, { params, body } = {}) {
    method = method.toUpperCase();
    const url = this.baseUrl + path + encodeParams(params);
    const headers = {
      Authorization: `Bearer ${this._apiKey}`,
      Accept: 'application/json',
      'User-Agent': `wizardapplication-node/${VERSION}`,
    };
    let payload;
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      payload = JSON.stringify(body);
    }

    for (let attempt = 0; ; attempt++) {
      let res;
      try {
        res = await fetch(url, { method, headers, body: payload, signal: AbortSignal.timeout(this.timeout * 1000), redirect: 'error' });
      } catch (e) {
        if (attempt < this.maxRetries && IDEMPOTENT.has(method)) {
          await sleep(this._wait(attempt + 1, null));
          continue;
        }
        throw new ConnectionError(`Could not reach ${this.baseUrl}: ${e && e.message ? e.message : e}`);
      }
      this._rememberRateLimit(res.headers);
      const text = await res.text();
      let data = null;
      if (text) {
        try {
          data = JSON.parse(text);
        } catch {
          data = { message: text.slice(0, 500) };
        }
      }
      if (res.ok) return data;

      const err = errorForStatus(res.status, data, intHeader(res.headers, 'Retry-After'));
      if (attempt < this.maxRetries && this._shouldRetry(method, res.status, err)) {
        await sleep(this._wait(attempt + 1, err.retryAfter));
        continue;
      }
      throw err;
    }
  }

  _shouldRetry(method, status, err) {
    if (status === 429) return err.code !== 'monthly_limit_exceeded';
    return RETRY_STATUSES.has(status) && IDEMPOTENT.has(method);
  }

  _wait(attempt, retryAfter) {
    if (retryAfter != null && retryAfter >= 0) return Math.min(retryAfter, MAX_RETRY_WAIT);
    return Math.min(0.5 * 2 ** (attempt - 1), 8);
  }

  _rememberRateLimit(headers) {
    if (headers.get('X-RateLimit-Limit') == null) return;
    this.lastRateLimit = {
      limit: intHeader(headers, 'X-RateLimit-Limit'),
      remaining: intHeader(headers, 'X-RateLimit-Remaining'),
      reset: intHeader(headers, 'X-RateLimit-Reset'),
    };
  }
}

module.exports = {
  WizardApplication,
  Page,
  VERSION,
  WizardApplicationError,
  ConnectionError,
  BadRequestError,
  AuthenticationError,
  PermissionDeniedError,
  NotFoundError,
  ValidationError,
  RateLimitError,
  ServerError,
};
module.exports.default = WizardApplication;
