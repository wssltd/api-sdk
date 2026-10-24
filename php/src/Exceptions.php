<?php

// Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved.

declare(strict_types=1);

namespace WizardApplication;

/**
 * Base class for every error the SDK throws.
 */
class WizardApplicationException extends \RuntimeException
{
    /** HTTP status, or 0 when no response arrived. */
    public int $status;
    /** The API error code, for example "validation_error". */
    public ?string $errorCode;
    /** @var array<string, string[]> Field errors for a 422. */
    public array $errors;
    /** Seconds to wait after a 429, when the API says. */
    public ?int $retryAfter;
    /** @var mixed The decoded response body. */
    public $body;

    /**
     * @param array<string, string[]> $errors
     * @param mixed $body
     */
    public function __construct(string $message, int $status = 0, ?string $errorCode = null, array $errors = [], ?int $retryAfter = null, $body = null, ?\Throwable $previous = null)
    {
        parent::__construct($message, $status, $previous);
        $this->status = $status;
        $this->errorCode = $errorCode;
        $this->errors = $errors;
        $this->retryAfter = $retryAfter;
        $this->body = $body;
    }

    /**
     * Builds the right exception for an HTTP status and decoded body.
     *
     * @param mixed $body
     */
    public static function forStatus(int $status, $body, ?int $retryAfter): self
    {
        $code = null;
        $message = "HTTP {$status}";
        $errors = [];
        if (is_array($body)) {
            if (isset($body['error']) && is_string($body['error'])) {
                $code = $body['error'];
            }
            if (isset($body['message']) && is_string($body['message']) && $body['message'] !== '') {
                $message = $body['message'];
            }
            if (isset($body['errors']) && is_array($body['errors'])) {
                foreach ($body['errors'] as $field => $list) {
                    $errors[(string) $field] = array_values(array_filter((array) $list, 'is_string'));
                }
            }
            if ($retryAfter === null && isset($body['retry_after']) && is_int($body['retry_after'])) {
                $retryAfter = $body['retry_after'];
            }
        }
        $class = [
            400 => BadRequestException::class,
            401 => AuthenticationException::class,
            403 => PermissionDeniedException::class,
            404 => NotFoundException::class,
            422 => ValidationException::class,
            429 => RateLimitException::class,
        ][$status] ?? ($status >= 500 ? ServerException::class : self::class);
        return new $class($message, $status, $code, $errors, $retryAfter, $body);
    }
}

/** No response: network failure, DNS, timeout or a refused redirect. */
class ConnectionException extends WizardApplicationException
{
}

/** 400: the request was malformed. */
class BadRequestException extends WizardApplicationException
{
}

/** 401: the API key is missing, invalid or expired. */
class AuthenticationException extends WizardApplicationException
{
}

/** 403: the key lacks a permission, or the plan has no API access (code "plan_upgrade_required"). */
class PermissionDeniedException extends WizardApplicationException
{
}

/** 404: the record or route does not exist. */
class NotFoundException extends WizardApplicationException
{
}

/** 422: validation failed; see $errors. */
class ValidationException extends WizardApplicationException
{
}

/** 429: the per-minute or monthly limit was reached; see $retryAfter. */
class RateLimitException extends WizardApplicationException
{
}

/** 5xx: something went wrong on the server. */
class ServerException extends WizardApplicationException
{
}
