// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package wizardapplication

import (
	"context"
	"errors"
	"net/url"
)

// Pagination describes where a page sits in the full result.
type Pagination struct {
	Page       int `json:"page"`
	PerPage    int `json:"per_page"`
	Total      int `json:"total"`
	TotalPages int `json:"total_pages"`
}

// Page is one page of a list response.
type Page struct {
	Data       []Record   `json:"data"`
	Pagination Pagination `json:"pagination"`
}

// HasNext reports whether another page follows.
func (p *Page) HasNext() bool { return p.Pagination.Page < p.Pagination.TotalPages }

type envelope struct {
	Data Record `json:"data"`
}

var errEmptyID = errors.New("wizardapplication: an ID is required")

// Resource has the standard list, get, create, update and delete calls.
type Resource struct {
	client    *Client
	path      string
	emptyType bool
}

func (r *Resource) item(id string) (string, error) {
	if r.emptyType {
		return "", errors.New("wizardapplication: a content type is required")
	}
	if id == "" {
		return "", errEmptyID
	}
	return r.path + "/" + url.PathEscape(id), nil
}

// List returns one page. Params hold filters (e.g. "status"), "page",
// "per_page" (up to 100), "sort" and "order".
func (r *Resource) List(ctx context.Context, params Params) (*Page, error) {
	if r.emptyType {
		return nil, errors.New("wizardapplication: a content type is required")
	}
	page := &Page{}
	if err := r.client.Do(ctx, "GET", r.path, params, nil, page); err != nil {
		return nil, err
	}
	return page, nil
}

// Iterate calls fn for every record across all pages, a page at a time.
// Return an error from fn to stop early.
func (r *Resource) Iterate(ctx context.Context, params Params, fn func(Record) error) error {
	q := Params{"per_page": 100}
	for k, v := range params {
		q[k] = v
	}
	for n := 1; ; n++ {
		q["page"] = n
		page, err := r.List(ctx, q)
		if err != nil {
			return err
		}
		for _, rec := range page.Data {
			if err := fn(rec); err != nil {
				return err
			}
		}
		if !page.HasNext() || len(page.Data) == 0 {
			return nil
		}
	}
}

// Get returns one record by ID.
func (r *Resource) Get(ctx context.Context, id string) (Record, error) {
	path, err := r.item(id)
	if err != nil {
		return nil, err
	}
	return r.one(ctx, "GET", path, nil)
}

// Create creates a record and returns it.
func (r *Resource) Create(ctx context.Context, data interface{}) (Record, error) {
	if r.emptyType {
		return nil, errors.New("wizardapplication: a content type is required")
	}
	return r.one(ctx, "POST", r.path, nonNil(data))
}

// Update changes the given fields of a record and returns it.
func (r *Resource) Update(ctx context.Context, id string, data interface{}) (Record, error) {
	path, err := r.item(id)
	if err != nil {
		return nil, err
	}
	return r.one(ctx, "PUT", path, nonNil(data))
}

// Delete deletes a record.
func (r *Resource) Delete(ctx context.Context, id string) error {
	path, err := r.item(id)
	if err != nil {
		return err
	}
	return r.client.Do(ctx, "DELETE", path, nil, nil, nil)
}

func (r *Resource) one(ctx context.Context, method, path string, body interface{}) (Record, error) {
	env := &envelope{}
	if err := r.client.Do(ctx, method, path, nil, body, env); err != nil {
		return nil, err
	}
	if env.Data == nil {
		env.Data = Record{}
	}
	return env.Data, nil
}

func nonNil(data interface{}) interface{} {
	if data == nil {
		return Record{}
	}
	return data
}

// Leads adds converting a lead to a customer.
type Leads struct {
	Resource
}

// Convert turns a lead into a customer and returns the updated record.
func (l *Leads) Convert(ctx context.Context, id string) (Record, error) {
	path, err := l.item(id)
	if err != nil {
		return nil, err
	}
	return l.one(ctx, "POST", path+"/convert", nil)
}

// ContentTypes lists the workspace's content types (read only).
type ContentTypes struct {
	client *Client
}

// List returns one page of content types.
func (t *ContentTypes) List(ctx context.Context, params Params) (*Page, error) {
	page := &Page{}
	if err := t.client.Do(ctx, "GET", "/content-types", params, nil, page); err != nil {
		return nil, err
	}
	return page, nil
}

// Get returns one content type with its fields.
func (t *ContentTypes) Get(ctx context.Context, id string) (Record, error) {
	if id == "" {
		return nil, errEmptyID
	}
	r := &Resource{client: t.client, path: "/content-types"}
	return r.one(ctx, "GET", "/content-types/"+url.PathEscape(id), nil)
}
