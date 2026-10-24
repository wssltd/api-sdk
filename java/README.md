# Wizard Application Java SDK

Official Java client for the Wizard Application API. Java 11 or later, no dependencies.

## Install

Build and install the jar into your local Maven repository from this folder:

```bash
mvn install
```

Then add it to your project:

```xml
<dependency>
  <groupId>com.wizardapplication</groupId>
  <artifactId>wizardapplication-sdk</artifactId>
  <version>1.0.0</version>
</dependency>
```

Or copy `src/main/java/com/wizardapplication` into your source tree.

## Quick start

```java
import com.wizardapplication.sdk.*;
import java.util.Map;

WizardApplication client = WizardApplication.builder()
        .apiKey("wa_...")
        .workspace("acme") // acme.wizardapp.io
        .build();

Page page = client.jobs().list(Map.of("status", "published", "per_page", 50));
for (Map<String, Object> job : page) {
    System.out.println(job.get("title"));
}
System.out.println(page.getTotal() + " jobs in " + page.getTotalPages() + " pages");
```

Leave out `apiKey` and `workspace` to read them from `WIZARDAPP_API_KEY` and `WIZARDAPP_WORKSPACE`. Create one client and reuse it; it is thread safe.

Records are `Map<String, Object>`: whole numbers are `Long`, other numbers `Double`, nested objects `Map` and arrays `List`.

## Records

```java
Map<String, Object> job = client.jobs().get("JOB_ID");
job = client.jobs().create(Map.of("title", "Senior Developer", "location", "Leeds",
        "employment_type", "permanent", "description", "..."));
job = client.jobs().update((String) job.get("id"), Map.of("status", "published"));
client.jobs().delete((String) job.get("id"));
```

The same calls work on `client.candidates()`, `client.companies()`, `client.bookings()` and `client.leads()`.

```java
Map<String, Object> customer = client.leads().convert("LEAD_ID");

Page types = client.contentTypes().list();
Page posts = client.content("blog-posts").list(Map.of("status", "published"));
```

## Every page

```java
for (Map<String, Object> candidate : client.candidates().iterate(Map.of("status", "screening"))) {
    System.out.println(candidate.get("email"));
}
```

## Errors

All exceptions are unchecked.

```java
try {
    client.jobs().create(Map.of("location", "Leeds"));
} catch (ValidationException e) {
    System.out.println(e.getErrors()); // {title=[Title is required]}
} catch (RateLimitException e) {
    System.out.println("Wait " + e.getRetryAfter() + " seconds");
} catch (WizardApplicationException e) {
    System.out.println(e.getStatus() + " " + e.getCode() + " " + e.getMessage());
}
```

Exceptions: `ConnectionException`, `BadRequestException` (400), `AuthenticationException` (401), `PermissionDeniedException` (403), `NotFoundException` (404), `ValidationException` (422), `RateLimitException` (429), `ServerException` (5xx). All extend `WizardApplicationException`.

## Options

```java
WizardApplication.builder()
        .apiKey("wa_...")
        .workspace("acme")                  // or .baseUrl("https://acme.wizardapp.io/api/v1")
        .timeout(Duration.ofSeconds(30))
        .maxRetries(2)                      // 0 turns retries off
        .build();
```

`client.getLastRateLimit()` holds the limits from the latest response.

See the [main README](../README.md) for filters, retries and security.

Copyright (c) 2026 Wizard Software Solutions Ltd. All rights reserved. See [LICENSE](LICENSE).
