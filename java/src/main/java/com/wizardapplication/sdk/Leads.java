// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.Map;

/** Leads, with conversion to a customer. */
public final class Leads extends Resource {
    Leads(WizardApplication client, String path) {
        super(client, path);
    }

    /** Converts a lead into a customer and returns the updated record. */
    public Map<String, Object> convert(String id) {
        return unwrap(client.request("POST", path + "/" + segment(id) + "/convert", null, null));
    }
}
