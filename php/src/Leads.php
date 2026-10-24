<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * Leads, with conversion to a customer.
 */
final class Leads extends Resource
{
    /**
     * Converts a lead into a customer and returns the updated record.
     *
     * @return array<string, mixed>
     */
    public function convert(string $id): array
    {
        return self::unwrap($this->client->request('POST', $this->path . '/' . self::segment($id) . '/convert'));
    }
}
