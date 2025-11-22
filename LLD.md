```markdown
# Low-Level Design – Insights API Service

## 1. Scope

This Low-Level Design focuses on the **`insights-api`** module of the Real-Time Ad Insights Platform.

The goal is to describe in detail:

- The internal structure and responsibilities of the `insights-api` module.
- The key classes and interfaces (`AdInsightController`, `AdInsightService`, `ScyllaRepository`, `AthenaQueryService`).
- How the main APIs work:
  - `GET /ad/{campaignId}/clicks`
  - `GET /ad/{campaignId}/impressions`
  - `GET /ad/{campaignId}/clickToBasket`
- Extension points for multi-tenancy, authentication, and additional metrics.

> Note: Some implementation classes (e.g., concrete Scylla/Athena integrations) are intentionally abstracted as interfaces in the current codebase and are described here at a design level.

---

## 2. Module & Package Structure

### 2.1 Maven Module

The `insights-api` module is a Maven submodule of the parent project `real-time-stream-process`.

```text
insights-api/
├── pom.xml
└── src/
    └── main/java/com/dunnhumby/
        ├── Main.java
        ├── controller/
        │   └── AdInsightController.java
        ├── service/
        │   └── AdInsightService.java
        ├── repository/
        │   └── ScyllaRepository.java
        └── integrators/
            └── AthenaQueryService.java
```

### 2.2 Package Responsibilities

- `com.dunnhumby.controller`
  - HTTP layer; exposes REST endpoints, handles HTTP-specific concerns.
- `com.dunnhumby.service`
  - Core business logic; coordinates between controllers and data access.
- `com.dunnhumby.repository`
  - Abstraction over the real-time metrics store (ScyllaDB).
- `com.dunnhumby.integrators`
  - Abstraction over the historical analytics engine (Athena).
- `com.dunnhumby`
  - Entry point / configuration classes (current `Main.java` is a placeholder; a Spring Boot application class is expected here).

---

## 3. Class-Level Design

### 3.1 Application Entry Point (to be added)

**Class:** `com.dunnhumby.InsightsApiApplication`

**Stereotype:** Spring Boot application

**Responsibilities:**

- Bootstraps the Spring container.
- Enables component scanning under `com.dunnhumby`.
- Acts as the main entry point when running the service.

**Example implementation:**

```java
package com.dunnhumby;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InsightsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightsApiApplication.class, args);
    }
}
```

> The existing `Main.java` class that only prints `"Hello world!"` can be replaced or complemented by this Spring Boot entrypoint.

---

### 3.2 Controller – `AdInsightController`

**Location:** `com.dunnhumby.controller.AdInsightController`

**Stereotype:** REST Controller (`@RestController`)

**Annotations:**

- `@RestController`
- `@RequestMapping("/ad")`
- `@Slf4j` (Lombok logging)
- `@RequiredArgsConstructor` (Lombok-generated constructor for dependencies)
- `@Tag`, `@Operation`, `@ApiResponse` (OpenAPI/Swagger docs)

**Dependencies:**

- `private final AdInsightService adInsightService;`

**Responsibilities:**

- Expose REST endpoints for retrieving:
  - Clicks per campaign.
  - Impressions per campaign.
  - Click-to-basket counts.
- Handle HTTP-level concerns:
  - URL path variables and query parameters.
  - Response codes and response bodies.
  - Mapping of exceptions to HTTP responses (currently inline via try/catch).

**Endpoints:**

1. `GET /ad/{campaignId}/clicks`

```java
@GetMapping("/{campaignId}/clicks")
public ResponseEntity<Long> getClicks(
        @PathVariable String campaignId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    try {
        return ResponseEntity.ok(adInsightService.getClicks(campaignId, startDate, endDate));
    } catch (Exception e) {
        log.error("Error retrieving clicks for campaign {}: {}", campaignId, e.getMessage(), e);
        return ResponseEntity.internalServerError().build();
    }
}
```

2. `GET /ad/{campaignId}/impressions`

```java
@GetMapping("/{campaignId}/impressions")
public ResponseEntity<Long> getImpressions(
        @PathVariable String campaignId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    try {
        return ResponseEntity.ok(adInsightService.getImpressions(campaignId, startDate, endDate));
    } catch (Exception e) {
        log.error("Error retrieving impressions for campaign {}: {}", campaignId, e.getMessage(), e);
        return ResponseEntity.internalServerError().build();
    }
}
```

3. `GET /ad/{campaignId}/clickToBasket`

```java
@GetMapping("/{campaignId}/clickToBasket")
public ResponseEntity<Long> getClickToBasket(
        @PathVariable String campaignId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    try {
        return ResponseEntity.ok(adInsightService.getClickToCart(campaignId, startDate, endDate));
    } catch (Exception e) {
        log.error("Error retrieving click-to-cart for campaign {}: {}", campaignId, e.getMessage(), e);
        return ResponseEntity.internalServerError().build();
    }
}
```

**Design notes & improvements:**

- Currently, error handling is done per endpoint with repetitive try/catch blocks.
  - Recommended: move to a global `@ControllerAdvice` with exception handlers.
- Input validation is minimal:
  - Additional checks can be added (non-empty `campaignId`, `startDate <= endDate`, etc.).
- Multi-tenancy is not yet surfaced in the controller signature; planned approach is to derive `tenantId` from a security context/JWT rather than URL.

---

### 3.3 Service – `AdInsightService`

**Location:** `com.dunnhumby.service.AdInsightService`

**Stereotype:** Service (`@Service`)

**Annotations:**

- `@Service`
- `@Slf4j`
- `@RequiredArgsConstructor`

**Dependencies:**

- `private final ScyllaRepository scyllaRepo;`
- `private final AthenaQueryService athenaService;`

**Responsibilities:**

- Implement core **business operations** for insights:
  - Total clicks for a campaign in a given period.
  - Total impressions for a campaign in a given period.
  - Click-to-basket counts for a campaign in a given period.
- Decide whether a request should be served from **real-time store** (ScyllaDB) or **historical store** (Athena), based on the recency of the requested date range.
- Provide a clear, testable seam between HTTP layer and data access.

**Key methods:**

```java
public Long getClicks(String campaignId, LocalDate start, LocalDate end) {
    if (isRealTime(start, end)) {
        return scyllaRepo.getClicks(campaignId, start, end);
    } else {
        return athenaService.queryClicks(campaignId, start, end);
    }
}

