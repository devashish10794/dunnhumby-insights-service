Here is the full content of `HLD.md`:

```markdown
# High-Level Design – Real-Time Ad Insights Platform

## 1. Overview

This system provides **real-time and historical advertising insights** for online retailers and marketers.

It ingests high-velocity events from retailer websites and ad platforms (product views, add-to-cart, ad impressions, ad clicks), processes them in real time, and exposes insights via REST APIs.

Key goals:

- Low-latency metrics for active campaigns.
- Scalable ingestion and processing.
- Support for real-time and historical analytics.
- Foundation for multi-tenancy (multiple retailers/advertisers).

---

## 2. Core Components

### 2.1 Event Producers

**Sources**

- Retailer web and mobile applications.
- Retailer backends (checkout, cart service, product service).
- Ad serving systems (ad impressions, ad clicks).

**Event types (examples)**

- `PRODUCT_VIEW`
- `ADD_TO_CART`
- `AD_IMPRESSION`
- `AD_CLICK`

**Common event schema** (logical):

- `tenant_id` – retailer / advertiser identifier.
- `campaign_id` – ad campaign that generated the event.
- `user_id` – user identifier (hashed or internal id).
- `session_id` – browsing session identifier.
- `product_id` – where applicable.
- `timestamp` – event time (UTC).
- `event_type` – one of the defined types.
- `context` – additional metadata (page, device, referrer, etc.).

Events are sent via HTTPS/SDKs to an ingestion API and written to a streaming bus.

---

### 2.2 Event Ingestion – Kinesis

**Technology**: AWS Kinesis Data Streams

Responsibilities:

- Accept high-velocity writes from multiple producers.
- Provide ordered, replicated, partitioned logs of events.
- Decouple producers from downstream processing.

**Partitioning strategy**

- `partition_key = hash(tenant_id + campaign_id)`

Benefits:

- Even distribution across shards.
- All events for a (tenant, campaign) are ordered in a shard for easier correlation.

---

### 2.3 Real-Time Processing – Flink (real-time-processing module)

**Technology**: Apache Flink (or Kinesis Data Analytics) consuming from Kinesis.

Responsibilities:

1. **Ingestion and deserialization**
   - Consume records from Kinesis.
   - Deserialize JSON/Avro into strongly typed event objects.

2. **Sessionization**
   - Key by `(tenant_id, user_id, session_id)`.
   - Use session windows to group events into sessions.

3. **Attribution logic**
   - For each campaign within a session:
     - Track latest `AD_IMPRESSION` before `AD_CLICK`.
     - Track whether an `ADD_TO_CART` occurs within a configurable time window after an `AD_CLICK`.
   - Produce intermediate facts such as:
     - `CAMPAIGN_IMPRESSION` (per impression).
     - `CAMPAIGN_CLICK` (per click).
     - `CAMPAIGN_CLICK_TO_BASKET` (per successful click→basket attribution).

4. **Aggregation**
   - Aggregate counts over time windows per:
     - `tenant_id`
     - `campaign_id`
     - `date` (and optionally hour)
   - Maintain state in Flink and emit **delta updates** to downstream stores.

5. **Sinks**

   - **Real-time metrics sink → ScyllaDB**
     - Upserts aggregated metrics for recent time window (e.g., last 30 days).
   - **Raw events sink → S3**
     - Writes raw or lightly processed events into S3, partitioned by `tenant_id`, `date`, and optionally `event_type`.

---

### 2.4 Storage Layer

#### 2.4.1 Real-Time Store – ScyllaDB (persistence module)

**Technology**: ScyllaDB (Cassandra-compatible)

Responsibilities:

- Store pre-aggregated metrics for **recent** time periods (e.g., last 30 days).
- Serve low-latency, high-throughput queries from the Insights API.

Example logical table (per metric type):

```sql
CREATE TABLE campaign_metrics_realtime (
    tenant_id text,
    campaign_id text,
    date date,
    metric_type text,   -- clicks, impressions, click_to_basket
    value bigint,
    PRIMARY KEY ((tenant_id, campaign_id), date, metric_type)
) WITH CLUSTERING ORDER BY (date DESC, metric_type ASC);
```

Characteristics:

- Partition key: `(tenant_id, campaign_id)` for even data distribution.
- Clustering on `date` to support range queries across time.
- Additional tables or materialized views can be defined for different access patterns if necessary.

#### 2.4.2 Historical Store – S3 + Athena

**Technologies**: Amazon S3, Amazon Athena

Responsibilities:

- Long-term archival of raw events.
- Flexible, ad hoc and batch analytics for historical periods (beyond the real-time window).

S3 layout (example):

```text
s3://ad-events-bucket/
  tenant_id=<tenant_id>/
    dt=YYYY-MM-DD/
      hour=HH/
        event_type=AD_IMPRESSION/...
