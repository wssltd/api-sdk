// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

package com.wizardapplication.sdk;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The workspace's AI employees, through its MCP server. The key needs ai_employees.write. */
public final class AIEmployees {
    /** A job can take a few minutes; the server stops it at three. */
    private static final Duration ASK_TIMEOUT = Duration.ofSeconds(200);

    private final WizardApplication client;

    AIEmployees(WizardApplication client) {
        this.client = client;
    }

    /** Every AI employee that is switched on: id, name, purpose and paused. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list() {
        Object result = client.callTool("list_ai_employees", Map.of(), null);
        List<Map<String, Object>> out = new ArrayList<>();
        if (result instanceof Map && ((Map<String, Object>) result).get("ai_employees") instanceof List) {
            for (Object item : (List<Object>) ((Map<String, Object>) result).get("ai_employees")) {
                if (item instanceof Map) out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    /** Gives an AI employee (by id or name) a job in plain words. */
    public Map<String, Object> ask(String aiEmployee, String instruction) {
        return ask(aiEmployee, instruction, null);
    }

    /**
     * Gives an AI employee (by id or name) a job in plain words and waits for
     * it, which can take a few minutes. Returns summary, changes_made and
     * awaiting_approval. Changes wait for one of the people it reports to
     * unless the workspace trusts it with that kind of change. The same job
     * asked twice on the same day doesn't queue its changes twice.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> ask(String aiEmployee, String instruction, String context) {
        if (aiEmployee == null || aiEmployee.isEmpty() || instruction == null || instruction.isEmpty()) {
            throw new IllegalArgumentException("aiEmployee and instruction are required.");
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("ai_employee", aiEmployee);
        args.put("instruction", instruction);
        if (context != null && !context.isEmpty()) args.put("context", context);
        Object result = client.callTool("ask_ai_employee", args, ASK_TIMEOUT);
        return result instanceof Map ? (Map<String, Object>) result : Map.of();
    }
}