public Long getImpressions(String campaignId, LocalDate start, LocalDate end) {
    if (isRealTime(start, end)) {
        return scyllaRepo.getImpressions(campaignId, start, end);
    } else {
        return athenaService.queryImpressions(campaignId, start, end);
    }
}

public Long getClickToCart(String campaignId, LocalDate start, LocalDate end) {
    if (isRealTime(start, end)) {
        return scyllaRepo.getClickToCart(campaignId, start, end);
    } else {
        return athenaService.queryClickToCart(campaignId, start, end);
    }
}
```

**Real-time vs Historical decision:**

```java
private boolean isRealTime(LocalDate start, LocalDate end) {
    return end == null || ChronoUnit.DAYS.between(end, LocalDate.now()) <= 30;
}
```

- If `end` is `null` → treat as real-time (current ongoing window).
- If `end` is within the last 30 days → treat as real-time.
- If `end` is older than 30 days → treat as historical.

> The `30`-day threshold is a business rule and should ideally be externalized to configuration (e.g., `application.yml`).

**Potential extensions:**

- Accept `tenantId` as a parameter (derived from security context) and pass it down to repositories for multi-tenant isolation.
- Introduce a `DateRange` value object to encapsulate `start`/`end` validation.
- Introduce a `DataSourceRoutingStrategy` interface if the routing logic becomes more complex (e.g., combining Scylla + Athena results for hybrid ranges).

---

### 3.4 Repository Interface – `ScyllaRepository`

**Location:** `com.dunnhumby.repository.ScyllaRepository`

**Stereotype:** Repository interface

**Responsibilities:**

- Abstracts reads from the **real-time metrics store** (ScyllaDB).
- Provides methods that match the service-level operations, but implemented against pre-aggregated metrics.

**Interface definition:**

```java
package com.dunnhumby.repository;

import java.time.LocalDate;

public interface ScyllaRepository {
    Long getClicks(String campaignId, LocalDate start, LocalDate end);

    Long getImpressions(String campaignId, LocalDate start, LocalDate end);

