# Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

"""API resources: jobs, candidates, companies, bookings, leads and content."""

import urllib.parse
from typing import Any, Dict, Iterator, List, Mapping, Optional


def quote_segment(value: Any) -> str:
    """Escapes one path segment (an ID or slug)."""
    text = str(value) if value is not None else ""
    if not text:
        raise ValueError("An ID is required.")
    return urllib.parse.quote(text, safe="")


class Page:
    """One page of a list response."""

    def __init__(self, payload: Any) -> None:
        payload = payload if isinstance(payload, dict) else {}
        self.data: List[Dict[str, Any]] = payload.get("data") or []
        pagination = payload.get("pagination") or {}
        self.page: int = int(pagination.get("page") or 1)
        self.per_page: int = int(pagination.get("per_page") or len(self.data))
        self.total: int = int(pagination.get("total") or len(self.data))
        self.total_pages: int = int(pagination.get("total_pages") or 1)

    @property
    def has_next(self) -> bool:
        return self.page < self.total_pages

    def __iter__(self) -> Iterator[Dict[str, Any]]:
        return iter(self.data)

    def __len__(self) -> int:
        return len(self.data)

    def __repr__(self) -> str:
        return f"Page(page={self.page}, total_pages={self.total_pages}, total={self.total}, items={len(self.data)})"


class Resource:
    """Standard list, get, create, update and delete for one resource."""

    def __init__(self, client: Any, path: str) -> None:
        self._client = client
        self._path = "/" + path

    def list(self, page: Optional[int] = None, per_page: Optional[int] = None,
             sort: Optional[str] = None, order: Optional[str] = None, **filters: Any) -> Page:
        """One page of records. Filters are passed as query parameters, for
        example status="published" or search="developer". per_page is at most 100."""
        params: Dict[str, Any] = dict(filters)
        params.update({"page": page, "per_page": per_page, "sort": sort, "order": order})
        return Page(self._client.request("GET", self._path, params=params))

    def iterate(self, per_page: int = 100, **filters: Any) -> Iterator[Dict[str, Any]]:
        """Every record across all pages, fetched a page at a time."""
        page_number = 1
        while True:
            page = self.list(page=page_number, per_page=per_page, **filters)
            for item in page.data:
                yield item
            if not page.has_next or not page.data:
                return
            page_number += 1

    def get(self, id: Any) -> Dict[str, Any]:
        """One record by ID."""
        return _data(self._client.request("GET", f"{self._path}/{quote_segment(id)}"))

    def create(self, data: Mapping[str, Any]) -> Dict[str, Any]:
        """Creates a record and returns it."""
        return _data(self._client.request("POST", self._path, body=dict(data)))

    def update(self, id: Any, data: Mapping[str, Any]) -> Dict[str, Any]:
        """Updates the given fields of a record and returns it."""
        return _data(self._client.request("PUT", f"{self._path}/{quote_segment(id)}", body=dict(data)))

    def delete(self, id: Any) -> None:
        """Deletes a record."""
        self._client.request("DELETE", f"{self._path}/{quote_segment(id)}")


class Leads(Resource):
    """Leads, with conversion to a customer."""

    def convert(self, id: Any) -> Dict[str, Any]:
        """Converts a lead into a customer and returns the updated record."""
        return _data(self._client.request("POST", f"{self._path}/{quote_segment(id)}/convert"))


class ContentTypes:
    """Content types (read only)."""

    def __init__(self, client: Any, path: str) -> None:
        self._client = client
        self._path = "/" + path

    def list(self, page: Optional[int] = None, per_page: Optional[int] = None, **filters: Any) -> Page:
        params: Dict[str, Any] = dict(filters)
        params.update({"page": page, "per_page": per_page})
        return Page(self._client.request("GET", self._path, params=params))

    def get(self, id: Any) -> Dict[str, Any]:
        """One content type, with its fields."""
        return _data(self._client.request("GET", f"{self._path}/{quote_segment(id)}"))


class ContentItems(Resource):
    """Items of one content type, addressed by the type's slug or ID."""

    def __init__(self, client: Any, content_type: str) -> None:
        super().__init__(client, "content/" + quote_segment(content_type))


def _data(payload: Any) -> Dict[str, Any]:
    if isinstance(payload, dict) and "data" in payload:
        return payload["data"]
    return payload if isinstance(payload, dict) else {}


__all__ = ["Page", "Resource", "Leads", "ContentTypes", "ContentItems", "quote_segment"]
