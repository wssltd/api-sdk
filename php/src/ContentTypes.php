<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * Content types (read only).
 */
final class ContentTypes
{
    private Client $client;

    public function __construct(Client $client)
    {
        $this->client = $client;
    }

    /** @param array<string, mixed> $params */
    public function list(array $params = []): Page
    {
        return new Page($this->client->request('GET', '/content-types', $params));
    }

    /** @return array<string, mixed> One content type, with its fields. */
    public function get(string $id): array
    {
        $payload = $this->client->request('GET', '/content-types/' . Resource::segment($id));
        return is_array($payload) && isset($payload['data']) && is_array($payload['data']) ? $payload['data'] : [];
    }
}
