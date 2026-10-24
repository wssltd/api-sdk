<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * Client for one workspace's Wizard Application API.
 *
 *     $client = new \WizardApplication\Client(['api_key' => 'wa_...', 'workspace' => 'acme']);
 *     $page = $client->jobs->list(['status' => 'published']);
 */
final class Client
{
    public const VERSION = '1.0.0';
    private const MAX_RETRY_WAIT = 60;
    private const LOCAL_HOSTS = ['localhost', '127.0.0.1', '::1', '[::1]'];

    private string $apiKey;
    public string $baseUrl;
    public int $timeout;
    public int $maxRetries;
    /** @var array{limit:int,remaining:int,reset:int}|null Rate limit headers from the latest response. */
    public ?array $lastRateLimit = null;

    public Resource $jobs;
    public Resource $candidates;
    public Resource $companies;
    public Resource $bookings;
    public Leads $leads;
    public ContentTypes $contentTypes;

    /**
     * @param array{api_key?:string,workspace?:string,base_url?:string,timeout?:int,max_retries?:int,allow_http?:bool} $options
     *   api_key defaults to the WIZARDAPP_API_KEY environment variable and
     *   workspace to WIZARDAPP_WORKSPACE. base_url replaces workspace and must
     *   be https. allow_http permits a plain http base_url (local testing only).
     */
    public function __construct(array $options = [])
    {
        $apiKey = $options['api_key'] ?? (getenv('WIZARDAPP_API_KEY') ?: '');
        if ($apiKey === '') {
            throw new \InvalidArgumentException('An API key is required (pass api_key or set WIZARDAPP_API_KEY).');
        }
        if (preg_match('/\s/', $apiKey)) {
            throw new \InvalidArgumentException('The API key must not contain whitespace.');
        }
        $this->apiKey = $apiKey;
        $workspace = $options['workspace'] ?? (isset($options['base_url']) ? null : (getenv('WIZARDAPP_WORKSPACE') ?: null));
        $this->baseUrl = self::resolveBaseUrl($workspace, $options['base_url'] ?? null, (bool) ($options['allow_http'] ?? false));
        $this->timeout = (int) ($options['timeout'] ?? 30);
        $this->maxRetries = max(0, (int) ($options['max_retries'] ?? 2));

        $this->jobs = new Resource($this, 'jobs');
        $this->candidates = new Resource($this, 'candidates');
        $this->companies = new Resource($this, 'companies');
        $this->bookings = new Resource($this, 'bookings');
        $this->leads = new Leads($this, 'leads');
        $this->contentTypes = new ContentTypes($this);
    }

    /** Items of one content type, by its slug or ID (e.g. "blog-posts"). */
    public function content(string $contentType): Resource
    {
        return new Resource($this, 'content/' . Resource::segment($contentType));
    }

    /** @return array<string, mixed> Never includes the API key. */
    public function __debugInfo(): array
    {
        return ['baseUrl' => $this->baseUrl, 'timeout' => $this->timeout, 'maxRetries' => $this->maxRetries];
    }

    private static function resolveBaseUrl(?string $workspace, ?string $baseUrl, bool $allowHttp): string
    {
        if ($baseUrl !== null && $baseUrl !== '') {
            $parts = parse_url($baseUrl);
            $scheme = $parts['scheme'] ?? '';
            $host = $parts['host'] ?? '';
            if ($host === '' || ($scheme !== 'https' && $scheme !== 'http')) {
                throw new \InvalidArgumentException('base_url must be a full URL, for example https://acme.wizardapp.io/api/v1');
            }
            if ($scheme === 'http' && !$allowHttp && !in_array($host, self::LOCAL_HOSTS, true)) {
                throw new \InvalidArgumentException('base_url must use https (your API key would be sent in clear text).');
            }
            if (isset($parts['user']) || isset($parts['pass'])) {
                throw new \InvalidArgumentException('base_url must not contain credentials.');
            }
            $url = rtrim($baseUrl, '/');
            return str_ends_with($url, '/api/v1') ? $url : $url . '/api/v1';
        }
        $ws = strtolower(trim((string) $workspace));
        if ($ws === '') {
            throw new \InvalidArgumentException("Pass workspace (e.g. 'acme') or base_url, or set WIZARDAPP_WORKSPACE.");
        }
        if (!preg_match('/^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/', $ws)) {
            throw new \InvalidArgumentException("workspace must be your workspace subdomain, for example 'acme' for acme.wizardapp.io.");
        }
        return "https://{$ws}.wizardapp.io/api/v1";
    }

