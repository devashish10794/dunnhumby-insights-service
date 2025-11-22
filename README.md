```markdown
# Real-Time Ad Insights Platform

This repository contains a reference implementation and architecture for a **real-time streaming platform** for online shopping networks (e.g., Amazon, Flipkart, Walmart Connect, Target Roundel).

The platform ingests **high-velocity events** (product views, add-to-cart, ad impressions, ad clicks), processes them in real time, and exposes **REST APIs** to retrieve insights such as:

- Total **clicks** per campaign
- Total **impressions** per campaign
- **Click-to-basket** counts (customers who added a product to cart after clicking an ad)

The design is built around **AWS Kinesis + Flink + ScyllaDB + Spring Boot**.

---

## 1. Repository Structure

This is a **multi-module Maven project**:

```text
.
├── pom.xml                     # Parent POM (real-time-stream-process)
├── kinesis-producer-service/   # Kinesis client / ingestion module (skeleton)
├── realtime-processor/         # Flink (or Kinesis Data Analytics) streaming job
├── persistence/                # Persistence models / helpers for Scylla
└── insights-api/               # Spring Boot REST API for ad insights
```

Key modules:

- **`kinesis-producer-service`** – Event ingestion utilities or clients targeting AWS Kinesis.
- **`realtime-processor`** – Flink/Kinesis Data Analytics streaming job that:
  - Consumes events from Kinesis
  - Performs sessionization and attribution
  - Writes real-time aggregates to ScyllaDB
  - Writes raw events to S3
- **`persistence`** – (Conceptual) data access and schema helpers for ScyllaDB.
- **`insights-api`** – Spring Boot service implementing the ad insights APIs.

---

## 2. High-Level Architecture

At a glance:

- **Ingestion**: Retailer and ad systems send events → **Kinesis Data Streams**
- **Processing**: **Flink** job consumes Kinesis, performs:
  - Sessionization (user sessions across events)
  - Attribution (impression → click → add-to-basket)
  - Aggregation (clicks, impressions, click-to-basket counts)
- **Storage**:
  - **ScyllaDB** – real-time pre-aggregated metrics (recent window, e.g. 30 days)
- **Insights API**:
  - **Spring Boot (insights-api)** – routes requests to Scylla or Athena based on time range
  - Exposes:
    - `GET /ad/{campaignId}/clicks`
    - `GET /ad/{campaignId}/impressions`
    - `GET /ad/{campaignId}/clickToBasket`

**Detailed design docs:**

- [`HLD.md`](./HLD.md) – High-Level Design (components, data flows, cross-cutting concerns)
- `LLD.md` – Low-Level Design (focused on `insights-api` service)
- `Deployment-Plan.md` – Deployment topology & AWS-focused deployment plan
- `Technology-Stack.md` – Technologies used and rationale

> If your viewer supports PlantUML/Mermaid you can render the diagrams directly from these docs.

---

## 3. Insights API

### 3.1 Endpoints

All endpoints are exposed by the **`insights-api`** Spring Boot service under `/ad`:

1. **Clicks**
   - `GET /ad/{campaignId}/clicks`
   - Query params:
     - `startDate` (optional, ISO date, e.g. `2025-07-01`)
     - `endDate` (optional, ISO date)
   - Returns: `Long` – number of customers who clicked on the ad in the given period.

2. **Impressions**
   - `GET /ad/{campaignId}/impressions`
   - Query params:
     - `startDate` (optional)
     - `endDate` (optional)
   - Returns: `Long` – number of customers who viewed the ad.

3. **Click-to-Basket**
   - `GET /ad/{campaignId}/clickToBasket`
   - Query params:
     - `startDate` (optional)
     - `endDate` (optional)
   - Returns: `Long` – number of customers who added a product to cart after clicking an ad.

### 3.2 Real-Time vs Historical Routing

`AdInsightService` (in `insights-api`) chooses the backend based on the **end date** of the query:

- If `endDate` is **today or within the last N days** (e.g., 30 days), or `endDate` is `null`:
  - Query **ScyllaDB** via `ScyllaRepository` (real-time path).
- If `endDate` is **older than N days**:
  - Query **Athena** via `AthenaQueryService` (historical path).

This allows:

- Low-latency responses for active/ongoing campaigns.
- Cost-effective, flexible analytics for historical ranges.

### 3.3 API Documentation (Swagger/OpenAPI)

The `insights-api` module uses **Springdoc OpenAPI**:

- Add the `spring-boot` entry point (e.g. `InsightsApiApplication`) and run the service.
- Swagger UI will typically be exposed at something like:

```text
http://localhost:8080/swagger-ui/index.html
```

(Exact path may depend on configuration.)

---

## 4. Tech Stack

High-level stack:

- **Language**: Java (Java 21)
- **Build**: Maven, multi-module
- **API / Web**:
  - Spring Boot 3.x
  - Spring Web MVC
  - Springdoc OpenAPI
  - Lombok for boilerplate reduction
- **Streaming & Processing**:
  - AWS Kinesis Data Streams
  - Apache Flink (or Kinesis Data Analytics Job)
- **Storage**:
  - ScyllaDB (Cassandra-compatible) – real-time aggregate store
  - Amazon S3 – raw event store
  - Amazon Athena – historical analytics over S3
- **Testing**:
  - `spring-boot-starter-test` (JUnit 5, Mockito, etc.)
  - (Recommended) JaCoCo for coverage reporting

See `Technology-Stack.md` for more details.

---

## 5. Running Locally

> Note: The repository provides a **design and structural skeleton**; some concrete implementations (e.g., actual Scylla/Athena integration, Flink job code) may need to be filled in based on your environment.

### 5.1 Prerequisites

- JDK 21+
- Maven 3.9+
- (If running end-to-end) Access to:
  - AWS Kinesis, S3, Athena
  - ScyllaDB cluster (local or remote)

### 5.2 Build All Modules

From the project root:

```bash
mvn clean install
```

This compiles and tests all modules.

### 5.3 Run the Insights API

1. Ensure you have the Spring Boot application class in `insights-api` (e.g. `InsightsApiApplication` annotated with `@SpringBootApplication`).
2. From project root:

```bash
cd insights-api
mvn spring-boot:run
```

3. Default port is usually `8080`:
   - `http://localhost:8080/ad/{campaignId}/clicks`
   - `http://localhost:8080/swagger-ui/index.html`

