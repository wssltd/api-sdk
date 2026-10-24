<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

// Lists published jobs and the newest leads.
//
//   WIZARDAPP_API_KEY=wa_... WIZARDAPP_WORKSPACE=acme php examples/quickstart.php

declare(strict_types=1);

require_once __DIR__ . '/../autoload.php';

use WizardApplication\Client;
use WizardApplication\WizardApplicationException;

$client = new Client();

try {
    $jobs = $client->jobs->list(['status' => 'published', 'per_page' => 10]);
    echo "{$jobs->total} published jobs", PHP_EOL;
    foreach ($jobs as $job) {
        echo '- ', $job['title'], ' (', $job['location'] ?? 'no location', ')', PHP_EOL;
    }

    $leads = $client->leads->list(['sort' => 'created_at', 'order' => 'desc', 'per_page' => 5]);
    echo 'Newest leads:', PHP_EOL;
    foreach ($leads as $lead) {
        echo '- ', $lead['first_name'] ?? '', ' ', $lead['last_name'] ?? '', ' <', $lead['email'] ?? '', '>', PHP_EOL;
    }
} catch (WizardApplicationException $e) {
    echo 'Request failed: ', $e->getMessage(), PHP_EOL;
}

print_r($client->lastRateLimit);
