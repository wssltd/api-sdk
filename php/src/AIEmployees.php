<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * The workspace's AI employees, through its MCP server. The key needs the
 * ai_employees.write permission.
 */
final class AIEmployees
{
    /** A job can take a few minutes; the server stops it at three. */
    private const ASK_TIMEOUT = 200;

    private Client $client;

    public function __construct(Client $client)
    {
        $this->client = $client;
    }

    /**
     * Every AI employee that is switched on: id, name, purpose and paused.
     *
     * @return list<array<string, mixed>>
     */
    public function list(): array
    {
        $result = $this->client->callTool('list_ai_employees', []);
        $items = is_array($result) && isset($result['ai_employees']) && is_array($result['ai_employees']) ? $result['ai_employees'] : [];
        return array_values(array_filter($items, 'is_array'));
    }

    /**
     * Gives an AI employee (by id or name) a job in plain words and waits for
     * it, which can take a few minutes. Returns summary, changes_made and
     * awaiting_approval. Changes wait for one of the people it reports to
     * unless the workspace trusts it with that kind of change. The same job
     * asked twice on the same day doesn't queue its changes twice.
     *
     * @return array<string, mixed>
     */
    public function ask(string $aiEmployee, string $instruction, ?string $context = null): array
    {
        if ($aiEmployee === '' || $instruction === '') {
            throw new \InvalidArgumentException('aiEmployee and instruction are required.');
        }
        $args = ['ai_employee' => $aiEmployee, 'instruction' => $instruction];
        if ($context !== null && $context !== '') {
            $args['context'] = $context;
        }
        $result = $this->client->callTool('ask_ai_employee', $args, self::ASK_TIMEOUT);
        return is_array($result) ? $result : [];
    }
}
