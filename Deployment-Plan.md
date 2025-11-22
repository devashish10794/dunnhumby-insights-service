```markdown
# Deployment Plan – Real-Time Ad Insights Platform

> Assumption: Primary deployment target is **AWS**. The design can be adapted to other clouds with equivalent services.

## 1. Components

- **Event Ingestion**
  - AWS Kinesis Data Streams
  - Optional API Gateway / ALB fronting ingestion endpoints

- **Real-Time Processing**
  - Apache Flink on:
    - Kinesis Data Analytics, or
    - Self-managed Flink cluster on ECS/EKS

- **Storage**
  - ScyllaDB cluster (self-managed or managed equivalent)

- **API Layer**
  - Spring Boot `insights-api` service
  - Deployed on ECS (Fargate), EKS, or EC2 autoscaling group
  - Fronted by an Application Load Balancer (ALB) or API Gateway

- **Observability**
  - CloudWatch (logs + metrics)
  - Prometheus + Grafana for detailed metrics (if using EKS)
  - OpenTelemetry traces to a backend (e.g., X-Ray, Tempo, Jaeger)

---

## 2. Deployment Topology

### 2.1 Network

- VPC with:
  - Public subnets for:
    - Load balancers (ALB/API Gateway integration)
  - Private subnets for:
    - ECS/EKS nodes
    - ScyllaDB nodes
- Multi-AZ deployment for high availability.

### 2.2 Ingestion Layer

- **Kinesis Data Streams**
  - Minimum of 3 shards to start, auto-scaled by traffic.
  - Events written by:
    - Retailer backends via AWS SDK.
    - Ad platforms via ingestion HTTP endpoint → Lambda → Kinesis.

- **Firehose / custom sink**
  - Streams raw events from Kinesis to S3 for archival and Athena.

---

### 2.3 Real-Time Processing

- **Flink application**
  - Packaged as a JAR (real-time-processing module).
  - Deployed on:
    - Kinesis Data Analytics (serverless), or
    - Flink cluster on EKS/ECS.
  - Configurations:
    - Checkpointing to durable storage (S3).
    - Parallelism tuned based on shard count and load.
    - Watermarks and allowed lateness for out-of-order events.

---

### 2.4 Storage and Analytics

- **ScyllaDB**
  - Multi-node cluster (3+ nodes) across AZs.
  - Keyspace configured with proper replication factor.
  - Used exclusively for:
    - Recent time window (e.g., last 30 days) aggregated metrics.


---

### 2.5 Insights API

- **Service**
  - `insights-api` Spring Boot application image:
    - Built with Maven and dockerized.
  - Running on ECS/EKS with:
    - Auto-scaling based on CPU/QPS.
    - Environment variables for:
      - ScyllaDB contact points.
      - Athena JDBC/SDK config.
      - Real-time threshold (e.g., 30 days).

- **Ingress**
  - Application Load Balancer or API Gateway.
  - Exposes:
    - `/ad/{campaignId}/clicks`
    - `/ad/{campaignId}/impressions`
    - `/ad/{campaignId}/clickToBasket`
  - Optional:
    - `/swagger-ui.html` for documentation.
    - `/actuator/health` for health checks.

- **Security**
  - API Gateway integrating with:
    - Cognito, Okta, or other OIDC provider for JWT validation.
  - Network security:
    - SGs to restrict API access only via ALB/API Gateway.
    - DB SGs to allow only API/processing layer.

---

## 3. CI/CD

- **CI**
  - Build each module with Maven.
  - Run unit tests and static analysis (Checkstyle/SpotBugs).
  - Generate test and coverage reports.

- **CD**
  - Build Docker images for runtime modules (real-time-processing, insights-api).
  - Push images to ECR.
  - Deploy via:
    - ECS service update, or
    - Helm chart (if using EKS).

- **Config Management**
  - Use SSM Parameter Store or Secrets Manager for:
    - DB credentials.
    - Athena access keys (if needed).
  - Environment-specific configs via:
    - Spring profiles (`dev`, `stage`, `prod`).

---

## 4. Scalability & Resilience

- **Scalability**
  - Kinesis shards scale with event rate.
  - Flink parallelism scales with CPU/memory.
  - ScyllaDB scaled by adding nodes.
  - API layer scales horizontally (more ECS tasks or pods).

- **Resilience**
  - Use auto-scaling groups and health checks.
  - Blue/green or canary deployments for API and Flink.
  - Backup strategies:
    - ScyllaDB snapshots.

---

## 5. Monitoring & Alerting

- **Metrics and Dashboards**
  - Kinesis: producer/consumer throughput, iterator age.
  - Flink: checkpoint success, backpressure, operator latency.
  - ScyllaDB: read/write latency, CPU, GC.
  - API: request rate, latency, error rates per endpoint.

- **Alerts**
  - High 5xx error rate on API.
  - High iterator age on Kinesis.
  - Flink checkpoints failing persistently.
  - ScyllaDB node down or high read/write latencies.

---

## 6. Cost Management

- **Kinesis**
  - Monitor shard usage and scale down during off-peak.
- **Athena**
  - Optimize partitions and columnar storage (e.g., Parquet).
  - Enforce limits on historical query ranges.
- **ScyllaDB**
  - Right-size cluster; monitor CPU and disk usage.
- **APIs**
  - Autoscale instances based on actual traffic.
