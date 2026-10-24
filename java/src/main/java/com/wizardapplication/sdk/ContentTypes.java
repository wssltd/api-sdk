// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.Map;

/** Content types (read only). */
public final class ContentTypes {
    private final WizardApplication client;

    ContentTypes(WizardApplication client) {
        this.client = client;
    }

    /** The first page of content types. */
    public Page list() {
        return list(null);
    }

    /** One page of content types. */
    public Page list(Map<String, ?> params) {
        return new Page(client.request("GET", "/content-types", params, null));
    }

    /** One content type, with its fields. */
    public Map<String, Object> get(String id) {
        return Resource.unwrap(client.request("GET", "/content-types/" + Resource.segment(id), null, null));
    }
}
