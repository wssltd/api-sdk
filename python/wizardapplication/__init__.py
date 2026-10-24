# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

"""Official Python SDK for the Wizard Application API."""

from .client import VERSION, RateLimit, WizardApplication
from .errors import (
    AuthenticationError,
    BadRequestError,
    ConnectionError,
    NotFoundError,
    PermissionDeniedError,
    RateLimitError,
    ServerError,
    ValidationError,
    WizardApplicationError,
)
from .resources import Page

__version__ = VERSION

__all__ = [
    "WizardApplication",
    "RateLimit",
    "Page",
    "WizardApplicationError",
    "ConnectionError",
    "BadRequestError",
    "AuthenticationError",
    "PermissionDeniedError",
    "NotFoundError",
    "ValidationError",
    "RateLimitError",
    "ServerError",
    "__version__",
]
