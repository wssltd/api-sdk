# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

"""Errors raised by the Wizard Application SDK.

Every API failure raises a subclass of WizardApplicationError carrying the
HTTP status, the API's error code (for example "validation_error"), the
message and, for validation failures, the per-field errors.
"""

from typing import Any, Dict, List, Optional


class WizardApplicationError(Exception):
    """Base class for every error the SDK raises."""

    def __init__(
        self,
        message: str,
        status: int = 0,
        code: Optional[str] = None,
        errors: Optional[Dict[str, List[str]]] = None,
        retry_after: Optional[int] = None,
        body: Any = None,
    ) -> None:
        super().__init__(message)
        self.message = message
        self.status = status
        self.code = code
        self.errors = errors or {}
        self.retry_after = retry_after
        self.body = body

    def __str__(self) -> str:
        prefix = f"[{self.status} {self.code}] " if self.status else ""
        return prefix + self.message


class ConnectionError(WizardApplicationError):  # noqa: A001 - mirrors the other SDKs
    """The request never got a response: network failure, DNS or timeout."""


class BadRequestError(WizardApplicationError):
    """400: the request was malformed."""


class AuthenticationError(WizardApplicationError):
    """401: the API key is missing, invalid or expired."""


class PermissionDeniedError(WizardApplicationError):
    """403: the key lacks a permission, or the plan has no API access
    (code "plan_upgrade_required")."""


class NotFoundError(WizardApplicationError):
    """404: the record or route does not exist."""


class ValidationError(WizardApplicationError):
    """422: the data failed validation. See .errors for each field."""


class RateLimitError(WizardApplicationError):
    """429: the per-minute or monthly limit was reached. .retry_after says how
    many seconds to wait (per-minute limit only)."""


class ServerError(WizardApplicationError):
    """5xx: something went wrong on the server."""


_BY_STATUS = {
    400: BadRequestError,
    401: AuthenticationError,
    403: PermissionDeniedError,
    404: NotFoundError,
    422: ValidationError,
    429: RateLimitError,
}


def error_for_status(status: int, body: Any, retry_after: Optional[int]) -> WizardApplicationError:
    """Builds the right error for an HTTP status and decoded response body."""
    code = None
    message = f"HTTP {status}"
    errors = None
    if isinstance(body, dict):
        code = body.get("error") or None
        message = body.get("message") or message
        if isinstance(body.get("errors"), dict):
            errors = body["errors"]
        if retry_after is None and isinstance(body.get("retry_after"), int):
            retry_after = body["retry_after"]
    cls = _BY_STATUS.get(status, ServerError if status >= 500 else WizardApplicationError)
    return cls(message, status=status, code=code, errors=errors, retry_after=retry_after, body=body)
