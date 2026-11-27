<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * Team members: list, iterate, get, create and update. The API has no delete.
 * Creating a user adds a paid seat, so the key must have been created with the
 * per-seat charge accepted.
 */
final class Users extends Resource
{
    /**
     * Users can't be deleted through the API.
     */
    public function delete(string $id): void
    {
        throw new \BadMethodCallException("Users can't be deleted through the API.");
    }

    /**
     * Creates up to 100 users in one call. Returns 'data' (one entry per row,
     * status created, skipped or failed) and 'summary' (the counts). A row's
     * manager_email may name a user created earlier in the same call.
     *
     * @param list<array<string, mixed>> $users
     * @return array<string, mixed>
     */
    public function importUsers(array $users): array
    {
        $result = $this->client->request('POST', $this->path . '/import', null, ['users' => array_values($users)]);
        return is_array($result) ? $result : [];
    }

    /**
     * Sends, or sends again, the invitation email to a user who hasn't signed in yet.
     *
     * @return array<string, mixed>
     */
    public function sendInvitation(string $id): array
    {
        return self::unwrap($this->client->request('POST', $this->path . '/' . self::segment($id) . '/invitation'));
    }
}
