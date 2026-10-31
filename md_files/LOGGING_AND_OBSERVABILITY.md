# Logging and Observability

This document describes the centralized application logging and request-correlation design for the Spring Boot backend. It covers application diagnostics only. It is not an audit-log or analytics platform, and it does not replace the existing money-entry VOID/history behaviour.

## Architecture

The logging stack uses the existing Spring Boot logging infrastructure:

- SLF4J APIs with the Logback implementation provided by Spring Boot.
- One console/STDOUT output. No database log table, log-file requirement, external log shipper or distributed tracing dependency.
- A single request filter (`com.budgetingapp.logging.RequestCorrelationFilter`) for correlation and request summaries.
- The existing `@RestControllerAdvice` (`GlobalExceptionHandler`) for authoritative exception logging.
- The existing `SecurityErrorWriter` for security-filter error logging.
- MDC (`requestId`) for request correlation across controllers, services, repositories, exception handling and security filters.

Flow:

```
HTTP request
  -> RequestCorrelationFilter (requestId in MDC + X-Request-ID header)
  -> Spring Security filters
  -> Controller
  -> Service
  -> Repository / database
  -> GlobalExceptionHandler (unexpected/known failures)
  -> request.completed summary (single INFO record)
```

## Correlation ID behaviour

- Every request receives a server-generated ID in the form `req-` followed by 32 lowercase hexadecimal characters.
- The ID is placed in MDC under the key `requestId`.
- The same value is returned in the `X-Request-ID` response header.
- The same value populates the `traceId` field of the API error response.
- The filter removes the MDC value in a `finally` block, including when downstream code throws.
- Inbound `X-Request-ID` headers are intentionally ignored. They are not trusted for logging or error responses, so callers cannot spoof or inject log content. The server-generated value is authoritative.
- IDs are generated once per request. Sequential requests on reused servlet threads receive distinct IDs and do not inherit MDC state.

The error response contract is unchanged:

```json
{
  "timestamp": "2026-10-31T05:40:00Z",
  "status": 500,
  "code": "INTERNAL_ERROR",
  "message": "An unexpected error occurred.",
  "fieldErrors": [],
  "traceId": "req-82f19a00000000000000000000000000"
}
```

The `traceId` always equals the `X-Request-ID` response header for the same request. If a controller is invoked without the filter (for example a slice test), the handler still produces a safe ID rather than omitting the field.

## Log levels and conventions

- `ERROR`: unexpected failures only. Includes the exception and stack trace once, at the global exception boundary.
- `WARN`: operationally meaningful expected events such as authentication failures, CSRF/access-denied events, rate limiting, lifecycle conflicts (`409`) and slow requests.
- `INFO`: request summaries and important business outcomes (login, logout, registration, budget saved, money entry created/voided, SIP plan created/updated/stopped, SIP installment paid/replayed, credit-card baseline updated). Validation failures (`400`) are also INFO.
- `DEBUG`: safe diagnostic detail such as report period/grouping and excluded static/swagger request summaries.
- `TRACE`: not used.

Events use stable prefixes:

| Event | Level | Meaning |
| --- | --- | --- |
| `request.completed` | INFO / DEBUG | One HTTP summary per request |
| `request.slow` | WARN | Duration reached the configured threshold |
| `request.failed` | ERROR | Unexpected exception mapped to 500 |
| `request.validation_failed` | INFO | Bean-validation failure |
| `request.malformed` | INFO | Unreadable request body |
| `request.invalid_parameter` | INFO | Parameter type mismatch |
| `business.rejected` | INFO/WARN | Known business rule rejection |
| `business.failure` | ERROR | Business exception mapped to 5xx |
| `security.authentication_required` | INFO | Security filter returned 401 |
| `security.access_denied` | WARN | Security filter returned 403 |
| `security.rate_limited` | WARN | Login rate limit returned 429 |
| `db.failure` | ERROR | Unhandled database access failure |
| `auth.login_succeeded` / `auth.login_failed` | INFO / WARN | Login outcome without credentials |
| `auth.registered` / `auth.logout_succeeded` | INFO | Account lifecycle |
| `budget.saved` | INFO | Budget create/update outcome |
| `money_entry.created` / `money_entry.voided` / `money_entry.idempotent_replay` | INFO | Entry lifecycle |
| `investment_plan.created` / `investment_plan.updated` / `investment_plan.stopped` | INFO | SIP plan lifecycle |
| `sip_installment.marked_paid` | INFO | New SIP installment recorded as a POSTED investment entry |
| `sip_installment.idempotent_replay` | INFO | Existing SIP installment returned instead of creating a duplicate |
| `credit_card.opening_baseline_updated` | INFO | Opening baseline configured |
| `report.monthly_summary` / `report.summary` | DEBUG | Report period/grouping diagnostics |

The request summary is emitted exactly once per request by the filter. Controllers do not log request summaries again. Repository methods do not log routine INFO records.

## Structured log fields

The default console pattern is:

```
%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] %logger{40} requestId=%X{requestId:-none} - %msg%n
```

This provides `timestamp`, `level`, `thread`, `logger` and `requestId`. Request summaries include `service`, `env`, `method`, `route`, `status`, `durationMs` and the `event` prefix. Exception events include `errorCode`, `exceptionClass`, and database events additionally include `sqlState` and `vendorCode` when available.

