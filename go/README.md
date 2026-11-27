# Wizard Application Go SDK

Official Go client for the Wizard Application API. Go 1.18 or later, standard library only.

## Install

```bash
go get github.com/wssltd/api-sdk/go@v1.1.0
```

Or copy this folder into your project and point your `go.mod` at it:

```
require github.com/wssltd/api-sdk/go v1.1.0
replace github.com/wssltd/api-sdk/go => ./third_party/wizardapplication-go
```

## Quick start

```go
import wa "github.com/wssltd/api-sdk/go"

client, err := wa.New("wa_...", wa.WithWorkspace("acme")) // acme.wizardapp.io
if err != nil {
	log.Fatal(err)
}

page, err := client.Jobs.List(ctx, wa.Params{"status": "published", "per_page": 50})
if err != nil {
	log.Fatal(err)
}
for _, job := range page.Data {
	fmt.Println(job["title"])
}
fmt.Println(page.Pagination.Total, "jobs in", page.Pagination.TotalPages, "pages")
```

Pass an empty key and no workspace to read them from `WIZARDAPP_API_KEY` and `WIZARDAPP_WORKSPACE`. Records are `wa.Record` (`map[string]interface{}`).

## Records

```go
job, err := client.Jobs.Get(ctx, "JOB_ID")
job, err = client.Jobs.Create(ctx, wa.Record{"title": "Senior Developer", "location": "Leeds",
	"employment_type": "permanent", "description": "..."})
job, err = client.Jobs.Update(ctx, job["id"].(string), wa.Record{"status": "published"})
err = client.Jobs.Delete(ctx, job["id"].(string))
```

`Create` and `Update` accept any value that encodes to JSON, including your own structs. The same calls work on `client.Candidates`, `client.Companies`, `client.Bookings` and `client.Leads`.

```go
customer, err := client.Leads.Convert(ctx, "LEAD_ID")

types, err := client.ContentTypes.List(ctx, nil)
posts, err := client.Content("blog-posts").List(ctx, wa.Params{"status": "published"})
```

To decode into your own types, use `client.Do(ctx, "GET", "/jobs/JOB_ID", nil, nil, &out)`.

## Users

```go
people, err := client.Users.List(ctx, wizardapplication.Params{"status": "active", "search": "smith"})
user, err := client.Users.Create(ctx, wizardapplication.Record{"email": "sam@acme.com", "first_name": "Sam",
	"last_name": "Lee", "role": "Recruiter", "manager_email": "alex@acme.com"})
_, err = client.Users.Update(ctx, user["id"].(string), wizardapplication.Record{"job_title": "Senior Recruiter"})
_, err = client.Users.SendInvitation(ctx, user["id"].(string))

result, err := client.Users.Import(ctx, []wizardapplication.Record{
	{"email": "lead@acme.com", "first_name": "Jo", "last_name": "Hart", "role": "Manager"},
	{"email": "new@acme.com", "first_name": "Kim", "last_name": "Ng", "manager_email": "lead@acme.com"},
})
fmt.Println(result.Summary.Created, result.Summary.Skipped, result.Summary.Failed)
```

Users can't be deleted through the API. Every user you create is a paid seat, so the key needs the per-seat charge accepted when it is created.

## AI employees

```go
employees, err := client.AIEmployees.List(ctx)
for _, e := range employees {
	fmt.Println(e.ID, e.Name)
}

result, err := client.AIEmployees.Ask(ctx, "Accounts Assistant", "Chase every invoice more than 14 days overdue.", "")
fmt.Println(result.Summary, result.AwaitingApproval)
```

`Ask` hands the job to the AI employee through your workspace's MCP server and waits for it to finish (up to a few minutes). Changes it wants to make wait in its action queue in Wizard Application until one of the people it reports to approves them, unless you trust it with that kind of change. The key needs the `ai_employees.write` permission.

`client.CallTool(ctx, name, args, &out)` calls any other MCP tool your key allows. `Ask` uses its own longer timeout, so the client timeout can stay short.

## Every page

```go
err := client.Candidates.Iterate(ctx, wa.Params{"status": "screening"}, func(c wa.Record) error {
	fmt.Println(c["email"])
	return nil // return an error to stop early
})
```

## Errors

```go
_, err := client.Jobs.Create(ctx, wa.Record{"location": "Leeds"})
var apiErr *wa.Error
if errors.As(err, &apiErr) {
	switch {
	case apiErr.IsValidation():
		fmt.Println(apiErr.Errors) // map[title:[Title is required]]
	case apiErr.IsRateLimit():
		fmt.Println("wait", apiErr.RetryAfter, "seconds")
	default:
		fmt.Println(apiErr.Status, apiErr.Code, apiErr.Message)
	}
}
```

Checks: `IsConnection`, `IsAuthentication` (401), `IsPermissionDenied` (403), `IsNotFound` (404), `IsValidation` (422), `IsRateLimit` (429), `IsServer` (5xx).

## Options

```go
wa.New("wa_...",
	wa.WithWorkspace("acme"),          // or wa.WithBaseURL("https://acme.wizardapp.io/api/v1")
	wa.WithTimeout(30*time.Second),
	wa.WithMaxRetries(2),              // 0 turns retries off
	wa.WithHTTPClient(myClient),       // redirects are still refused
)
```

Every call takes a `context.Context`, which also cancels retry waits. `client.LastRateLimit()` returns the limits from the latest response. The client is safe for concurrent use.

See the [main README](../README.md) for filters, retries and security.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
