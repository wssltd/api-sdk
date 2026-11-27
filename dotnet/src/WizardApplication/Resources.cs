// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

using System;
using System.Collections.Generic;
using System.Net.Http;
using System.Runtime.CompilerServices;
using System.Text.Json.Nodes;
using System.Threading;
using System.Threading.Tasks;

namespace WizardApplication
{
    /// <summary>Where a page sits in the full result.</summary>
    public sealed record Pagination(int Page, int PerPage, int Total, int TotalPages);

    /// <summary>One page of a list response.</summary>
    public sealed class Page
    {
        /// <summary>The records on this page.</summary>
        public IReadOnlyList<JsonObject> Data { get; }

        /// <summary>Page numbers and totals.</summary>
        public Pagination Pagination { get; }

        /// <summary>True when another page follows.</summary>
        public bool HasNext => Pagination.Page < Pagination.TotalPages;

        internal Page(JsonNode? node)
        {
            var data = new List<JsonObject>();
            if (node?["data"] is JsonArray arr)
            {
                foreach (var item in arr)
                {
                    if (item is JsonObject obj) data.Add(obj);
                }
            }
            Data = data;
            var p = node?["pagination"] as JsonObject;
            int Num(string k, int fallback) => p?[k] is JsonValue v && v.TryGetValue<int>(out var n) ? n : fallback;
            Pagination = new Pagination(Num("page", 1), Num("per_page", data.Count), Num("total", data.Count), Num("total_pages", 1));
        }
    }

    /// <summary>Paging, sorting and filters for a list call.</summary>
    public sealed class ListOptions
    {
        /// <summary>Page number, from 1.</summary>
        public int? Page { get; set; }
        /// <summary>Records per page, up to 100.</summary>
        public int? PerPage { get; set; }
        /// <summary>Field to sort by.</summary>
        public string? Sort { get; set; }
        /// <summary>"asc" or "desc".</summary>
        public string? Order { get; set; }
        /// <summary>Filters, e.g. status = "published".</summary>
        public Dictionary<string, object?> Filters { get; } = new();

        /// <summary>Adds a filter and returns these options.</summary>
        public ListOptions Where(string name, object? value)
        {
            Filters[name] = value;
            return this;
        }

        internal Dictionary<string, object?> ToQuery()
        {
            var q = new Dictionary<string, object?>(Filters)
            {
                ["page"] = Page,
                ["per_page"] = PerPage,
                ["sort"] = Sort,
                ["order"] = Order,
            };
            return q;
        }
    }

    /// <summary>Standard list, get, create, update and delete for one resource.</summary>
    public class Resource
    {
        internal readonly WizardApplicationClient Client;
        internal readonly string Path;

        internal Resource(WizardApplicationClient client, string path)
        {
            Client = client;
            Path = "/" + path;
        }

        internal static string Segment(string? id)
        {
            if (string.IsNullOrEmpty(id)) throw new ArgumentException("An ID is required.");
            return Uri.EscapeDataString(id);
        }

        internal static JsonObject Unwrap(JsonNode? node) =>
            node?["data"] as JsonObject ?? node as JsonObject ?? new JsonObject();

        /// <summary>One page of records.</summary>
        public async Task<Page> ListAsync(ListOptions? options = null, CancellationToken cancellationToken = default) =>
            new(await Client.RequestAsync(HttpMethod.Get, Path, (options ?? new ListOptions()).ToQuery(), null, cancellationToken).ConfigureAwait(false));

        /// <summary>Every record across all pages, fetched a page at a time.</summary>
        public async IAsyncEnumerable<JsonObject> IterateAsync(ListOptions? options = null,
            [EnumeratorCancellation] CancellationToken cancellationToken = default)
        {
            var o = new ListOptions { PerPage = options?.PerPage ?? 100, Sort = options?.Sort, Order = options?.Order };
            if (options != null)
            {
                foreach (var f in options.Filters) o.Filters[f.Key] = f.Value;
            }
            for (var n = 1; ; n++)
            {
                o.Page = n;
                var page = await ListAsync(o, cancellationToken).ConfigureAwait(false);
                foreach (var item in page.Data) yield return item;
                if (!page.HasNext || page.Data.Count == 0) yield break;
            }
        }

        /// <summary>One record by ID.</summary>
        public async Task<JsonObject> GetAsync(string id, CancellationToken cancellationToken = default) =>
            Unwrap(await Client.RequestAsync(HttpMethod.Get, $"{Path}/{Segment(id)}", null, null, cancellationToken).ConfigureAwait(false));

        /// <summary>Creates a record (any object that serialises to JSON) and returns it.</summary>
        public async Task<JsonObject> CreateAsync(object data, CancellationToken cancellationToken = default) =>
            Unwrap(await Client.RequestAsync(HttpMethod.Post, Path, null, data ?? new JsonObject(), cancellationToken).ConfigureAwait(false));

        /// <summary>Updates the given fields of a record and returns it.</summary>
        public async Task<JsonObject> UpdateAsync(string id, object data, CancellationToken cancellationToken = default) =>
            Unwrap(await Client.RequestAsync(HttpMethod.Put, $"{Path}/{Segment(id)}", null, data ?? new JsonObject(), cancellationToken).ConfigureAwait(false));

