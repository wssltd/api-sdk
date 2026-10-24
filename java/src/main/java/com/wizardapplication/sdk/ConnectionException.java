// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

/** No response: network failure, DNS, timeout or a refused redirect. */
public class ConnectionException extends WizardApplicationException {
    private static final long serialVersionUID = 1L;

    ConnectionException(String message, Throwable cause) {
        super(message, 0, null, null, null, null, cause);
    }
}
