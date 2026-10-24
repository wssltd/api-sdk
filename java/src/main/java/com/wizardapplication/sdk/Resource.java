// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/** Standard list, get, create, update and delete for one resource. */
public class Resource {
    protected final WizardApplication client;
    protected final String path;

    Resource(WizardApplication client, String path) {
        this.client = client;
        this.path = "/" + path;
    }

    /** Escapes one path segment (an ID or slug). */
    static String segment(String id) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("An ID is required.");
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> unwrap(Object payload) {
        if (payload instanceof Map) {
            Object data = ((Map<?, ?>) payload).get("data");
            if (data instanceof Map) return (Map<String, Object>) data;
            return (Map<String, Object>) payload;
        }
        return new LinkedHashMap<>();
    }

    /** The first page of records. */
    public Page list() {
        return list(null);
    }

    /**
     * One page of records. Params hold filters (e.g. "status"), "page",
     * "per_page" (up to 100), "sort" and "order".
     */
    public Page list(Map<String, ?> params) {
        return new Page(client.request("GET", path, params, null));
    }

    /** Every record across all pages, fetched lazily a page at a time. */
    public Iterable<Map<String, Object>> iterate(Map<String, ?> params) {
        Map<String, Object> base = new HashMap<>();
        base.put("per_page", 100);
        if (params != null) base.putAll(params);
        return () -> new Iterator<>() {
            private int pageNumber = 1;
            private Page current;
            private Iterator<Map<String, Object>> items;
            private boolean done;

            private void load() {
                Map<String, Object> q = new HashMap<>(base);
                q.put("page", pageNumber++);
                current = list(q);
                items = current.iterator();
            }

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (items == null) load();
                while (!items.hasNext()) {
                    if (!current.hasNext() || current.getData().isEmpty()) {
                        done = true;
                        return false;
                    }
                    load();
                }
                return true;
            }

            @Override
            public Map<String, Object> next() {
                if (!hasNext()) throw new NoSuchElementException();
                return items.next();
            }
        };
    }

    /** One record by ID. */
    public Map<String, Object> get(String id) {
        return unwrap(client.request("GET", path + "/" + segment(id), null, null));
    }

    /** Creates a record and returns it. */
    public Map<String, Object> create(Map<String, ?> data) {
        return unwrap(client.request("POST", path, null, data == null ? Map.of() : data));
    }

    /** Updates the given fields of a record and returns it. */
    public Map<String, Object> update(String id, Map<String, ?> data) {
        return unwrap(client.request("PUT", path + "/" + segment(id), null, data == null ? Map.of() : data));
    }

    /** Deletes a record. */
    public void delete(String id) {
        client.request("DELETE", path + "/" + segment(id), null, null);
    }
}