```

Athena external tables are defined over these prefixes and partition columns (`tenant_id`, `dt`, `hour`, `event_type`).

For insights APIs, Athena is used to aggregate:

- Clicks by campaign over a long date range.
- Impressions by campaign.
- Click-to-basket counts over arbitrary historical ranges.

---

### 2.5 Insights API – Spring Boot (insights-api module)

**Technology**: Spring Boot + Spring Web MVC, Springdoc OpenAPI

Responsibilities:

- Provide RESTful interfaces for marketers and retailer systems.
- Aggregate data from either real-time store (ScyllaDB) or historical store (Athena) depending on query window.

Main endpoints (per assignment):

- `GET /ad/{campaignId}/clicks`
  - Returns number of customers who clicked on the ad.
- `GET /ad/{campaignId}/impressions`
  - Returns number of customers who viewed the ads.
- `GET /ad/{campaignId}/clickToBasket`
  - Returns number of customers who added a product to cart after clicking on the ad.

All endpoints support optional query parameters:

- `startDate` (ISO date)
- `endDate` (ISO date)

**Key classes (from repo)**

- `AdInsightController`
  - REST controller exposing `/ad/{campaignId}/...` endpoints.
  - Uses Swagger/OpenAPI annotations for API documentation.
- `AdInsightService`
  - Business logic and **routing** between `ScyllaRepository` (real-time) and `AthenaQueryService` (historical).
  - Method examples: `getClicks`, `getImpressions`, `getClickToCart`.
  - Contains `isRealTime(start, end)` helper that decides which backend to use based on recency.
- `ScyllaRepository` (interface)
  - Abstraction over ScyllaDB queries for real-time aggregates.
- `AthenaQueryService` (interface)
  - Abstraction over Athena queries for historical aggregates.

**Routing logic (conceptual)**

- For a given `(campaignId, startDate, endDate)`:
  - If `endDate` is `null` or within `N` days of `now` (e.g., 30 days), use **ScyllaDB**.
  - If `endDate` is older than `N` days, or the requested range spans beyond `N` days in the past, use **Athena**.

This is implemented in the service layer to keep controllers thin and make it easy to test.

---

## 3. Data Flows

### 3.1 Event Ingestion and Processing Flow

1. **User Interaction**
   - Shopper browses retailer website/app, views products, views ads, clicks ads, and adds products to cart.

2. **Event Production**
   - Frontend or backend emits events to an ingestion API.
   - Events include `tenant_id`, `campaign_id`, `user_id`, `session_id`, `event_type`, `timestamp`, etc.

3. **Ingestion API → Kinesis**
   - Ingestion API authenticates/authorizes calls (if external) and validates payload.
   - Writes records to **Kinesis Data Streams**, using `(tenant_id, campaign_id)`-based partition keys.

4. **Kinesis → Flink**
   - Flink job consumes events from Kinesis.
   - Performs sessionization and attribution logic.
   - Maintains running aggregates in state.

5. **Flink → ScyllaDB + S3**
   - Writes/updates aggregated metrics into **ScyllaDB** for recent (real-time) windows.
   - Writes raw or minimally processed events into **S3** for long-term historical analysis.

6. **S3 → Athena**
   - Athena exposes SQL interface over the data in S3.
   - Used by the insights API and by analysts directly for batch analytics.

---

### 3.2 Real-Time Query Flow (ScyllaDB path)

1. **Client (Marketer) → Insights API**
   - Issues a GET request, e.g.:
     - `GET /ad/{campaignId}/clicks?startDate=2025-07-01&endDate=2025-07-07`
   - Authenticates using a JWT/OIDC token (future enhancement).

2. **Controller → Service**
   - `AdInsightController` parses path and query parameters.
   - Delegates to `AdInsightService.getClicks(campaignId, startDate, endDate)`.

3. **Service Routing**
   - `AdInsightService` evaluates `isRealTime(startDate, endDate)`.
   - Since the window is recent (e.g. endDate within last 30 days), it delegates to `ScyllaRepository`.

4. **ScyllaRepository → ScyllaDB**
   - Executes read query (e.g., `SELECT SUM(value) ...`) over `campaign_metrics_realtime` or equivalent tables.
   - Returns the aggregated count.

5. **Response**
   - `AdInsightService` returns the count.
   - Controller wraps it in an HTTP 200 response (JSON or plain numeric body).

---

### 3.3 Historical Query Flow (Athena path)

1. **Client (Marketer) → Insights API**
   - Issues a GET request with a long historical range, e.g.:
     - `GET /ad/{campaignId}/clickToBasket?startDate=2024-01-01&endDate=2024-12-31`

2. **Controller → Service**
   - Same as above: controller passes parameters to `AdInsightService`.

3. **Service Routing**
   - `AdInsightService` determines that the requested endDate is older than the configured real-time window.
   - Delegates to `AthenaQueryService`.

4. **AthenaQueryService → Athena**
   - Constructs parameterized SQL query against Athena tables over S3.
   - Starts query execution and polls for completion.
   - Reads and aggregates results into a single count.

5. **Response**
   - `AdInsightService` returns the historical aggregate.
   - Controller responds with HTTP 200 and the count.

---

## 4. Cross-Cutting Concerns

### 4.1 Authentication & Authorization (Planned)

- Use OAuth2/OIDC-based authentication (e.g. Cognito/Okta/Auth0).
- Issue JWT tokens that include:
  - `sub` (user id)
  - `tenant_id`
  - `roles` (e.g., `TENANT_ANALYST`, `TENANT_ADMIN`).
- API Gateway / Spring Security validates the token and injects tenant/context into the request.
- Service layer enforces that users can only access data for their tenant.

### 4.2 Multi-Tenancy

- **Data level**:
  - `tenant_id` is included in:
    - Kinesis partition keys.
    - ScyllaDB primary keys.
    - S3/Athena partitions.
- **API level**:
  - Tenant derived from JWT claim to avoid exposing tenantId in URL.
- **Isolation**:
  - Optional: separate Scylla keyspaces per tenant for strict isolation.
  - IAM policies to restrict Athena and S3 access by tenant if direct access is used.

### 4.3 Observability

- **Metrics**:
  - Request rates, latencies, and error rates for insights APIs.
  - Kinesis: iterator age, throttling, shard utilization.
  - Flink: checkpointing, backpressure, operator latency.
  - ScyllaDB: read/write latency, errors, resource utilization.
  - Athena: query latency, failure counts, and cost.

- **Logs**:
  - Structured, JSON logs from services including correlation IDs, tenant_id, campaign_id.
  - Ingested into centralized logging solution (e.g. CloudWatch Logs, ELK).

- **Tracing**:
  - OpenTelemetry instrumentation for end-to-end traces across:
    - Ingestion API → Kinesis → Flink → Scylla/S3.
    - Insights API → Scylla/Athena.

### 4.4 Scalability & Fault Tolerance

- **Horizontal scalability** at each layer:
  - Kinesis shards scale based on event volume.
  - Flink job scales via increased parallelism and more task managers.
  - ScyllaDB scales by adding nodes.
  - Insights APIs scale by increasing instance count behind load balancers.

- **Fault tolerance**:
  - Flink uses checkpointing and state backends for recovery.
  - Kinesis retains data for a configurable duration to replay.
  - ScyllaDB is replicated across AZs.
  - API instances are stateless and can be restarted/redeployed easily.

---

## 5. Challenges & Trade-Offs

- **Real-Time Performance vs Accuracy**
  - Late-arriving or out-of-order events may miss real-time windows and only appear in historical views.
  - Tuning Flink watermarks and allowed lateness is critical.

- **Cost vs Flexibility**
  - Athena offers flexible queries but costs scale with data scanned; careful partitioning and query design is required.
  - ScyllaDB needs proper capacity planning to avoid hotspots.

- **Multi-Tenancy Complexity**
  - Strong isolation vs shared infrastructure must be balanced.
  - Per-tenant SLAs and resource limits may be required to protect shared services.

---

## 6. Extensibility

The architecture is designed so that you can:

- Add new metrics (e.g., conversions, revenue, ROAS) by:
  - Extending Flink aggregations.
  - Adding fields/tables in ScyllaDB and Athena.
  - Extending the Insights API and services.
- Add new channels (mobile apps, external partner sites) by plugging new producers into the ingestion API.
- Swap or augment technology components (e.g., replace Athena with Snowflake) by implementing new integrator interfaces.
```
