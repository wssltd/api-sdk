<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * One page of a list response.
 *
 * @implements \IteratorAggregate<int, array<string, mixed>>
 */
final class Page implements \IteratorAggregate, \Countable
{
    /** @var array<int, array<string, mixed>> */
    public array $data;
    public int $page;
    public int $perPage;
    public int $total;
    public int $totalPages;

    /** @param mixed $payload */
    public function __construct($payload)
    {
        $payload = is_array($payload) ? $payload : [];
        $this->data = isset($payload['data']) && is_array($payload['data']) ? array_values($payload['data']) : [];
        $p = isset($payload['pagination']) && is_array($payload['pagination']) ? $payload['pagination'] : [];
        $this->page = (int) ($p['page'] ?? 1);
        $this->perPage = (int) ($p['per_page'] ?? count($this->data));
        $this->total = (int) ($p['total'] ?? count($this->data));
        $this->totalPages = (int) ($p['total_pages'] ?? 1);
    }

    public function hasNext(): bool
    {
        return $this->page < $this->totalPages;
    }

    public function getIterator(): \ArrayIterator
    {
        return new \ArrayIterator($this->data);
    }

    public function count(): int
    {
        return count($this->data);
    }
}
