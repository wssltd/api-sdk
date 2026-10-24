// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package wizardapplication

import "fmt"

// Error is returned for every API failure. Use errors.As to read it, and the
// Is* helpers to check the kind.
type Error struct {
	// Status is the HTTP status, or 0 when no response arrived.
	Status int
	// Code is the API error code, for example "validation_error".
	Code string
	// Message is the API's description of the problem.
	Message string
	// Errors holds field errors for a 422, e.g. {"title": ["Title is required"]}.
	Errors map[string][]string
	// RetryAfter is the number of seconds to wait after a 429, or -1.
	RetryAfter int
	// Body is the decoded response body.
	Body interface{}
}

func (e *Error) Error() string {
	if e.Status == 0 {
		return e.Message
	}
	return fmt.Sprintf("[%d %s] %s", e.Status, e.Code, e.Message)
}

// IsConnection reports a network failure or timeout (no response).
func (e *Error) IsConnection() bool { return e.Status == 0 }

// IsAuthentication reports a 401: the API key is missing, invalid or expired.
func (e *Error) IsAuthentication() bool { return e.Status == 401 }

// IsPermissionDenied reports a 403: the key lacks a permission, or the plan has
// no API access (Code "plan_upgrade_required").
func (e *Error) IsPermissionDenied() bool { return e.Status == 403 }

// IsNotFound reports a 404.
func (e *Error) IsNotFound() bool { return e.Status == 404 }

// IsValidation reports a 422; see Errors for each field.
func (e *Error) IsValidation() bool { return e.Status == 422 }

// IsRateLimit reports a 429; see RetryAfter.
func (e *Error) IsRateLimit() bool { return e.Status == 429 }

// IsServer reports a 5xx.
func (e *Error) IsServer() bool { return e.Status >= 500 }

func errorForStatus(status int, body interface{}, retryAfter int) *Error {
	e := &Error{Status: status, Message: fmt.Sprintf("HTTP %d", status), RetryAfter: retryAfter, Body: body, Errors: map[string][]string{}}
	m, ok := body.(map[string]interface{})
	if !ok {
		return e
	}
	if s, ok := m["error"].(string); ok {
		e.Code = s
	}
	if s, ok := m["message"].(string); ok && s != "" {
		e.Message = s
	}
	if fields, ok := m["errors"].(map[string]interface{}); ok {
		for field, v := range fields {
			list, _ := v.([]interface{})
			for _, msg := range list {
				if s, ok := msg.(string); ok {
					e.Errors[field] = append(e.Errors[field], s)
				}
			}
		}
	}
	if e.RetryAfter < 0 {
		if n, ok := m["retry_after"].(float64); ok {
			e.RetryAfter = int(n)
		}
	}
	return e
}
