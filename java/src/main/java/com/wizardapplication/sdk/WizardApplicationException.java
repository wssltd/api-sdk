// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Base class for every error the SDK throws. Unchecked. */
public class WizardApplicationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;
    private final transient Map<String, List<String>> errors;
    private final Integer retryAfter;
    private final transient Object body;

    public WizardApplicationException(String message, int status, String code, Map<String, List<String>> errors,
                                      Integer retryAfter, Object body, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? Collections.emptyMap() : Collections.unmodifiableMap(errors);
        this.retryAfter = retryAfter;
        this.body = body;
    }

    /** HTTP status, or 0 when no response arrived. */
    public int getStatus() { return status; }

    /** The API error code, for example "validation_error". */
    public String getCode() { return code; }

    /** Field errors for a 422, e.g. title: ["Title is required"]. */
    public Map<String, List<String>> getErrors() { return errors; }

    /** Seconds to wait after a 429, when the API says; otherwise null. */
    public Integer getRetryAfter() { return retryAfter; }

    /** The decoded response body. */
    public Object getBody() { return body; }

    @Override
    public String toString() {
        return status == 0 ? getMessage() : "[" + status + " " + code + "] " + getMessage();
    }

    static WizardApplicationException forStatus(int status, Object body, Integer retryAfter) {
        String code = null;
        String message = "HTTP " + status;
        Map<String, List<String>> errors = new LinkedHashMap<>();
        if (body instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) body;
            if (m.get("error") instanceof String) code = (String) m.get("error");
            if (m.get("message") instanceof String && !((String) m.get("message")).isEmpty()) message = (String) m.get("message");
            if (m.get("errors") instanceof Map) {
                for (Map.Entry<?, ?> e : ((Map<?, ?>) m.get("errors")).entrySet()) {
                    List<String> list = new ArrayList<>();
                    if (e.getValue() instanceof List) {
                        for (Object o : (List<?>) e.getValue()) if (o instanceof String) list.add((String) o);
                    }
                    errors.put(String.valueOf(e.getKey()), Collections.unmodifiableList(list));
                }
            }
            if (retryAfter == null && m.get("retry_after") instanceof Long) retryAfter = ((Long) m.get("retry_after")).intValue();
        }
        switch (status) {
            case 400: return new BadRequestException(message, status, code, errors, retryAfter, body);
            case 401: return new AuthenticationException(message, status, code, errors, retryAfter, body);
            case 403: return new PermissionDeniedException(message, status, code, errors, retryAfter, body);
            case 404: return new NotFoundException(message, status, code, errors, retryAfter, body);
            case 422: return new ValidationException(message, status, code, errors, retryAfter, body);
            case 429: return new RateLimitException(message, status, code, errors, retryAfter, body);
            default:
                if (status >= 500) return new ServerException(message, status, code, errors, retryAfter, body);
                return new WizardApplicationException(message, status, code, errors, retryAfter, body, null);
        }
    }
}
