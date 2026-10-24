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