    /**
     * Sends a request and returns the decoded JSON body (null when empty).
     * Retries a 429 after Retry-After, and network errors and 502/503/504 on
     * GET, PUT and DELETE, up to max_retries times.
     *
     * @param array<string, mixed>|null $query
     * @param array<string, mixed>|object|null $body
     * @return mixed
     */
    public function request(string $method, string $path, ?array $query = null, $body = null)
    {
        $method = strtoupper($method);
        $url = $this->baseUrl . $path . self::encodeQuery($query);
        $payload = null;
        $headers = [
            'Authorization: Bearer ' . $this->apiKey,
            'Accept: application/json',
            'User-Agent: wizardapplication-php/' . self::VERSION,
        ];
        if ($body !== null) {
            $payload = json_encode($body === [] ? new \stdClass() : $body, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
            $headers[] = 'Content-Type: application/json';
        }
        $idempotent = in_array($method, ['GET', 'PUT', 'DELETE'], true);

        for ($attempt = 0; ; $attempt++) {
            $responseHeaders = [];
            $ch = curl_init($url);
            curl_setopt_array($ch, [
                CURLOPT_CUSTOMREQUEST => $method,
                CURLOPT_HTTPHEADER => $headers,
                CURLOPT_RETURNTRANSFER => true,
                CURLOPT_TIMEOUT => $this->timeout,
                CURLOPT_CONNECTTIMEOUT => min(10, $this->timeout),
                // Never follow redirects: the API key must not reach another URL.
                CURLOPT_FOLLOWLOCATION => false,
                CURLOPT_SSL_VERIFYPEER => true,
                CURLOPT_SSL_VERIFYHOST => 2,
                CURLOPT_PROTOCOLS => CURLPROTO_HTTPS | CURLPROTO_HTTP,
                CURLOPT_HEADERFUNCTION => static function ($curl, string $line) use (&$responseHeaders): int {
                    $pos = strpos($line, ':');
                    if ($pos !== false) {
                        $responseHeaders[strtolower(trim(substr($line, 0, $pos)))] = trim(substr($line, $pos + 1));
                    }
                    return strlen($line);
                },
            ]);
            if ($payload !== null) {
                curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
            }
            $raw = curl_exec($ch);
            $status = (int) curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
            $curlError = curl_error($ch);
            curl_close($ch);

            if ($raw === false) {
                if ($attempt < $this->maxRetries && $idempotent) {
                    self::sleep(self::backoff($attempt + 1, null));
                    continue;
                }
                throw new ConnectionException("Could not reach {$this->baseUrl}: {$curlError}");
            }

            $this->rememberRateLimit($responseHeaders);
            $decoded = null;
            if ($raw !== '') {
                $decoded = json_decode((string) $raw, true);
                if (json_last_error() !== JSON_ERROR_NONE) {
                    $decoded = ['message' => substr((string) $raw, 0, 500)];
                }
            }
            if ($status >= 200 && $status < 300) {
                return $decoded;
            }
            if ($status >= 300 && $status < 400) {
                throw new ConnectionException('Redirect refused: the API key is never sent to another URL.');
            }

            $retryAfter = isset($responseHeaders['retry-after']) && ctype_digit($responseHeaders['retry-after'])
                ? (int) $responseHeaders['retry-after'] : null;
            $error = WizardApplicationException::forStatus($status, $decoded, $retryAfter);
            $retry = $status === 429
                ? $error->errorCode !== 'monthly_limit_exceeded'
                : in_array($status, [502, 503, 504], true) && $idempotent;
            if ($retry && $attempt < $this->maxRetries) {
                self::sleep(self::backoff($attempt + 1, $error->retryAfter));
                continue;
            }
            throw $error;
        }
    }

    /** @param array<string, string> $headers */
    private function rememberRateLimit(array $headers): void
    {
        if (!isset($headers['x-ratelimit-limit'])) {
            return;
        }
        $this->lastRateLimit = [
            'limit' => (int) $headers['x-ratelimit-limit'],
            'remaining' => (int) ($headers['x-ratelimit-remaining'] ?? 0),
            'reset' => (int) ($headers['x-ratelimit-reset'] ?? 0),
        ];
    }

    private static function backoff(int $attempt, ?int $retryAfter): float
    {
        if ($retryAfter !== null && $retryAfter >= 0) {
            return (float) min($retryAfter, self::MAX_RETRY_WAIT);
        }
        return min(0.5 * (2 ** ($attempt - 1)), 8.0);
    }

    private static function sleep(float $seconds): void
    {
        usleep((int) ($seconds * 1000000));
    }

    /** @param array<string, mixed>|null $query */
    private static function encodeQuery(?array $query): string
    {
        if (!$query) {
            return '';
        }
        $clean = [];
        foreach ($query as $key => $value) {
            if ($value === null) {
                continue;
            }
            $clean[$key] = is_bool($value) ? ($value ? 'true' : 'false') : (string) $value;
        }
        $q = http_build_query($clean, '', '&', PHP_QUERY_RFC3986);
        return $q === '' ? '' : '?' . $q;
    }
}
