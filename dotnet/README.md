# Wizard Application .NET SDK

Official C# client for the Wizard Application API. .NET 6 or later, no dependencies beyond the .NET libraries.

## Install

Add the project to your solution and reference it:

```bash
dotnet add reference path/to/dotnet/src/WizardApplication/WizardApplication.csproj
```

Or build a package with `dotnet pack src/WizardApplication -c Release` and install the `.nupkg`.

## Quick start

```csharp
using WizardApplication;

using var client = new WizardApplicationClient("wa_...", "acme"); // acme.wizardapp.io

var page = await client.Jobs.ListAsync(new ListOptions { PerPage = 50 }.Where("status", "published"));
foreach (var job in page.Data) Console.WriteLine((string?)job["title"]);
Console.WriteLine($"{page.Pagination.Total} jobs in {page.Pagination.TotalPages} pages");
```

With `new WizardApplicationClient(new WizardApplicationOptions())` the key and workspace come from `WIZARDAPP_API_KEY` and `WIZARDAPP_WORKSPACE`. Create one client and reuse it; it is thread safe.

Records are `System.Text.Json.Nodes.JsonObject`.

## Records

```csharp
var job = await client.Jobs.GetAsync("JOB_ID");
job = await client.Jobs.CreateAsync(new { title = "Senior Developer", location = "Leeds", employment_type = "permanent", description = "..." });
job = await client.Jobs.UpdateAsync((string)job["id"]!, new { status = "published" });
await client.Jobs.DeleteAsync((string)job["id"]!);
```

`CreateAsync` and `UpdateAsync` accept any object that serialises to JSON. The same calls work on `client.Candidates`, `client.Companies`, `client.Bookings` and `client.Leads`.

```csharp
var customer = await client.Leads.ConvertAsync("LEAD_ID");

var types = await client.ContentTypes.ListAsync();
var posts = await client.Content("blog-posts").ListAsync(new ListOptions().Where("status", "published"));
```

## Every page

```csharp
await foreach (var candidate in client.Candidates.IterateAsync(new ListOptions().Where("status", "screening")))
{
    Console.WriteLine((string?)candidate["email"]);
}
```

## Errors

```csharp
try
{
    await client.Jobs.CreateAsync(new { location = "Leeds" });
}
catch (ValidationException e)
{
    foreach (var (field, messages) in e.Errors) Console.WriteLine($"{field}: {string.Join(", ", messages)}");
}
catch (RateLimitException e)
{
    Console.WriteLine($"Wait {e.RetryAfter} seconds");
}
catch (WizardApplicationException e)
{
    Console.WriteLine($"{e.Status} {e.Code} {e.Message}");
}
```

Exceptions: `ApiConnectionException`, `BadRequestException` (400), `AuthenticationException` (401), `PermissionDeniedException` (403), `NotFoundException` (404), `ValidationException` (422), `RateLimitException` (429), `ServerException` (5xx). All extend `WizardApplicationException`.

## Options

```csharp
new WizardApplicationClient(new WizardApplicationOptions
{
    ApiKey = "wa_...",
    Workspace = "acme",                    // or BaseUrl = "https://acme.wizardapp.io/api/v1"
    Timeout = TimeSpan.FromSeconds(30),
    MaxRetries = 2,                        // 0 turns retries off
});
```

Every call takes an optional `CancellationToken`. `client.LastRateLimit` holds the limits from the latest response.

See the [main README](../README.md) for filters, retries and security.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
