# Wizard Application Python SDK

Official Python client for the Wizard Application API. Python 3.8 or later, no dependencies.

## Install

From GitHub:

```bash
pip install "git+https://github.com/wssltd/api-sdk.git@v1.1.0#subdirectory=python"
```

Or from this folder:

```bash
pip install .
```

Or copy the `wizardapplication` folder into your project.

## Quick start

```python
from wizardapplication import WizardApplication

client = WizardApplication(api_key="wa_...", workspace="acme")  # acme.wizardapp.io

page = client.jobs.list(status="published", per_page=50)
for job in page:
    print(job["title"])
print(page.total, "jobs in", page.total_pages, "pages")
```

Leave out `api_key` and `workspace` to read them from `WIZARDAPP_API_KEY` and `WIZARDAPP_WORKSPACE`.

## Records

```python
job = client.jobs.get("JOB_ID")
job = client.jobs.create({"title": "Senior Developer", "location": "Leeds",
                          "employment_type": "permanent", "description": "..."})
job = client.jobs.update(job["id"], {"status": "published"})
client.jobs.delete(job["id"])
```

The same calls work on `client.candidates`, `client.companies`, `client.bookings` and `client.leads`.

```python
customer = client.leads.convert("LEAD_ID")

types = client.content_types.list()
posts = client.content("blog-posts").list(status="published")
post = client.content("blog-posts").create({"title": "Hello", "fields": {"body": "..."}})
```

## Users

```python
people = client.users.list(status="active", search="smith")
user = client.users.create({"email": "sam@acme.com", "first_name": "Sam", "last_name": "Lee",
                            "role": "Recruiter", "manager_email": "alex@acme.com"})
client.users.update(user["id"], {"job_title": "Senior Recruiter"})
client.users.send_invitation(user["id"])

result = client.users.import_users([
    {"email": "lead@acme.com", "first_name": "Jo", "last_name": "Hart", "role": "Manager"},
    {"email": "new@acme.com", "first_name": "Kim", "last_name": "Ng", "manager_email": "lead@acme.com"},
])
print(result["summary"])  # {"created": 2, "skipped": 0, "failed": 0}
```

Users can't be deleted through the API. Every user you create is a paid seat, so the key needs the per-seat charge accepted when it is created.

## AI employees

```python
for employee in client.ai_employees.list():
    print(employee["id"], employee["name"])

result = client.ai_employees.ask("Accounts Assistant",
                                 "Chase every invoice more than 14 days overdue.")
print(result["summary"], result["awaiting_approval"])
```

`ask` hands the job to the AI employee through your workspace's MCP server and waits for it to finish (up to a few minutes). Changes it wants to make wait in its action queue in Wizard Application until one of the people it reports to approves them, unless you trust it with that kind of change. The key needs the `ai_employees.write` permission.

`client.call_tool(name, arguments)` calls any other MCP tool your key allows.

## Every page

```python
for candidate in client.candidates.iterate(status="screening"):
    print(candidate["email"])
```

## Sorting

```python
client.jobs.list(sort="title", order="asc")
```

## Errors

```python
from wizardapplication import NotFoundError, ValidationError, RateLimitError, WizardApplicationError

try:
    client.jobs.create({"location": "Leeds"})
except ValidationError as e:
    print(e.errors)          # {"title": ["Title is required"]}
except RateLimitError as e:
    print("wait", e.retry_after, "seconds")
except WizardApplicationError as e:
    print(e.status, e.code, e.message)
```

Error classes: `ConnectionError`, `BadRequestError` (400), `AuthenticationError` (401), `PermissionDeniedError` (403), `NotFoundError` (404), `ValidationError` (422), `RateLimitError` (429), `ServerError` (5xx). All extend `WizardApplicationError`.

## Options

```python
WizardApplication(
    api_key="wa_...",
    workspace="acme",       # or base_url="https://acme.wizardapp.io/api/v1"
    timeout=30,             # seconds
    max_retries=2,          # 0 turns retries off
)
```

`client.last_rate_limit` holds the limit, remaining requests and seconds until reset from the latest response.

See the [main README](../README.md) for filters, retries and security.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