        /// <summary>Deletes a record.</summary>
        public async Task DeleteAsync(string id, CancellationToken cancellationToken = default) =>
            await Client.RequestAsync(HttpMethod.Delete, $"{Path}/{Segment(id)}", null, null, cancellationToken).ConfigureAwait(false);
    }

    /// <summary>Leads, with conversion to a customer.</summary>
    public sealed class LeadsResource : Resource
    {
        internal LeadsResource(WizardApplicationClient client, string path) : base(client, path) { }

        /// <summary>Converts a lead into a customer and returns the updated record.</summary>
        public async Task<JsonObject> ConvertAsync(string id, CancellationToken cancellationToken = default) =>
            Unwrap(await Client.RequestAsync(HttpMethod.Post, $"{Path}/{Segment(id)}/convert", null, null, cancellationToken).ConfigureAwait(false));
    }

    /// <summary>
    /// Team members: list, iterate, get, create and update. The API has no
    /// delete. Creating a user adds a paid seat, so the key must have been
    /// created with the per-seat charge accepted.
    /// </summary>
    public sealed class UsersResource : Resource
    {
        internal UsersResource(WizardApplicationClient client, string path) : base(client, path) { }

        /// <summary>Users can't be deleted through the API.</summary>
        public new Task DeleteAsync(string id, CancellationToken cancellationToken = default) =>
            throw new NotSupportedException("Users can't be deleted through the API.");

        /// <summary>
        /// Creates up to 100 users in one call. Returns <c>data</c> (one entry per row,
        /// status created, skipped or failed) and <c>summary</c> (the counts). A row's
        /// manager_email may name a user created earlier in the same call.
        /// </summary>
        public async Task<JsonObject> ImportAsync(IEnumerable<object> users, CancellationToken cancellationToken = default) =>
            await Client.RequestAsync(HttpMethod.Post, Path + "/import", null,
                new Dictionary<string, object?> { ["users"] = users ?? Array.Empty<object>() }, cancellationToken).ConfigureAwait(false) as JsonObject
            ?? new JsonObject();

        /// <summary>Sends, or sends again, the invitation email to a user who hasn't signed in yet.</summary>
        public async Task<JsonObject> SendInvitationAsync(string id, CancellationToken cancellationToken = default) =>
            Unwrap(await Client.RequestAsync(HttpMethod.Post, $"{Path}/{Segment(id)}/invitation", null, null, cancellationToken).ConfigureAwait(false));
    }

    /// <summary>The workspace's AI employees, through its MCP server. The key needs ai_employees.write.</summary>
    public sealed class AIEmployeesResource
    {
        // A job can take a few minutes; the server stops it at three.
        private static readonly TimeSpan AskTimeout = TimeSpan.FromSeconds(200);
        private readonly WizardApplicationClient _client;

        internal AIEmployeesResource(WizardApplicationClient client) => _client = client;

        /// <summary>Every AI employee that is switched on: id, name, purpose and paused.</summary>
        public async Task<IReadOnlyList<JsonObject>> ListAsync(CancellationToken cancellationToken = default)
        {
            var result = await _client.CallToolAsync("list_ai_employees", null, cancellationToken).ConfigureAwait(false);
            var list = new List<JsonObject>();
            if (result?["ai_employees"] is JsonArray items)
                foreach (var item in items)
                    if (item is JsonObject o) list.Add(o);
            return list;
        }

        /// <summary>
        /// Gives an AI employee (by id or name) a job in plain words and waits for it,
        /// which can take a few minutes. Returns summary, changes_made and
        /// awaiting_approval. Changes wait for one of the people it reports to unless
        /// the workspace trusts it with that kind of change. The same job asked twice
        /// on the same day doesn't queue its changes twice.
        /// </summary>
        public async Task<JsonObject> AskAsync(string aiEmployee, string instruction, string? context = null,
            CancellationToken cancellationToken = default)
        {
            if (string.IsNullOrEmpty(aiEmployee) || string.IsNullOrEmpty(instruction))
                throw new ArgumentException("aiEmployee and instruction are required.");
            var args = new Dictionary<string, object?> { ["ai_employee"] = aiEmployee, ["instruction"] = instruction };
            if (!string.IsNullOrEmpty(context)) args["context"] = context;
            var result = await _client.CallToolAsync("ask_ai_employee", args, AskTimeout, cancellationToken).ConfigureAwait(false);
            return result as JsonObject ?? new JsonObject();
        }
    }

    /// <summary>Content types (read only).</summary>
    public sealed class ContentTypesResource
    {
        private readonly WizardApplicationClient _client;

        internal ContentTypesResource(WizardApplicationClient client) => _client = client;

        /// <summary>One page of content types.</summary>
        public async Task<Page> ListAsync(ListOptions? options = null, CancellationToken cancellationToken = default) =>
            new(await _client.RequestAsync(HttpMethod.Get, "/content-types", (options ?? new ListOptions()).ToQuery(), null, cancellationToken).ConfigureAwait(false));

        /// <summary>One content type, with its fields.</summary>
        public async Task<JsonObject> GetAsync(string id, CancellationToken cancellationToken = default) =>
            Resource.Unwrap(await _client.RequestAsync(HttpMethod.Get, "/content-types/" + Resource.Segment(id), null, null, cancellationToken).ConfigureAwait(false));
    }
}
