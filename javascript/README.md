# Wizard Application JavaScript SDK

Official JavaScript and TypeScript client for the Wizard Application API. Node.js 18 or later, no dependencies. Works with `import` and `require`, and includes TypeScript types.

Use it in server-side code only. Never put an API key in a web page.

## Install

From your project:

```bash
npm install /path/to/javascript
```

## Quick start

```js
import WizardApplication from 'wizardapplication';
// const { WizardApplication } = require('wizardapplication');

const client = new WizardApplication({ apiKey: 'wa_...', workspace: 'acme' }); // acme.wizardapp.io

const page = await client.jobs.list({ status: 'published', perPage: 50 });
for (const job of page) console.log(job.title);
console.log(page.total, 'jobs in', page.totalPages, 'pages');
```

Leave out `apiKey` and `workspace` to read them from `WIZARDAPP_API_KEY` and `WIZARDAPP_WORKSPACE`.

## Records

```js
let job = await client.jobs.get('JOB_ID');
job = await client.jobs.create({ title: 'Senior Developer', location: 'Leeds', employment_type: 'permanent', description: '...' });
job = await client.jobs.update(job.id, { status: 'published' });
await client.jobs.delete(job.id);
```

The same calls work on `client.candidates`, `client.companies`, `client.bookings` and `client.leads`.

```js
const customer = await client.leads.convert('LEAD_ID');

const types = await client.contentTypes.list();
const posts = await client.content('blog-posts').list({ status: 'published' });
```

## Users

```js
const people = await client.users.list({ status: 'active', search: 'smith' });
const user = await client.users.create({ email: 'sam@acme.com', first_name: 'Sam', last_name: 'Lee',
  role: 'Recruiter', manager_email: 'alex@acme.com' });
await client.users.update(user.id, { job_title: 'Senior Recruiter' });
await client.users.sendInvitation(user.id);

const result = await client.users.importUsers([
  { email: 'lead@acme.com', first_name: 'Jo', last_name: 'Hart', role: 'Manager' },
  { email: 'new@acme.com', first_name: 'Kim', last_name: 'Ng', manager_email: 'lead@acme.com' },
]);
console.log(result.summary); // { created: 2, skipped: 0, failed: 0 }
```

Users can't be deleted through the API. Every user you create is a paid seat, so the key needs the per-seat charge accepted when it is created.

## AI employees

```js
for (const employee of await client.aiEmployees.list()) console.log(employee.id, employee.name);

const result = await client.aiEmployees.ask('Accounts Assistant',
  'Chase every invoice more than 14 days overdue.');
console.log(result.summary, result.awaiting_approval);
```

`ask` hands the job to the AI employee through your workspace's MCP server and waits for it to finish (up to a few minutes). Changes it wants to make wait in its action queue in Wizard Application until one of the people it reports to approves them, unless you trust it with that kind of change. The key needs the `ai_employees.write` permission.

`client.callTool(name, args)` calls any other MCP tool your key allows.

## Every page

```js
for await (const candidate of client.candidates.iterate({ status: 'screening' })) {
  console.log(candidate.email);
}
```

## Sorting

```js
await client.jobs.list({ sort: 'title', order: 'asc' });
```

## Errors

```js
import { ValidationError, RateLimitError, WizardApplicationError } from 'wizardapplication';

try {
  await client.jobs.create({ location: 'Leeds' });
} catch (e) {
  if (e instanceof ValidationError) console.log(e.errors); // { title: ['Title is required'] }
  else if (e instanceof RateLimitError) console.log('wait', e.retryAfter, 'seconds');
  else if (e instanceof WizardApplicationError) console.log(e.status, e.code, e.message);
  else throw e;
}
```

Error classes: `ConnectionError`, `BadRequestError` (400), `AuthenticationError` (401), `PermissionDeniedError` (403), `NotFoundError` (404), `ValidationError` (422), `RateLimitError` (429), `ServerError` (5xx). All extend `WizardApplicationError`.

## Options

```js
new WizardApplication({
  apiKey: 'wa_...',
  workspace: 'acme',   // or baseUrl: 'https://acme.wizardapp.io/api/v1'
  timeout: 30,         // seconds
  maxRetries: 2,       // 0 turns retries off
});
```

`client.lastRateLimit` holds the limit, remaining requests and seconds until reset from the latest response.

See the [main README](../README.md) for filters, retries and security.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
