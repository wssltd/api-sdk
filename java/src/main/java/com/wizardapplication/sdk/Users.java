// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.List;
import java.util.Map;

/**
 * Team members: list, iterate, get, create and update. The API has no delete.
 * Creating a user adds a paid seat, so the key must have been created with
 * the per-seat charge accepted.
 */
public final class Users extends Resource {
    Users(WizardApplication client, String path) {
        super(client, path);
    }

    /** Users can't be deleted through the API. */
    @Override
    public void delete(String id) {
        throw new UnsupportedOperationException("Users can't be deleted through the API.");
    }

    /**
     * Creates up to 100 users in one call. Returns "data" (one entry per row,
     * status created, skipped or failed) and "summary" (the counts). A row's
     * manager_email may name a user created earlier in the same call.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> importUsers(List<? extends Map<String, ?>> users) {
        Object res = client.request("POST", path + "/import", null, Map.of("users", users == null ? List.of() : users));
        return res instanceof Map ? (Map<String, Object>) res : Map.of();
    }

    /** Sends, or sends again, the invitation email to a user who hasn't signed in yet. */
    public Map<String, Object> sendInvitation(String id) {
        return unwrap(client.request("POST", path + "/" + segment(id) + "/invitation", null, null));
    }
}
