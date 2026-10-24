// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.List;
import java.util.Map;

/** 403: the key lacks a permission, or the plan has no API access (code "plan_upgrade_required"). */
public class PermissionDeniedException extends WizardApplicationException {
    private static final long serialVersionUID = 1L;

    PermissionDeniedException(String message, int status, String code, Map<String, List<String>> errors, Integer retryAfter, Object body) {
        super(message, status, code, errors, retryAfter, body, null);
    }
}
