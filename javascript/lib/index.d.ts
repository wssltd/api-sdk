// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

// Type definitions for the Wizard Application JavaScript SDK.

export type Record = { [field: string]: any };

export interface ClientOptions {
  /** Defaults to process.env.WIZARDAPP_API_KEY. */
  apiKey?: string;
  /** Your workspace subdomain, e.g. "acme". Defaults to process.env.WIZARDAPP_WORKSPACE. */
  workspace?: string;
  /** Full API URL instead of workspace. Must be https. */
  baseUrl?: string;
  /** Request timeout in seconds. Default 30. */
  timeout?: number;
  /** Retries for 429 and, on GET/PUT/DELETE, network errors and 502/503/504. Default 2. */
  maxRetries?: number;
  /** Allow a plain http baseUrl. Local testing only. */
  allowHttp?: boolean;
}

export interface ListOptions {
  page?: number;
  /** Up to 100. */
  perPage?: number;
  sort?: string;
  order?: 'asc' | 'desc';
  /** Any other key is sent as a filter, e.g. status, search. */
  [filter: string]: string | number | boolean | undefined | null;
}

export interface RateLimit {
  limit: number | null;
  remaining: number | null;
  /** Seconds until the per-minute window resets. */
  reset: number | null;
}

export declare class Page<T = Record> implements Iterable<T> {
  data: T[];
  page: number;
  perPage: number;
  total: number;
  totalPages: number;
  readonly hasNext: boolean;
  [Symbol.iterator](): Iterator<T>;
}

export declare class Resource<T = Record> {
  list(options?: ListOptions): Promise<Page<T>>;
  iterate(options?: ListOptions): AsyncGenerator<T, void, unknown>;
  get(id: string): Promise<T>;
  create(data: Partial<T>): Promise<T>;
  update(id: string, data: Partial<T>): Promise<T>;
  delete(id: string): Promise<void>;
}

export declare class Leads<T = Record> extends Resource<T> {
  convert(id: string): Promise<T>;
}

export interface ImportResult {
  /** One entry per row, in order. */
  data: { index: number; email: string; status: 'created' | 'skipped' | 'failed'; user?: Record; error?: Record }[];
  summary: { created: number; skipped: number; failed: number };
}

/** Team members. The API has no delete; creating a user adds a paid seat. */
export declare class Users<T = Record> extends Resource<T> {
  /** Creates up to 100 users in one call. */
  importUsers(users: Partial<T>[]): Promise<ImportResult>;
  /** Sends, or sends again, the invitation email. */
  sendInvitation(id: string): Promise<T>;
}

export interface AIEmployee {
  id: string;
  name: string;
  purpose?: string;
  paused: boolean;
}

export interface AskResult {
  ai_employee: string;
  summary: string;
  /** Changes it made itself. */
  changes_made: number;
  /** Changes waiting for approval in Wizard Application. */
  awaiting_approval: number;
  approval_url: string;
}

/** The workspace's AI employees, through its MCP server (permission ai_employees.write). */
export declare class AIEmployees {
  list(): Promise<AIEmployee[]>;
  /** Gives an AI employee (by id or name) a job. Can take a few minutes. */
  ask(aiEmployee: string, instruction: string, options?: { context?: string }): Promise<AskResult>;
}

export declare class ContentTypes<T = Record> {
  list(options?: Omit<ListOptions, 'sort' | 'order'>): Promise<Page<T>>;
  get(id: string): Promise<T>;
}

export declare class WizardApplication {
  constructor(options?: ClientOptions);
  readonly baseUrl: string;
  timeout: number;
  maxRetries: number;
  lastRateLimit: RateLimit | null;
  readonly jobs: Resource;
  readonly candidates: Resource;
  readonly companies: Resource;
  readonly bookings: Resource;
  readonly leads: Leads;
  readonly contentTypes: ContentTypes;
  readonly users: Users;
  readonly aiEmployees: AIEmployees;
  /** Items of one content type, by its slug or ID. */
  content(contentType: string): Resource;
  /** Calls one tool on the workspace's MCP server. */
  callTool(name: string, args?: Record, options?: { timeout?: number }): Promise<any>;
  request(method: string, path: string, options?: { params?: Record; body?: unknown; timeout?: number }): Promise<any>;
}

export declare class WizardApplicationError extends Error {
  status: number;
  /** The API error code, e.g. "validation_error". */
  code: string | null;
  /** Field errors for a 422, e.g. { title: ["Title is required"] }. */
  errors: { [field: string]: string[] };
  /** Seconds to wait, for a 429. */
  retryAfter: number | null;
  body: unknown;
}
export declare class ConnectionError extends WizardApplicationError {}
export declare class BadRequestError extends WizardApplicationError {}
export declare class AuthenticationError extends WizardApplicationError {}
export declare class PermissionDeniedError extends WizardApplicationError {}
export declare class NotFoundError extends WizardApplicationError {}
export declare class ValidationError extends WizardApplicationError {}
export declare class RateLimitError extends WizardApplicationError {}
export declare class ServerError extends WizardApplicationError {}

export declare const VERSION: string;
export default WizardApplication;
