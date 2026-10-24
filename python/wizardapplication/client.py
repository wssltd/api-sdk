# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

"""HTTP client for the Wizard Application API."""

import json
import os
import re
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Mapping, Optional

from . import errors as _errors
from .resources import ContentItems, ContentTypes, Leads, Resource

VERSION = "1.0.0"

_WORKSPACE_RE = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
_LOCAL_HOSTS = {"localhost", "127.0.0.1", "::1"}
_RETRY_STATUSES = {502, 503, 504}
_IDEMPOTENT = {"GET", "PUT", "DELETE"}
_MAX_RETRY_WAIT = 60


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Refuses redirects, so the API key is never forwarded to another host."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):  # noqa: D401
        return None


_OPENER = urllib.request.build_opener(_NoRedirect)


class RateLimit:
    """Rate limit headers from the most recent response."""

    def __init__(self, limit: Optional[int], remaining: Optional[int], reset: Optional[int]) -> None:
        self.limit = limit
        self.remaining = remaining
        self.reset = reset  # seconds until the per-minute window resets

    def __repr__(self) -> str:
        return f"RateLimit(limit={self.limit}, remaining={self.remaining}, reset={self.reset})"


class WizardApplication:
    """Client for the Wizard Application API.

    >>> client = WizardApplication(api_key="wa_...", workspace="acme")
    >>> page = client.jobs.list(status="published")

    The API key defaults to the WIZARDAPP_API_KEY environment variable and the
    workspace to WIZARDAPP_WORKSPACE. Use base_url instead of workspace to
    point at another host (it must be https).
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        workspace: Optional[str] = None,
        base_url: Optional[str] = None,
        timeout: float = 30.0,
        max_retries: int = 2,
        allow_http: bool = False,
    ) -> None:
        api_key = api_key or os.environ.get("WIZARDAPP_API_KEY")
        if not api_key:
            raise ValueError("An API key is required (pass api_key or set WIZARDAPP_API_KEY).")
        if any(ch.isspace() for ch in api_key):
            raise ValueError("The API key must not contain whitespace.")
        self._api_key = api_key
        self.base_url = _resolve_base_url(workspace or os.environ.get("WIZARDAPP_WORKSPACE"), base_url, allow_http)
        self.timeout = timeout
        self.max_retries = max(0, int(max_retries))
        self.last_rate_limit: Optional[RateLimit] = None

        self.jobs = Resource(self, "jobs")
        self.candidates = Resource(self, "candidates")
        self.companies = Resource(self, "companies")
        self.bookings = Resource(self, "bookings")
        self.leads = Leads(self, "leads")
        self.content_types = ContentTypes(self, "content-types")

    def content(self, content_type: str) -> ContentItems:
        """Items of one content type, by its slug or ID (for example "blog-posts")."""
        return ContentItems(self, content_type)

    def __repr__(self) -> str:  # never show the key
        return f"WizardApplication(base_url={self.base_url!r})"

    def request(
        self,
        method: str,
        path: str,
        params: Optional[Mapping[str, Any]] = None,
        body: Optional[Mapping[str, Any]] = None,
    ) -> Any:
        """Sends a request and returns the decoded JSON body.

        Retries a 429 after Retry-After, and network errors and 502/503/504 on
        GET, PUT and DELETE, up to max_retries times.
        """
        method = method.upper()
        url = self.base_url + path
        query = _encode_params(params)
        if query:
            url += "?" + query
        data = json.dumps(body).encode("utf-8") if body is not None else None
        headers = {
            "Authorization": f"Bearer {self._api_key}",
            "Accept": "application/json",
            "User-Agent": f"wizardapplication-python/{VERSION}",
        }
        if data is not None:
            headers["Content-Type"] = "application/json"

        attempt = 0
        while True:
            req = urllib.request.Request(url, data=data, headers=headers, method=method)
            try:
                with _OPENER.open(req, timeout=self.timeout) as resp:
                    self._remember_rate_limit(resp.headers)
                    return _decode(resp.read())
            except urllib.error.HTTPError as e:
                self._remember_rate_limit(e.headers)
                payload = _decode(e.read())
                retry_after = _int_header(e.headers, "Retry-After")
                err = _errors.error_for_status(e.code, payload, retry_after)
                if attempt < self.max_retries and self._should_retry(method, e.code, err):
                    attempt += 1
                    time.sleep(self._wait(attempt, err.retry_after))
                    continue
                raise err from None
            except (urllib.error.URLError, socket.timeout, ConnectionResetError) as e:
                if attempt < self.max_retries and method in _IDEMPOTENT:
                    attempt += 1
                    time.sleep(self._wait(attempt, None))
                    continue
                reason = getattr(e, "reason", e)
                raise _errors.ConnectionError(f"Could not reach {self.base_url}: {reason}") from None

    @staticmethod
    def _should_retry(method: str, status: int, err: _errors.WizardApplicationError) -> bool:
        if status == 429:
            # The monthly limit won't clear by waiting.
            return err.code != "monthly_limit_exceeded"
        return status in _RETRY_STATUSES and method in _IDEMPOTENT

    @staticmethod
    def _wait(attempt: int, retry_after: Optional[int]) -> float:
        if retry_after is not None and retry_after >= 0:
            return float(min(retry_after, _MAX_RETRY_WAIT))
        return min(0.5 * (2 ** (attempt - 1)), 8.0)

    def _remember_rate_limit(self, headers: Any) -> None:
        if headers is None or headers.get("X-RateLimit-Limit") is None:
            return
        self.last_rate_limit = RateLimit(
            _int_header(headers, "X-RateLimit-Limit"),
            _int_header(headers, "X-RateLimit-Remaining"),
            _int_header(headers, "X-RateLimit-Reset"),
        )


def _resolve_base_url(workspace: Optional[str], base_url: Optional[str], allow_http: bool) -> str:
    if base_url:
        parsed = urllib.parse.urlparse(base_url)
        if parsed.scheme not in ("https", "http") or not parsed.hostname:
            raise ValueError("base_url must be a full URL, for example https://acme.wizardapp.io/api/v1")
        if parsed.scheme == "http" and not (allow_http or parsed.hostname in _LOCAL_HOSTS):
            raise ValueError("base_url must use https (your API key would be sent in clear text).")
        if parsed.username or parsed.password:
            raise ValueError("base_url must not contain credentials.")
        url = base_url.rstrip("/")
        if not url.endswith("/api/v1"):
            url += "/api/v1"
        return url
    if not workspace:
        raise ValueError("Pass workspace (your subdomain, e.g. 'acme') or base_url, or set WIZARDAPP_WORKSPACE.")
    workspace = workspace.strip().lower()
    if not _WORKSPACE_RE.match(workspace):
        raise ValueError("workspace must be your workspace subdomain, for example 'acme' for acme.wizardapp.io.")
    return f"https://{workspace}.wizardapp.io/api/v1"


def _encode_params(params: Optional[Mapping[str, Any]]) -> str:
    if not params:
        return ""
    pairs = []
    for key, value in params.items():
        if value is None:
            continue
        if isinstance(value, bool):
            value = "true" if value else "false"
        pairs.append((key, str(value)))
    return urllib.parse.urlencode(pairs)


def _decode(raw: bytes) -> Any:
    if not raw:
        return None
    try:
        return json.loads(raw.decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        return {"message": raw.decode("utf-8", "replace")[:500]}


def _int_header(headers: Any, name: str) -> Optional[int]:
    if headers is None:
        return None
    value = headers.get(name)
    try:
        return int(value) if value is not None else None
    except ValueError:
        return None


__all__ = ["WizardApplication", "RateLimit", "VERSION"]
