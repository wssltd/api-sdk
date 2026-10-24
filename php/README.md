# Wizard Application PHP SDK

Official PHP client for the Wizard Application API. PHP 8.0 or later with the curl and json extensions, no other dependencies.

## Install

With Composer, add this folder as a path repository:

```json
{
    "repositories": [{ "type": "path", "url": "path/to/php" }],
    "require": { "wizardapplication/sdk": "*" }
}
```

Without Composer:

```php
require_once 'path/to/php/autoload.php';
```

## Quick start

```php
use WizardApplication\Client;

$client = new Client(['api_key' => 'wa_...', 'workspace' => 'acme']); // acme.wizardapp.io

$page = $client->jobs->list(['status' => 'published', 'per_page' => 50]);
foreach ($page as $job) {
    echo $job['title'], PHP_EOL;
}
echo "{$page->total} jobs in {$page->totalPages} pages", PHP_EOL;
```

Leave out `api_key` and `workspace` to read them from `WIZARDAPP_API_KEY` and `WIZARDAPP_WORKSPACE`.

## Records

```php
$job = $client->jobs->get('JOB_ID');
$job = $client->jobs->create(['title' => 'Senior Developer', 'location' => 'Leeds',
    'employment_type' => 'permanent', 'description' => '...']);
$job = $client->jobs->update($job['id'], ['status' => 'published']);
$client->jobs->delete($job['id']);
```

The same calls work on `$client->candidates`, `$client->companies`, `$client->bookings` and `$client->leads`.

```php
$customer = $client->leads->convert('LEAD_ID');

$types = $client->contentTypes->list();
$posts = $client->content('blog-posts')->list(['status' => 'published']);
```

## Every page

```php
foreach ($client->candidates->iterate(['status' => 'screening']) as $candidate) {
    echo $candidate['email'], PHP_EOL;
}
```

## Errors

```php
use WizardApplication\ValidationException;
use WizardApplication\RateLimitException;
use WizardApplication\WizardApplicationException;

try {
    $client->jobs->create(['location' => 'Leeds']);
} catch (ValidationException $e) {
    print_r($e->errors);              // ['title' => ['Title is required']]
} catch (RateLimitException $e) {
    echo "Wait {$e->retryAfter} seconds";
} catch (WizardApplicationException $e) {
    echo "{$e->status} {$e->errorCode} {$e->getMessage()}";
}
```

Exceptions: `ConnectionException`, `BadRequestException` (400), `AuthenticationException` (401), `PermissionDeniedException` (403), `NotFoundException` (404), `ValidationException` (422), `RateLimitException` (429), `ServerException` (5xx). All extend `WizardApplicationException`.

## Options

```php
new Client([
    'api_key' => 'wa_...',
    'workspace' => 'acme',     // or 'base_url' => 'https://acme.wizardapp.io/api/v1'
    'timeout' => 30,           // seconds
    'max_retries' => 2,        // 0 turns retries off
]);
```

`$client->lastRateLimit` holds the limit, remaining requests and seconds until reset from the latest response.

See the [main README](../README.md) for filters, retries and security.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
