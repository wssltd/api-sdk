// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package wizardapplication

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"sync/atomic"
	"time"
)

// Users is the team member list: List, Iterate, Get, Create and Update. The
// API has no delete. Creating a user adds a paid seat, so the key must have
// been created with the per-seat charge accepted.
type Users struct {
	Resource
}

// Delete is not offered for users and always returns an error.
func (u *Users) Delete(ctx context.Context, id string) error {
	return errors.New("wizardapplication: users can't be deleted through the API")
}

// ImportResult is the outcome of Users.Import.
type ImportResult struct {
	// Data has one entry per row, in order: index, email, status (created,
	// skipped or failed), and the user or the error.
	Data    []Record `json:"data"`
	Summary struct {
		Created int `json:"created"`
		Skipped int `json:"skipped"`
		Failed  int `json:"failed"`
	} `json:"summary"`
}

// Import creates up to 100 users in one call. A row's manager_email may name
// a user created earlier in the same call.
func (u *Users) Import(ctx context.Context, users []Record) (*ImportResult, error) {
	if users == nil {
		users = []Record{}
	}
	out := &ImportResult{}
	if err := u.client.Do(ctx, "POST", u.path+"/import", nil, map[string]interface{}{"users": users}, out); err != nil {
		return nil, err
	}
	return out, nil
}

// SendInvitation sends, or sends again, the invitation email to a user who
// hasn't signed in yet, and returns the user.
func (u *Users) SendInvitation(ctx context.Context, id string) (Record, error) {
	path, err := u.item(id)
	if err != nil {
		return nil, err
	}
	return u.one(ctx, "POST", path+"/invitation", nil)
}

// askTimeout covers an AI employee job; the server stops one at three minutes.
const askTimeout = 200 * time.Second

// AIEmployees reaches the workspace's AI employees through its MCP server.
// The key needs the ai_employees.write permission.
type AIEmployees struct {
	client *Client
}

// AIEmployee is one AI employee.
type AIEmployee struct {
	ID      string `json:"id"`
	Name    string `json:"name"`
	Purpose string `json:"purpose,omitempty"`
	Paused  bool   `json:"paused"`
}

// AskResult is what an AI employee reports back.
type AskResult struct {
	AIEmployee string `json:"ai_employee"`
	Summary    string `json:"summary"`
	// ChangesMade counts the changes it made itself.
	ChangesMade int `json:"changes_made"`
	// AwaitingApproval counts the changes waiting in Wizard Application.
	AwaitingApproval int    `json:"awaiting_approval"`
	ApprovalURL      string `json:"approval_url"`
}

// List returns every AI employee that is switched on.
func (a *AIEmployees) List(ctx context.Context) ([]AIEmployee, error) {
	var out struct {
		AIEmployees []AIEmployee `json:"ai_employees"`
	}
	if err := a.client.callTool(ctx, a.client.httpClient, "list_ai_employees", nil, &out); err != nil {
		return nil, err
	}
	if out.AIEmployees == nil {
		out.AIEmployees = []AIEmployee{}
	}
	return out.AIEmployees, nil
}

// Ask gives an AI employee (by id or name) a job in plain words and waits for
// it to finish, which can take a few minutes. Changes wait for one of the
// people it reports to unless the workspace trusts it with that kind of
// change. The same job asked twice on the same day doesn't queue its changes
// twice. details is optional extra information for the job.
func (a *AIEmployees) Ask(ctx context.Context, aiEmployee, instruction, details string) (*AskResult, error) {
	if aiEmployee == "" || instruction == "" {
		return nil, errors.New("wizardapplication: aiEmployee and instruction are required")
	}
	args := map[string]interface{}{"ai_employee": aiEmployee, "instruction": instruction}
	if details != "" {
		args["context"] = details
	}
	out := &AskResult{}
	if err := a.client.callTool(ctx, a.client.withTimeout(askTimeout), "ask_ai_employee", args, out); err != nil {
		return nil, err
	}
	return out, nil
}

// CallTool calls one tool on the workspace's MCP server and decodes its JSON
// result into out (which may be nil). A tool the key isn't allowed to use
// returns an *Error with Status 403; a tool that fails returns an *Error
// with Code "tool_error".
func (c *Client) CallTool(ctx context.Context, name string, args map[string]interface{}, out interface{}) error {
	return c.callTool(ctx, c.httpClient, name, args, out)
}

const notAllowed = "Unknown tool or not allowed"

func (c *Client) callTool(ctx context.Context, hc *http.Client, name string, args map[string]interface{}, out interface{}) error {
	if args == nil {
		args = map[string]interface{}{}
	}
	body := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      atomic.AddInt64(&c.mcpID, 1),
		"method":  "tools/call",
		"params":  map[string]interface{}{"name": name, "arguments": args},
	}
	var resp struct {
		Error *struct {
			Message string `json:"message"`
		} `json:"error"`
		Result *struct {
			Content []struct {
				Type string `json:"type"`
				Text string `json:"text"`
			} `json:"content"`
			IsError bool `json:"isError"`
		} `json:"result"`
	}
	if err := c.do(ctx, hc, "POST", "/mcp", nil, body, &resp); err != nil {
		return err
	}
	if resp.Error != nil {
		msg := resp.Error.Message
		if msg == "" {
			msg = "MCP request failed"
		}
		return &Error{Code: "mcp_error", Message: msg, RetryAfter: -1, Errors: map[string][]string{}}
	}
	if resp.Result == nil {
		return &Error{Code: "mcp_error", Message: "the MCP server sent an unexpected response", RetryAfter: -1, Errors: map[string][]string{}}
	}
	var text strings.Builder
	for _, part := range resp.Result.Content {
		if part.Type == "text" {
			text.WriteString(part.Text)
		}
	}
	if resp.Result.IsError {
		msg := text.String()
		if strings.HasPrefix(msg, notAllowed) {
			return &Error{Status: 403, Code: "permission_denied", Message: msg, RetryAfter: -1, Errors: map[string][]string{}}
		}
		if msg == "" {
			msg = "the tool failed"
		}
		return &Error{Code: "tool_error", Message: msg, RetryAfter: -1, Errors: map[string][]string{}}
	}
	if out == nil || text.Len() == 0 {
		return nil
	}
	if s, ok := out.(*string); ok {
		*s = text.String()
		return nil
	}
	return json.Unmarshal([]byte(text.String()), out)
}