    Long getClickToCart(String campaignId, LocalDate start, LocalDate end);
}
```

**Typical implementation design (not in repo yet):**

- Use a Scylla/Cassandra driver (e.g., DataStax Java driver).
- Define CQL queries over tables like `campaign_metrics_realtime`.
- Map `campaignId`, `start`, `end`, and `tenantId` (once multi-tenancy is in place) to appropriate primary key and clustering key ranges.

Example pseudocode for an implementation method:

```java
@Override
public Long getClicks(String campaignId, LocalDate start, LocalDate end) {
    // Build CQL: SELECT SUM(value) FROM campaign_metrics_realtime
    // WHERE tenant_id = ? AND campaign_id = ? AND date BETWEEN ? AND ?
    //   AND metric_type = 'clicks';

    // Execute query and return aggregated result.
}
```

**Error handling considerations:**

- Timeouts and transient errors should be retried with backoff.
- Translate driver exceptions into domain-level exceptions (e.g., `DataAccessException`) that can be handled by service or global exception handler.

---

### 3.5 Integrator Interface – `AthenaQueryService`

**Location:** `com.dunnhumby.integrators.AthenaQueryService`

**Stereotype:** Integrator / external system interface

**Responsibilities:**

- Abstracts access to **Amazon Athena** for historical analytics over data stored in S3.
- Provides query methods that mirror `AdInsightService` operations but over longer time ranges.

**Interface definition:**

```java
package com.dunnhumby.integrators;

import java.time.LocalDate;

public interface dunnhumbyQueryService {
    Long queryClicks(String campaignId, LocalDate start, LocalDate end);

    Long queryImpressions(String campaignId, LocalDate start, LocalDate end);

    Long queryClickToCart(String campaignId, LocalDate start, LocalDate end);
}
```

**Typical implementation design (not in repo yet):**

- Use AWS SDK for Athena:
  - `StartQueryExecution` with SQL string.
  - Poll using `GetQueryExecution` until completion.
  - Retrieve results via `GetQueryResults`.
- Build SQL queries against external tables defined over S3 partitions, e.g.:

```sql
SELECT COUNT(DISTINCT user_id)
FROM ad_events
WHERE tenant_id = :tenantId
  AND campaign_id = :campaignId
  AND event_type = 'AD_CLICK'
  AND event_date BETWEEN :startDate AND :endDate;
```

- Convert result rows to a `Long` count.

**Error handling considerations:**

- Rate limiting and query concurrency.
- Timeouts and aborted queries.
- Cost control (e.g., restricting maximum date range or scanned data size per request).

---

## 4. Request Flows (Detailed)

### 4.1 `GET /ad/{campaignId}/clicks`

**Objective:** Return number of customers who clicked on the ad for a given campaign and time window.

**Step-by-step flow:**

1. **HTTP Request**
   - Client calls:

     ```http
     GET /ad/12345/clicks?startDate=2025-07-01&endDate=2025-07-15
     ```

2. **Controller Layer**
   - `AdInsightController.getClicks` is invoked.
   - Spring parses:
     - `campaignId` = `"12345"`
     - `startDate` = `2025-07-01` (LocalDate)
     - `endDate` = `2025-07-15` (LocalDate)
   - Method delegates to:

     ```java
     adInsightService.getClicks(campaignId, startDate, endDate)
     ```

3. **Service Layer – Routing**
   - `AdInsightService.getClicks` calls:

     ```java
     if (isRealTime(startDate, endDate)) {
         return scyllaRepo.getClicks(campaignId, startDate, endDate);
     } else {
         return athenaService.queryClicks(campaignId, startDate, endDate);
     }
     ```

   - If `endDate` is within the last 30 days, it uses `ScyllaRepository`; otherwise, `AthenaQueryService`.

4. **Repository / Integrator**
   - Real-time path:

     - `ScyllaRepository.getClicks` builds and runs a query against ScyllaDB, summing up click metrics for the campaign and date range.

   - Historical path:

     - `AthenaQueryService.queryClicks` builds and runs a SQL query in Athena over S3 data, aggregating distinct click events.

5. **Response**
   - The resulting count is returned to the controller as a `Long`.
   - Controller wraps it in `ResponseEntity.ok(count)` and returns HTTP 200.

6. **Error Scenarios**
   - If any exception occurs (e.g., DB connectivity issues):
     - Current code logs the error and returns `500 Internal Server Error`.
     - In the future, fine-grained exceptions can map to 4xx or 5xx codes as appropriate.

---

### 4.2 `GET /ad/{campaignId}/impressions`

Flow is identical to clicks, except the underlying metric is **impressions**.

- Controller method: `getImpressions`
- Service method: `getImpressions`
- Repository methods:
  - `ScyllaRepository.getImpressions`
  - `AthenaQueryService.queryImpressions`

The routing and error handling follow the same pattern as for clicks.

---

### 4.3 `GET /ad/{campaignId}/clickToBasket`

This endpoint returns the **number of customers who added a product to cart after clicking an ad**.

- Controller method: `getClickToBasket`
- Service method: `getClickToCart`
- Repository/Integrator methods:
  - `ScyllaRepository.getClickToCart`
  - `AthenaQueryService.queryClickToCart`

**Note:**

- The attribution (`clickToBasket`) is computed in the **stream processing layer** (Flink), which identifies sessions where a click is followed by an add-to-cart within a time window.
- The `insights-api` only reads these pre-computed aggregates; it does not perform attribution itself.

---

## 5. Cross-Cutting Concerns (Within insights-api)

### 5.1 Logging

- Lombok `@Slf4j` is used in `AdInsightController` and `AdInsightService`.
- Current behavior:
  - Logs errors on exceptions with campaign id context.

**Recommended enhancements:**

- Introduce structured logging (JSON) with MDC keys:
  - `tenant_id`, `campaign_id`, `request_id`.
- Add INFO-level logs for successful calls at service layer, if needed for audit.

### 5.2 Error Handling

- Currently, each controller method has its own try/catch.

**Planned improvements:**

- Implement a `@ControllerAdvice` with `@ExceptionHandler` methods for:
  - Validation errors → 400 Bad Request.
  - Data access issues → 503/500.
  - Generic fallback → 500.

### 5.3 Configuration

- Hard-coded real-time window (`30` days) in `isRealTime`.

**Recommended configuration:**

```yaml
insights:
  realtime-window-days: 30
