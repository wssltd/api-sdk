# Wizard Application SDKs

Official client libraries for the Wizard Application API, version 1.0.0.

Each folder is a complete SDK for one language, with its own README and a quick start example.

| Folder | Language | Requires | Dependencies |
|---|---|---|---|
| [`python/`](python/) | Python | 3.8 or later | None |
| [`javascript/`](javascript/) | JavaScript and TypeScript (Node.js) | Node.js 18 or later | None |
| [`php/`](php/) | PHP | 8.0 or later, curl and json extensions | None |
| [`ruby/`](ruby/) | Ruby | 3.0 or later | None |
| [`go/`](go/) | Go | 1.18 or later | None |
| [`dotnet/`](dotnet/) | C# and .NET | .NET 6 or later | None |
| [`java/`](java/) | Java | Java 11 or later | None |

## Before you start

1. **Plan.** API access is included with the Professional and Enterprise plans.
2. **API key.** Create one in Wizard Application under **Settings > API Keys**, and give it only the permissions it needs (for example `jobs.read`). Copy the key when it is shown; it isn't shown again.
3. **Workspace.** Your workspace is the first part of your Wizard Application address. For `acme.wizardapp.io` it is `acme`.

Every SDK reads the key from the `WIZARDAPP_API_KEY` environment variable and the workspace from `WIZARDAPP_WORKSPACE` when you don't pass them in code. Keep keys out of source control.

## Getting the SDKs

Download the latest release from the [Releases page](https://github.com/wssltd/api-sdk/releases), or clone this repository. Each language's README explains how to add it to a project.

Some package tools can install straight from this repository:

| Language | Command |
|---|---|
| Python | `pip install "git+https://github.com/wssltd/api-sdk.git@v1.0.0#subdirectory=python"` |
| Ruby | `gem "wizardapplication", git: "https://github.com/wssltd/api-sdk.git", tag: "v1.0.0", glob: "ruby/*.gemspec"` in your Gemfile |
| Go | `go get github.com/wssltd/api-sdk/go@v1.0.0` |

## What every SDK does

All seven SDKs work the same way. Only the naming follows each language's conventions.

**Resources**

| Resource | Calls | Permission |
|---|---|---|
| Jobs | list, iterate, get, create, update, delete | `jobs.read`, `jobs.write` |
| Candidates | list, iterate, get, create, update, delete | `candidates.read`, `candidates.write` |
| Companies | list, iterate, get, create, update, delete | `companies.read`, `companies.write` |
| Bookings | list, iterate, get, create, update, delete | `bookings.read`, `bookings.write` |
| Leads | list, iterate, get, create, update, delete, convert | `leads.read`, `leads.write` |
| Content types | list, get | `content.read` |
| Content items (`content("blog-posts")`) | list, iterate, get, create, update, delete | `content.read`, `content.write` |

Content needs the Content feature turned on in your workspace.

**Lists and paging.** `list` returns one page with the records and the page details (page, per page, total, total pages). Pass `page`, `per_page` (up to 100), `sort` and `order` (`asc` or `desc`), plus any filters the endpoint supports, such as `status` or `search`. `iterate` goes through every page for you.

**Filters by resource**

| Resource | Filters | Sort fields |
|---|---|---|
| Jobs | `status`, `employment_type`, `search` | created_at, updated_at, title, location, status, employment_type, salary_min, salary_max, published_at |
| Candidates | `status`, `employment_type`, `assigned_to`, `rating`, `date_from`, `date_to`, `search` | created_at, updated_at, first_name, last_name, email, status, employment_type, current_job_title, current_company, available_from |
| Companies | `status`, `search` | created_at, updated_at, name, industry, status |
| Bookings | `status`, `booking_type`, `start_date`, `end_date`, `search` | created_at, updated_at, start_datetime, end_datetime, title, status, booking_type |
| Leads | `status`, `source`, `assigned_to`, `search` | created_at, updated_at, first_name, last_name, email, company_name, status, source, last_contacted_at |
| Content items | `status`, `search` | created_at, updated_at, title, slug, status, published_at |

**Records.** Records come back as the language's natural map or object type, with the same field names as the API (`first_name`, `salary_min` and so on). The full field list for each resource is in the [API documentation](https://wizardapplication.com/development/api-docs).

**Errors.** Every failure raises one error type with the HTTP status, the API error code, the message and, for validation errors, the problem with each field:

| Status | Error | When |
|---|---|---|
| 0 | Connection | No response: network, DNS, timeout or a refused redirect |
| 400 | Bad request | The request was malformed |
| 401 | Authentication | The key is missing, wrong or expired |
| 403 | Permission denied | The key lacks a permission, or the plan has no API access (`plan_upgrade_required`) |
| 404 | Not found | The record doesn't exist |
| 422 | Validation | The data failed validation; each field's problems are included |
| 429 | Rate limit | Too many requests this minute (`rate_limit_exceeded`) or this month (`monthly_limit_exceeded`) |
| 5xx | Server | Something went wrong on our side |

**Retries.** A request that hits the per-minute rate limit is retried after the wait the API gives (up to 60 seconds). Network errors and 502, 503 and 504 responses are retried with a short backoff, but only for reads, updates and deletes, so a create is never sent twice. The default is 2 retries; you can change it or turn retries off.

**Rate limits.** The limits from the latest response (limit, remaining, seconds until reset) are available on the client.

**Security.**
- The key is only ever sent over https. A plain http address is refused unless it is your own machine or you turn on the allow-http option for local testing.
- Redirects are never followed, so the key can't be passed to another server.
- The workspace name is checked, so it can't point requests at another host.
- IDs are escaped, so an ID can't change which endpoint is called.
- The key never appears when the client is printed or logged.
- The SDKs are for server-side code. Never put an API key in a web page or a mobile app.

## Licence and contributions

These SDKs are published so you can read, use and adapt them for your own integrations. They are source available, not open source: they are licensed under the [Wizard Application SDK Licence](LICENSE), which allows their use only with the Wizard Application API.

- **Bug reports and questions** are welcome as GitHub issues, or by email to support@wizardapplication.com.
- **Pull requests** aren't accepted. If you'd like a change, open an issue describing it.
- **Security problems** must not be reported in a public issue. See [SECURITY.md](SECURITY.md).

## Help

API reference: https://wizardapplication.com/development/api-docs

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
