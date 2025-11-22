# Technology Stack

## 1. Programming Languages

- **Java** (Java 21) – primary language for all modules.

---

## 2. Frameworks & Libraries

### 2.1 Backend Framework

- **Spring Boot 3.x**
  - REST API (`insights-api`).
  - Dependency injection and configuration management.

- **Lombok**
  - Annotations like `@Slf4j`, `@RequiredArgsConstructor` to reduce boilerplate.

- **Springdoc OpenAPI**
  - `springdoc-openapi-starter-webmvc-ui`
  - Generates Swagger/OpenAPI documentation for REST APIs.

### 2.2 Streaming & Data Processing

- **AWS Kinesis Data Streams**
  - Primary event ingestion bus for web and ad events.

- **Apache Flink**
  - Real-time stream processing (via `real-time-processing` module).
  - Sessionization, windowed aggregations, and metrics computation.

---

## 3. Storage & Analytics

- **ScyllaDB**
  - Distributed, Cassandra-compatible NoSQL database.
  - Used as a real-time metrics store for campaign insights (recent time window).

- **Amazon S3**
  - Durable, cheap storage for raw event data.
  - Partitioned layout for efficient querying with Athena.

- **Amazon Athena**
  - Serverless query engine over data in S3.
  - Used for historical queries and long-term analytics.

---

## 4. APIs & Documentation

- **REST Endpoints** (insights-api)
  - `GET /ad/{campaignId}/clicks`
  - `GET /ad/{campaignId}/impressions`
  - `GET /ad/{campaignId}/clickToBasket`

- **OpenAPI / Swagger**
  - Auto-generated docs available in Swagger UI.
  - Annotations in `AdInsightController` describe:
    - Summary and description.
    - Response codes and payload type.

---

## 5. Build & Dependency Management

- **Maven**
  - Multi-module project with parent POM (`real-time-stream-process`).
  - Modules:
    - `kinesis`
    - `real-time-processing`
    - `persistence`
    - `insights-api`

- **Spring Boot Maven Plugin**
  - For packaging and running Spring Boot services.

---

## 6. Testing (Current & Planned)

- **Spring Boot Starter Test**
  - Included in root `pom.xml` for unit and integration tests.
- **Planned usage:**
  - JUnit 5 for unit tests (controllers, services).
  - Mocking frameworks (e.g., Mockito) for repository/integrator tests.
  - JaCoCo for coverage reporting.

---

## 7. Observability Tools (Recommended)

- **Spring Boot Actuator**
  - Health checks, metrics endpoints.

- **Prometheus & Grafana (optional)**
  - Metrics scraping and dashboards.

- **CloudWatch**
  - Logs and metrics for AWS services (Kinesis, Lambda/Flink, Athena).

- **OpenTelemetry**
  - Distributed tracing across microservices and AWS components.

---

## 8. Security & Authentication (Planned)

- **Spring Security**
  - OAuth2 / OIDC-based authentication.
  - Role-based and tenant-based authorization.

- **Identity Provider**
  - Cognito / Okta / Auth0 (choice depends on organization).

---

## 9. Rationale Summary

- **Kinesis + Flink**: Optimized for high-throughput, low-latency event processing in AWS.
- **ScyllaDB**: High-performance, horizontally scalable NoSQL store for real-time metrics.
- **S3 + Athena**: Cost-efficient, serverless analytics for large historical datasets.
- **Spring Boot + Springdoc**: Rapid API development with strong ecosystem and automatic documentation.
