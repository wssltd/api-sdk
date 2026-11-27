# Wizard Application Ruby SDK

Official Ruby client for the Wizard Application API. Ruby 3.0 or later, standard library only.

## Install

In your Gemfile, from GitHub:

```ruby
gem "wizardapplication", git: "https://github.com/wssltd/api-sdk.git", tag: "v1.1.0", glob: "ruby/*.gemspec"
```

Or from a local copy:

```ruby
gem "wizardapplication", path: "path/to/ruby"
```

Or build and install the gem from this folder:

```bash
gem build wizardapplication.gemspec
gem install wizardapplication-1.1.0.gem
```

## Quick start

```ruby
require "wizardapplication"

client = WizardApplication::Client.new(api_key: "wa_...", workspace: "acme") # acme.wizardapp.io

page = client.jobs.list(status: "published", per_page: 50)
page.each { |job| puts job["title"] }
puts "#{page.total} jobs in #{page.total_pages} pages"
```

Leave out `api_key:` and `workspace:` to read them from `WIZARDAPP_API_KEY` and `WIZARDAPP_WORKSPACE`.

## Records

```ruby
job = client.jobs.get("JOB_ID")
job = client.jobs.create(title: "Senior Developer", location: "Leeds",
                         employment_type: "permanent", description: "...")
job = client.jobs.update(job["id"], status: "published")
client.jobs.delete(job["id"])
```

The same calls work on `client.candidates`, `client.companies`, `client.bookings` and `client.leads`. Records are hashes with string keys.

```ruby
customer = client.leads.convert("LEAD_ID")

types = client.content_types.list
posts = client.content("blog-posts").list(status: "published")
```

## Users

```ruby
people = client.users.list(status: "active", search: "smith")
user = client.users.create("email" => "sam@acme.com", "first_name" => "Sam", "last_name" => "Lee",
                           "role" => "Recruiter", "manager_email" => "alex@acme.com")
client.users.update(user["id"], "job_title" => "Senior Recruiter")
client.users.send_invitation(user["id"])

result = client.users.import_users([
  { "email" => "lead@acme.com", "first_name" => "Jo", "last_name" => "Hart", "role" => "Manager" },
  { "email" => "new@acme.com", "first_name" => "Kim", "last_name" => "Ng", "manager_email" => "lead@acme.com" }
])
p result["summary"] # {"created"=>2, "skipped"=>0, "failed"=>0}
```

Users can't be deleted through the API. Every user you create is a paid seat, so the key needs the per-seat charge accepted when it is created.

## AI employees

```ruby
client.ai_employees.list.each { |employee| puts "#{employee["id"]} #{employee["name"]}" }

result = client.ai_employees.ask("Accounts Assistant", "Chase every invoice more than 14 days overdue.")
puts result["summary"], result["awaiting_approval"]
```

`ask` hands the job to the AI employee through your workspace's MCP server and waits for it to finish (up to a few minutes). Changes it wants to make wait in its action queue in Wizard Application until one of the people it reports to approves them, unless you trust it with that kind of change. The key needs the `ai_employees.write` permission.

`client.call_tool(name, arguments)` calls any other MCP tool your key allows.

## Every page

```ruby
client.candidates.iterate(status: "screening") { |candidate| puts candidate["email"] }

# or as an Enumerator
emails = client.candidates.iterate(status: "screening").map { |c| c["email"] }
```

## Errors

```ruby
begin
  client.jobs.create(location: "Leeds")
rescue WizardApplication::ValidationError => e
  p e.errors # {"title"=>["Title is required"]}
rescue WizardApplication::RateLimitError => e
  puts "Wait #{e.retry_after} seconds"
rescue WizardApplication::Error => e
  puts "#{e.status} #{e.code} #{e.message}"
end
```

Errors: `ConnectionError`, `BadRequestError` (400), `AuthenticationError` (401), `PermissionDeniedError` (403), `NotFoundError` (404), `ValidationError` (422), `RateLimitError` (429), `ServerError` (5xx). All extend `WizardApplication::Error`.

## Options

```ruby
WizardApplication::Client.new(
  api_key: "wa_...",
  workspace: "acme",   # or base_url: "https://acme.wizardapp.io/api/v1"
  timeout: 30,         # seconds
  max_retries: 2       # 0 turns retries off
)
```

`client.last_rate_limit` holds the limit, remaining requests and seconds until reset from the latest response.

See the [main README](../README.md) for filters, retries and security.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
