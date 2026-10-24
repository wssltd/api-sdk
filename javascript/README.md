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
