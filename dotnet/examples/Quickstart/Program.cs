// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

// Lists published jobs and the newest leads.
//
//   set WIZARDAPP_API_KEY=wa_...
//   set WIZARDAPP_WORKSPACE=acme
//   dotnet run --project examples/Quickstart

using System;
using WizardApplication;

using var client = new WizardApplicationClient(new WizardApplicationOptions());

try
{
    var jobs = await client.Jobs.ListAsync(new ListOptions { PerPage = 10 }.Where("status", "published"));
    Console.WriteLine($"{jobs.Pagination.Total} published jobs");
    foreach (var job in jobs.Data)
    {
        Console.WriteLine($"- {job["title"]} ({job["location"]})");
    }

    var leads = await client.Leads.ListAsync(new ListOptions { Sort = "created_at", Order = "desc", PerPage = 5 });
    Console.WriteLine("Newest leads:");
    foreach (var lead in leads.Data)
    {
        Console.WriteLine($"- {lead["first_name"]} {lead["last_name"]} <{lead["email"]}>");
    }
}
catch (WizardApplicationException e)
{
    Console.WriteLine($"Request failed: {e}");
}

if (client.LastRateLimit is { } rl)
{
    Console.WriteLine($"{rl.Remaining} of {rl.Limit} requests left this minute");
}