Configure **ScyllaDB** and **Athena** connection properties in `application.yml`/`application.properties` (not included here).

---

## 6. Testing

### 6.1 Unit Tests

Recommended unit tests (some may be provided, others to be added):

- **`AdInsightService`**
  - Mock `ScyllaRepository` and `AthenaQueryService`.
  - Test routing logic for:
    - Real-time window
    - Historical window
  - Validate correct counts are returned.

- **`AdInsightController`**
  - `@WebMvcTest` for:
    - Endpoint mapping correctness.
    - Validation of `startDate`/`endDate` parsing.
    - HTTP status codes (200, 400, 500).

Run tests with:

```bash
mvn test
```

### 6.2 Coverage (recommended)

Add JaCoCo plugin in the parent POM for coverage reporting, then:

```bash
mvn test
mvn jacoco:report
```

Open `target/site/jacoco/index.html` in a browser to view coverage.

---

## 7. Cross-Cutting Concerns

### 7.1 Authentication & Authorization

Planned approach:

- OAuth2/OIDC with JWT tokens (Cognito, Okta, Auth0, etc.).
- API Gateway / Spring Security for:
  - Token validation.
  - Tenant extraction from claims (e.g., `tenant_id`).
  - Role-based access control.

### 7.2 Multi-Tenancy

- **Data model**:
  - `tenant_id` is included:
    - In all events.
    - In Kinesis partition keys.
    - In Scylla primary keys.
    - As partition columns in S3/Athena.
- **API**:
  - Tenant resolved from JWT (claim) at the edge.
  - Service layer ensures calls are scoped to that tenant.

### 7.3 Observability

Recommended stack:

- **Metrics**:
  - Spring Boot Actuator + Prometheus/Grafana.
  - CloudWatch metrics for Kinesis, Flink, Athena.
- **Logs**:
  - Structured JSON logs.
  - Correlation IDs, tenant_id, campaign_id in MDC.
- **Tracing**:
  - OpenTelemetry instrumentation across ingestion, processing, and API layers.

---

## 8. Design Decisions & Trade-Offs (Summary)

- **Kinesis + Flink** chosen for:
  - Managed, scalable streaming ingestion and processing on AWS.
- **ScyllaDB** for real-time store:
  - High throughput, low latency, easy horizontal scaling.
- **S3 + Athena** for history:
  - Very cost-effective, serverless analytics for large datasets.
- **Spring Boot** for API:
  - Mature ecosystem, fast development of REST APIs, strong integration with docs and testing.

Trade-offs:

- Slight **complexity** in maintaining two storage paths (real-time + historical), but this yields a good balance of **latency**, **flexibility**, and **cost**.
- Multi-tenancy and strong isolation require careful IAM and schema design.

---
