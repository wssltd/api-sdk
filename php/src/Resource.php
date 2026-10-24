<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * Standard list, get, create, update and delete for one resource.
 */
class Resource
{
    protected Client $client;
    protected string $path;

    public function __construct(Client $client, string $path)
    {
        $this->client = $client;
        $this->path = '/' . $path;
    }

    /** Escapes one path segment (an ID or slug). */
    public static function segment(string $id): string
    {
        if ($id === '') {
            throw new \InvalidArgumentException('An ID is required.');
        }
        return rawurlencode($id);
    }

    /**
     * One page of records. Pass filters (e.g. status, search) with page,
     * per_page (up to 100), sort and order.
     *
     * @param array<string, mixed> $params
     */
    public function list(array $params = []): Page
    {
        return new Page($this->client->request('GET', $this->path, $params));
    }

    /**
     * Every record across all pages, fetched a page at a time.
     *
     * @param array<string, mixed> $params
     * @return \Generator<int, array<string, mixed>>
     */
    public function iterate(array $params = []): \Generator
    {
        $params['per_page'] = $params['per_page'] ?? 100;
        for ($n = 1; ; $n++) {
            $params['page'] = $n;
            $page = $this->list($params);
            foreach ($page->data as $item) {
                yield $item;
            }
            if (!$page->hasNext() || count($page->data) === 0) {
                return;
            }
        }
    }

    /** @return array<string, mixed> */
    public function get(string $id): array
    {
        return self::unwrap($this->client->request('GET', $this->path . '/' . self::segment($id)));
    }

    /**
     * @param array<string, mixed> $data
     * @return array<string, mixed>
     */
    public function create(array $data): array
    {
        return self::unwrap($this->client->request('POST', $this->path, null, $data));
    }

    /**
     * @param array<string, mixed> $data
     * @return array<string, mixed>
     */
    public function update(string $id, array $data): array
    {
        return self::unwrap($this->client->request('PUT', $this->path . '/' . self::segment($id), null, $data));
    }

    public function delete(string $id): void
    {
        $this->client->request('DELETE', $this->path . '/' . self::segment($id));
    }

    /**
     * @param mixed $payload
     * @return array<string, mixed>
     */
    protected static function unwrap($payload): array
    {
        if (is_array($payload) && array_key_exists('data', $payload) && is_array($payload['data'])) {
            return $payload['data'];
        }
        return is_array($payload) ? $payload : [];
    }
}