When structured logging is enabled, Spring Boot's ECS formatter emits JSON to STDOUT, including MDC entries and the `service.name` / `service.environment` fields.

## Sensitive-data exclusion policy

Logging statements never print:

- Passwords or password hashes.
- Session identifiers, cookies, CSRF headers/tokens or authorization headers.
- API keys or database credentials.
- Complete HTTP request or response bodies.
- Full monetary request payloads or free-text transaction notes.
- User email addresses or other unnecessary identifying profile data.

Additional protections:

- Query strings are not logged. Route templates such as `/api/v1/budgets/{month}` are preferred; the concrete path is used only when no handler mapping exists (for example a request rejected by security).
- User-controlled values that are logged (route, method, field names, SQL state) pass through `LogSanitizer`, which replaces CR/LF, tabs and control characters and truncates long values to prevent log injection.
- Exception responses return generic messages. SQL text, stack traces, file paths and internal exception messages are not returned to clients.
- Hibernate SQL and bind-parameter logging are explicitly disabled in `application.properties`.

## Running and filtering logs locally in PowerShell

Run the backend normally and keep the console output:

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\mvnw.cmd spring-boot:run 2>&1 | Tee-Object -FilePath .\app.log
```

Locate every event for one request by its ID (from the response header or error body):

```powershell
Select-String -Path .\app.log -Pattern "req-82f19a00000000000000000000000000"
```

Show only failures:

```powershell
Get-Content .\app.log | Select-String -Pattern "ERROR|request.failed|db.failure"
```

Show one request summary per line:

```powershell
Get-Content .\app.log | Select-String -Pattern "request.completed"
```

Filter live output:

```powershell
.\mvnw.cmd spring-boot:run 2>&1 | Select-String -Pattern "requestId=|request.completed|ERROR"
```

## Environment-specific configuration

| Property / environment variable | Default | Purpose |
| --- | --- | --- |
| `app.environment` / `APP_ENV` | `local` | Environment label in request summaries |
| `app.logging.slow-request-threshold-ms` / `SLOW_REQUEST_THRESHOLD_MS` | `1000` | WARN threshold; `0` disables slow-request warnings |
| `logging.level.root` | `INFO` | Root level |
| `logging.level.com.budgetingapp` | `INFO` | Application package level |
| `logging.level.org.hibernate.SQL` | `OFF` | SQL logging disabled |
| `logging.level.org.hibernate.orm.jdbc.bind` | `OFF` | Bind values disabled |
| `SPRING_PROFILES_ACTIVE=prod` | not set | Enables `application-prod.properties` |

`application-prod.properties` enables Spring Boot's ECS structured console format and sets the ECS service environment. Standard Spring Boot overrides such as `LOGGING_LEVEL_COM_BUDGETINGAPP=DEBUG` remain available.

## Production STDOUT / collection expectations

- The application writes one centralized stream to STDOUT.
- Hosting infrastructure is expected to collect STDOUT later; the application does not write to a compulsory log directory and does not ship logs over the network.
- With `SPRING_PROFILES_ACTIVE=prod`, each record is a JSON object suitable for log collectors.
- Do not enable Hibernate SQL or bind-value logging in production.

## Redacted samples

Successful request:

```
2026-10-31T11:10:01.260+05:30 INFO  [http-nio-8080-exec-1] com.budgetingapp.request requestId=req-f53f41b407de4e5883f101aad2bdb65f - request.completed requestId=req-f53f41b407de4e5883f101aad2bdb65f service=budgeting-backend env=local method=GET route=/api/v1/budgets/{month} status=200 durationMs=34
2026-10-31T11:10:01.261+05:30 INFO  [http-nio-8080-exec-1] com.budgetingapp.service.MonthlyBudgetService requestId=req-f53f41b407de4e5883f101aad2bdb65f - budget.saved month=2026-10 inputMode=PERCENTAGE allocationMode=BALANCED investmentsEnabled=true
```

Unexpected failure:

```
2026-10-31T11:10:01.300+05:30 ERROR [http-nio-8080-exec-2] com.budgetingapp.exception.GlobalExceptionHandler requestId=req-82f19a00000000000000000000000000 - request.failed status=500 errorCode=INTERNAL_ERROR exceptionClass=java.lang.IllegalStateException method=GET route=/api/v1/reports/summary
java.lang.IllegalStateException: <redacted>
    at <redacted stack trace>
```

Security rejection:

```
2026-10-31T11:10:01.320+05:30 WARN  [http-nio-8080-exec-3] com.budgetingapp.security.SecurityErrorWriter requestId=req-1a2b3c00000000000000000000000000 - security.access_denied status=403 errorCode=CSRF_TOKEN_INVALID
```

## Known limitations

- Inbound request IDs are ignored by design; cross-service trace propagation is not implemented.
- There is no asynchronous request processing in this application, so MDC thread propagation for async executors is not implemented.
- Logs are not persisted by the application; retention and shipping depend on the hosting platform.
- For requests rejected before handler mapping, the concrete request path is logged because no route template is available.
- Database exceptions that are caught and converted to business exceptions by existing service code are not logged separately as `db.failure`; only the final business outcome is logged.
- Slow-request warnings are based on elapsed filter time and a configurable threshold; they are not a full performance-monitoring solution.
