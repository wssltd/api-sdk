// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.List;
import java.util.Map;

/** 401: the API key is missing, invalid or expired. */
public class AuthenticationException extends WizardApplicationException {
    private static final long serialVersionUID = 1L;

    AuthenticationException(String message, int status, String code, Map<String, List<String>> errors, Integer retryAfter, Object body) {
        super(message, status, code, errors, retryAfter, body, null);
    }
}