```

- Inject via `@Value` or configuration properties class:

```java
@Value("${insights.realtime-window-days:30}")
private int realtimeWindowDays;

private boolean isRealTime(LocalDate start, LocalDate end) {
    return end == null || ChronoUnit.DAYS.between(end, LocalDate.now()) <= realtimeWindowDays;
}
```

### 5.4 Security & Multi-Tenancy (Extension Points)

- Add Spring Security configuration to:
  - Validate JWT tokens.
  - Extract `tenantId` and roles from token claims.

- Introduce a `TenantContext` component that reads the tenant from the security context and makes it available to:
  - `AdInsightService`.
  - `ScyllaRepository` and `AthenaQueryService` implementations.

Example usage in service:

```java
public Long getClicks(String campaignId, LocalDate start, LocalDate end) {
    String tenantId = tenantContext.getCurrentTenantId();

    if (isRealTime(start, end)) {
        return scyllaRepo.getClicks(tenantId, campaignId, start, end);
    } else {
        return athenaService.queryClicks(tenantId, campaignId, start, end);
    }
}
```

(Repository method signatures would be extended accordingly.)

---

## 6. Testing Strategy (insights-api)

### 6.1 Unit Tests – Service Layer

- **Class under test:** `AdInsightService`
- **Approach:**
  - Use JUnit 5 + Mockito.
  - Mock `ScyllaRepository` and `AthenaQueryService`.
  - Test routing logic through `isRealTime`:
    - Real-time with `end == null`.
    - Real-time with `end` within threshold.
    - Historical with `end` older than threshold.
  - Verify correct delegate is invoked with expected arguments.

### 6.2 Unit Tests – Controller Layer

- **Class under test:** `AdInsightController`
- **Approach:**
  - Use `@WebMvcTest(AdInsightController.class)`.
  - Mock `AdInsightService`.
  - Validate:
    - Path and query parameter binding.
    - HTTP response codes for success and error scenarios.
    - JSON or numeric response format.

### 6.3 Integration Tests (Optional)

- Spin up the full Spring context (with in-memory or mocked data layer) and issue HTTP requests using `TestRestTemplate` or `WebTestClient`.
- Useful for verifying OpenAPI configuration, filters, and security once implemented.

---

## 7. Extensibility

The `insights-api` design is intentionally modular and interface-driven:

- New metrics (e.g., conversion rate, revenue, ROAS) can be added by:
  - Extending `AdInsightService` with new methods.
  - Adding new methods to `ScyllaRepository` and `AthenaQueryService`.
  - Adding new endpoints in `AdInsightController`.

- Multi-tenancy can be cleanly integrated by:
  - Propagating `tenantId` from security context down the call stack.
  - Extending repository and integrator interfaces.

- Additional storage engines (e.g., Snowflake) can be introduced by:
  - Creating new integrator interfaces and implementations.
  - Adding routing strategies in `AdInsightService` (or delegated router component).

This keeps the HTTP layer, business logic, and data access **loosely coupled** and **testable**.
```
