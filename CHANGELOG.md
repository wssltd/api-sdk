# Changelog

## 1.1.0 (27 November 2026)

- Users: list, iterate, get, create and update team members, import up to 100 in one call, and send or resend an invitation. Users can't be deleted through the API.
- AI employees: list them, and ask one to do a job in plain words through your workspace's MCP server. Changes it wants to make wait for approval in Wizard Application unless you trust it with them.
- A call tool method for any other MCP tool your API key allows.
- A per-request timeout, so a long AI employee job doesn't need a long timeout on every call.
- .NET: timeouts now apply per request rather than through HttpClient.Timeout; the configured timeout is unchanged.
- Go: Error.IsConnection is now false for errors that carry a code, such as tool_error.
- Ruby: a request that times out is no longer sent a second time by Net::HTTP, so the timeout you set is the time it takes, and only the client's own retries apply.

## 1.0.0 (24 October 2026)

First release, in Python, JavaScript and TypeScript, PHP, Ruby, Go, .NET and Java.

- Jobs, candidates, companies, bookings and leads: list, iterate, get, create, update and delete.
- Lead conversion to a customer.
- Content types (list, get) and content items (list, iterate, get, create, update, delete).
- Paging, sorting and filters.
- One error type per kind of failure, with field errors for validation failures.
- Automatic retries for rate limits and temporary server errors.
- Rate limit details from the latest response.
- https only, no redirects followed, workspace and ID checks.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.
