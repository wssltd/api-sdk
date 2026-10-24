// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.List;
import java.util.Map;

/** 422: validation failed; see getErrors(). */
public class ValidationException extends WizardApplicationException {
    private static final long serialVersionUID = 1L;

    ValidationException(String message, int status, String code, Map<String, List<String>> errors, Integer retryAfter, Object body) {
        super(message, status, code, errors, retryAfter, body, null);
    }
}
