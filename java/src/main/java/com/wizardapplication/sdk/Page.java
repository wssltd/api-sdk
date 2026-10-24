// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** One page of a list response. */
public final class Page implements Iterable<Map<String, Object>> {
    private final List<Map<String, Object>> data;
    private final int page;
    private final int perPage;
    private final int total;
    private final int totalPages;

    @SuppressWarnings("unchecked")
    Page(Object payload) {
        Map<?, ?> p = payload instanceof Map ? (Map<?, ?>) payload : Map.of();
        List<Map<String, Object>> items = new ArrayList<>();
        if (p.get("data") instanceof List) {
            for (Object o : (List<?>) p.get("data")) {
                if (o instanceof Map) items.add((Map<String, Object>) o);
            }
        }
        this.data = Collections.unmodifiableList(items);
        Map<?, ?> pg = p.get("pagination") instanceof Map ? (Map<?, ?>) p.get("pagination") : Map.of();
        this.page = num(pg.get("page"), 1);
        this.perPage = num(pg.get("per_page"), items.size());
        this.total = num(pg.get("total"), items.size());
        this.totalPages = num(pg.get("total_pages"), 1);
    }

    private static int num(Object o, int fallback) {
        return o instanceof Number ? ((Number) o).intValue() : fallback;
    }

    public List<Map<String, Object>> getData() { return data; }
    public int getPage() { return page; }
    public int getPerPage() { return perPage; }
    public int getTotal() { return total; }
    public int getTotalPages() { return totalPages; }

    /** True when another page follows. */
    public boolean hasNext() { return page < totalPages; }

    @Override
    public Iterator<Map<String, Object>> iterator() { return data.iterator(); }
}
