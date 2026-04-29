# FlowDeck Platform — Product Design Document

**Version:** 1.2  
**Status:** Draft  
**Project Codename:** FlowDeck  
**Primary Cloud:** Amazon Web Services (AWS) — `us-east-1` launch region  
**Cloud Strategy:** AWS-first, cloud-agnostic abstractions from day one  
**Audience:** Engineering, Architecture, Product

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Cloud Strategy — AWS First, Multi-Cloud Ready](#2-cloud-strategy--aws-first-multi-cloud-ready)
3. [Product Vision & Deployment Tiers](#3-product-vision--deployment-tiers)
4. [Core Architecture Principles](#4-core-architecture-principles)
5. [AWS Infrastructure Foundation (Phase 1)](#5-aws-infrastructure-foundation-phase-1)
6. [Cloud Provider Abstraction Layer](#6-cloud-provider-abstraction-layer)
7. [System Components](#7-system-components)
8. [Deployment Mode 1 — Public Cloud (Fully Managed on AWS)](#8-deployment-mode-1--public-cloud-fully-managed-on-aws)
9. [Deployment Mode 2 — Agent-Based Managed Airflow](#9-deployment-mode-2--agent-based-managed-airflow)
10. [Deployment Mode 3 — FlowDeck CLI](#10-deployment-mode-3--flowdeck-cli)
11. [Control Plane Deep Dive](#11-control-plane-deep-dive)
12. [Data Plane Deep Dive](#12-data-plane-deep-dive)
13. [Agent Architecture Deep Dive](#13-agent-architecture-deep-dive)
14. [Networking Architecture on AWS](#14-networking-architecture-on-aws)
15. [API Design](#15-api-design)
16. [Authentication & Authorization](#16-authentication--authorization)
17. [Observability Stack](#17-observability-stack)
18. [Multi-Tenancy Model](#18-multi-tenancy-model)
19. [Extension Path — Remote Execution Mode](#19-extension-path--remote-execution-mode)
20. [Extension Path — Private Cloud Mode](#20-extension-path--private-cloud-mode)
21. [Extension Path — Multi-Cloud (GCP & Azure)](#21-extension-path--multi-cloud-gcp--azure)
22. [Extension Path — Multi-Region](#22-extension-path--multi-region)
23. [Data Model](#23-data-model)
24. [Technology Stack](#24-technology-stack)
25. [Security Architecture](#25-security-architecture)
26. [Cost Architecture on AWS](#26-cost-architecture-on-aws)
27. [Infrastructure as Code](#27-infrastructure-as-code)
28. [Rollout & Migration Strategy](#28-rollout--migration-strategy)
29. [Open Questions & Decisions](#29-open-questions--decisions)

---

## 1. Executive Summary

**FlowDeck** is a managed Apache Airflow orchestration platform. It launches on **Amazon Web Services (AWS)** as the sole cloud provider, but is architected from day one to extend to Google Cloud Platform (GCP), Microsoft Azure, and on-premises environments without core redesign.

The platform supports three deployment modes at launch:

- **Mode 1 — Public Cloud:** Fully managed Airflow deployments hosted in FlowDeck's AWS account. Runs on Amazon EKS, Amazon RDS Aurora PostgreSQL, Amazon ECR, Amazon S3, and AWS Secrets Manager.
- **Mode 2 — Agent-Based:** Customer installs a lightweight Helm-based agent into their own Kubernetes cluster. FlowDeck manages Airflow inside the customer's cluster via an outbound-only connection. Works on customer's EKS, GKE, AKS, or any Kubernetes 1.27+.
- **Mode 3 — CLI:** A single `flowdeck` binary for local development, CI/CD, DAG authoring, connection management, and all deployment lifecycle operations.

Future delivery phases add **Remote Execution**, **Private Cloud**, and **multi-cloud Control Plane** support (GCP and Azure).

**Launch region:** `us-east-1` (N. Virginia).  
**DR region (Phase 3):** `us-west-2` (Oregon).

---

## 2. Cloud Strategy — AWS First, Multi-Cloud Ready

### 2.1 Philosophy

FlowDeck ships on AWS first because it offers the broadest managed service coverage, the largest enterprise customer base, and the most mature EKS ecosystem. However, a significant portion of potential customers run on GCP or Azure, or operate multi-cloud environments. Locking the platform to AWS primitives at the design level would make future expansion expensive.

The strategy is:

> **Use AWS managed services concretely in Phase 1. Wrap every cloud-specific integration behind a provider interface from day one so that adding GCP or Azure in Phase 4 is a new implementation of an existing interface, not a refactor.**

### 2.2 The Three Layers of Cloud Coupling

| Layer | Coupling Level | AWS Phase 1 | GCP Future | Azure Future |
|---|---|---|---|---|
| **Infrastructure** (compute, DB, storage) | High — service-specific | EKS, RDS Aurora, S3, ECR | GKE, Cloud SQL, GCS, Artifact Registry | AKS, Azure DB for PG, Azure Blob, ACR |
| **Platform services** (secrets, DNS, certs, networking) | Medium — abstracted via interface | Secrets Manager, Route53, ACM, VPC | Secret Manager, Cloud DNS, Cert Manager, VPC | Key Vault, Azure DNS, App Service Certs, VNet |
| **Application logic** (API Server, Agent, Airflow Runtime) | Zero — cloud-agnostic | ✓ | ✓ | ✓ |

### 2.3 What is Abstracted Behind Interfaces (Phase 1)

These Go interfaces are defined in Phase 1, with only the AWS implementation built. GCP/Azure implementations slot in later without changing any business logic:

```go
// CloudProvider is the top-level provider interface
type CloudProvider interface {
    Compute()   ComputeProvider   // EKS → GKE → AKS
    Database()  DatabaseProvider  // RDS Aurora → Cloud SQL → Azure DB
    Storage()   StorageProvider   // S3 → GCS → Azure Blob
    Registry()  RegistryProvider  // ECR → Artifact Registry → ACR
    Secrets()   SecretsProvider   // Secrets Manager → Secret Manager → Key Vault
    DNS()       DNSProvider       // Route53 → Cloud DNS → Azure DNS
    Network()   NetworkProvider   // VPC/PrivateLink → VPC/PSC → VNet/Private Link
}
```

### 2.4 What is NOT Abstracted (Intentionally)

The following AWS services are used directly in Phase 1 and will be replaced rather than abstracted when expanding to other clouds, because abstracting them prematurely adds complexity with no Phase 1 benefit:

- **AWS IAM / IRSA** — GCP uses Workload Identity Federation, Azure uses Workload Identity. These are configured at the cluster level and are isolated from application code.
- **AWS PrivateLink** — equivalent products exist on GCP (Private Service Connect) and Azure (Private Link). Network topology is per-cloud; the API endpoint URL is the only application-level concern.
- **AWS CloudWatch** (optional integration) — treated as an external log/metrics sink, not a core dependency.

### 2.5 Cloud Expansion Roadmap

| Phase | Cloud | Timeline |
|---|---|---|
| Phase 1 | AWS (`us-east-1`) | Months 1–9 |
| Phase 3 | AWS multi-region (`us-west-2`, `eu-west-1`) | Months 10–14 |
| Phase 4 | GCP (`us-central1`) | Months 18–24 |
| Phase 5 | Azure (`eastus`) | Months 24–30 |
| Phase 6 | Customer-hosted (any cloud or on-prem) | Private Cloud mode |

---

## 3. Product Vision & Deployment Tiers

### 3.1 Deployment Tier Matrix

| Feature | Public Cloud | Agent-Based | Remote Execution *(future)* | Private Cloud *(future)* |
|---|---|---|---|---|
| Airflow scheduling | FlowDeck AWS | Customer env | FlowDeck AWS | Customer env |
| Task execution | FlowDeck AWS | Customer env | Customer env | Customer env |
| DAG code | FlowDeck ECR/S3 | Customer repo | Customer repo | Customer repo |
| Metadata DB | RDS Aurora (FlowDeck) | Customer RDS/PG | RDS Aurora (FlowDeck) | Customer env |
| Secrets | AWS Secrets Manager (FlowDeck acct) | Customer Secrets Manager / Vault | Customer Secrets Manager / Vault | Customer env |
| Data access | Via Airflow connections | Direct inside customer VPC | Direct inside customer VPC | Direct |
| Managed by FlowDeck | Fully | Control plane only | Orchestration only | Platform layer only |
| Customer cloud required | No | Yes (any k8s) | Yes (any k8s) | Yes |
| AWS PrivateLink option | N/A | Optional | Recommended | N/A |
| Air-gap support | No | Partial | No | Yes |

### 3.2 North Star Architecture

```
┌────────────────────────────────────────────────────────────┐
│         FLOWDECK AWS ACCOUNT (us-east-1, Phase 1)          │
│                                                            │
│  ┌──────────────────────────────────────────────────────┐  │
│  │             FLOWDECK CONTROL PLANE                   │  │
│  │  EKS · ALB · Route53 · RDS Aurora · ECR · S3        │  │
│  │  API Server · UI · NATS · Prometheus · Loki          │  │
│  │  AWS Secrets Manager · ACM · WAF · CloudFront        │  │
│  └────────────────────────┬─────────────────────────────┘  │
└───────────────────────────┼────────────────────────────────┘
                            │ HTTPS/gRPC outbound from agent
        ┌───────────────────┼────────────────────────┐
        │                   │                        │
 ┌──────▼──────┐    ┌───────▼──────┐    ┌───────────▼────┐
 │ Data Plane  │    │  Data Plane  │    │   Data Plane   │
 │ (FlowDeck   │    │  (Customer   │    │   (Customer    │
 │  EKS ns)    │    │  EKS/k8s)    │    │  GKE/AKS/k8s) │
 └─────────────┘    └──────────────┘    └────────────────┘
 Public Cloud       Agent-Based AWS     Agent-Based GCP/Azure
                                        (works today — Phase 4
                                         adds GCP Control Plane)
```

---

## 4. Core Architecture Principles

### 4.1 Plane Separation
Control Plane and Data Plane are always logically separate, even in Public Cloud mode where they run in the same AWS account. Enforced at the API level from day one so relocating the Data Plane — to a customer's VPC, a different AWS account, or a different cloud — never requires rearchitecting the Control Plane.

### 4.2 Desired State Reconciliation
The Control Plane maintains a desired state for every Airflow deployment. The Commander reconciler continuously compares desired vs. actual state and applies changes via Helm. This loop is identical whether Commander runs co-located in FlowDeck's EKS or inside a customer's cluster via the Agent.

### 4.3 Outbound-Only Agent Communication
Any component running in a customer environment communicates exclusively via outbound HTTPS/gRPC. No inbound connections, no VPN tunnels, no security group rules opening inbound ports on the customer side.

### 4.4 Cloud-Agnostic Application Code
All application logic (API Server, Agent, Commander, Config Syncer, CLI) is pure Go with no AWS SDK calls in business logic. AWS SDK usage is confined to implementations of the `CloudProvider` interfaces defined in Section 6. This ensures application tests run without cloud credentials.

### 4.5 API-First Everything
Every platform capability is exposed via a versioned REST/gRPC API before being surfaced in the UI or CLI. The CLI and UI are consumers of the same API that customers use programmatically.

### 4.6 Airflow 3 as the Foundation
FlowDeck targets Apache Airflow 3.x. The Task Execution API (workers communicate via HTTP to the Airflow API Server, not directly to Postgres) is the key enabler for Remote Execution and clean agent-based architectures. Airflow 2.x is supported for Public Cloud and Agent-Based modes but is not eligible for Remote Execution.

---

## 5. AWS Infrastructure Foundation (Phase 1)

### 5.1 AWS Account Structure

```
FlowDeck AWS Organization
├── flowdeck-prod          (Production — Control Plane + Data Plane)
├── flowdeck-staging       (Staging environment)
├── flowdeck-dev           (Development environment)
└── flowdeck-security      (GuardDuty aggregator, Security Hub, CloudTrail)
```

All environments are separate AWS accounts within an AWS Organization. This provides hard billing isolation, IAM blast-radius containment, and Service Control Policy (SCP) enforcement at the org level.

### 5.2 Core AWS Services Map

| Logical Component | AWS Service | Configuration |
|---|---|---|
| Kubernetes (Control Plane) | **Amazon EKS** | Managed node groups, Bottlerocket AMI, 3 AZs |
| Kubernetes (Data Plane, Public Cloud mode) | **Amazon EKS** | Separate node groups per tenant tier |
| Platform Database | **Amazon RDS Aurora PostgreSQL** | Multi-AZ, PostgreSQL 15-compatible, `db.r7g.large` baseline |
| Airflow Deployment Databases | **Amazon RDS Aurora PostgreSQL** | Shared cluster, per-deployment schema (enterprise: dedicated instance) |
| Container Registry | **Amazon ECR** | Private, per-environment; image scanning enabled |
| Log Storage | **Amazon S3** | `flowdeck-logs-<env>`, lifecycle → Glacier after 90 days |
| DAG Bundle Storage | **Amazon S3** | `flowdeck-dag-bundles-<env>`, versioned |
| Secrets | **AWS Secrets Manager** | Rotation enabled; KMS CMK encryption |
| Encryption Keys | **AWS KMS** | Customer-managed CMK per environment |
| DNS | **Amazon Route53** | Public hosted zone `flowdeck.io`, private hosted zones per VPC |
| TLS Certificates | **AWS Certificate Manager (ACM)** | Wildcard cert `*.flowdeck.io`, auto-renewed |
| Load Balancer | **AWS ALB** | AWS Load Balancer Controller on EKS; WAF attached |
| WAF | **AWS WAF v2** | Rate limiting, OWASP ruleset, geo-blocking |
| CDN | **Amazon CloudFront** | UI static assets |
| Infrastructure as Code | **Terraform + Terragrunt** | All resources defined as code |
| Secrets Rotation | **AWS Secrets Manager rotation lambdas** | 30-day automatic rotation |
| Cost Management | **AWS Cost Explorer + Budgets** | Per-deployment cost tagging |
| Compliance | **AWS Config + Security Hub** | CIS Benchmark rules |
| Threat Detection | **Amazon GuardDuty** | Enabled org-wide |
| Audit Logging | **AWS CloudTrail** | All API calls, S3 + CloudWatch Logs sink |
| Worker Autoscaling | **KEDA on EKS** | Scales on SQS queue depth or custom metrics |

### 5.3 EKS Cluster Architecture

FlowDeck runs two EKS clusters in `us-east-1`, each spanning 3 Availability Zones:

**Control Plane Cluster (`flowdeck-control-<env>`):**
- Hosts: FlowDeck API Server, UI, NATS, Prometheus, Grafana, Loki, Commander (for Public Cloud mode)
- Node groups: `system` (t3.medium × 3), `api` (c6i.xlarge × 2–6, autoscaled), `observability` (r6i.large × 2)
- EKS add-ons: VPC CNI, CoreDNS, kube-proxy, EBS CSI Driver, EFS CSI Driver

**Data Plane Cluster (`flowdeck-data-<env>`)** *(Public Cloud mode only)*:
- Hosts: All customer Airflow deployments (one namespace per deployment)
- Node groups: `small` (t3.large), `medium` (c6i.xlarge), `large` (c6i.2xlarge), `xlarge` (c6i.4xlarge) — KEDA autoscaled
- Spot instances for worker node groups; On-Demand for scheduler nodes
- IRSA scoped per deployment namespace

### 5.4 VPC Architecture

```
FlowDeck Production VPC  (10.0.0.0/16)
│
├── Public Subnets (3 AZs)       10.0.0.0/20, 10.0.16.0/20, 10.0.32.0/20
│   └── ALB, NAT Gateways, CloudFront origin
│
├── Private Subnets — Apps (3 AZs)  10.0.64.0/18 split across AZs
│   └── EKS Node Groups, NATS
│
├── Private Subnets — DB (3 AZs)    10.0.192.0/20 split across AZs
│   └── RDS Aurora cluster
│
└── VPC Endpoints (no internet for AWS API calls)
    ├── com.amazonaws.us-east-1.ecr.api
    ├── com.amazonaws.us-east-1.ecr.dkr
    ├── com.amazonaws.us-east-1.s3
    ├── com.amazonaws.us-east-1.secretsmanager
    ├── com.amazonaws.us-east-1.kms
    └── com.amazonaws.us-east-1.logs
```

All AWS API calls from EKS pods use VPC endpoints — traffic never hits the public internet. NAT Gateways handle outbound internet for non-AWS traffic (e.g., pulling PyPI packages).

### 5.5 IAM Strategy — IRSA (IAM Roles for Service Accounts)

Every pod needing AWS API access uses IRSA. No static IAM keys anywhere.

```
flowdeck-api-server-role
  → secretsmanager:GetSecretValue (own secrets namespace only)
  → ecr:GetAuthorizationToken, BatchGetImage
  → s3:GetObject, PutObject (dag-bundles bucket only)

flowdeck-commander-role
  → ecr:GetAuthorizationToken
  → s3:GetObject (dag-bundles)

flowdeck-airflow-worker-role (per deployment, scoped by namespace)
  → secretsmanager:GetSecretValue (deployment secret path only: /flowdeck/dep/<id>/*)
  → s3:GetObject, PutObject (deployment log prefix only: logs/<org>/<dep>/*)
  → (customer-specific permissions added per deployment via role chaining)
```

### 5.6 RDS Aurora Configuration

| Setting | Value |
|---|---|
| Engine | Aurora PostgreSQL 15 (PostgreSQL wire-compatible) |
| Cluster type | Multi-AZ (1 writer + 2 readers) |
| Instance class | `db.r7g.large` writer (Graviton3), `db.r7g.medium` readers |
| Storage | Aurora auto-scaling (10 GB → 128 TB) |
| Encryption | KMS CMK per environment |
| Backup retention | 7 days automated PITR; S3 export monthly |
| Connection pooling | PgBouncer sidecar on API Server pods (transaction mode) |
| Monitoring | Enhanced Monitoring + Performance Insights |

---

## 6. Cloud Provider Abstraction Layer

This section documents the Go interface layer that decouples application logic from AWS-specific APIs. All interfaces are defined and enforced in Phase 1. Only the AWS implementation is built in Phase 1. GCP and Azure implementations follow in Phase 4 and Phase 5.

### 6.1 Interface Definitions

```go
package cloud

// SecretsProvider abstracts secret read/write.
// AWS: Secrets Manager. GCP: Secret Manager. Azure: Key Vault.
type SecretsProvider interface {
    GetSecret(ctx context.Context, name string) (string, error)
    PutSecret(ctx context.Context, name, value string) error
    DeleteSecret(ctx context.Context, name string) error
    RotateSecret(ctx context.Context, name string) error
}

// StorageProvider abstracts object storage.
// AWS: S3. GCP: GCS. Azure: Azure Blob Storage.
type StorageProvider interface {
    PutObject(ctx context.Context, bucket, key string, body io.Reader) error
    GetObject(ctx context.Context, bucket, key string) (io.ReadCloser, error)
    DeleteObject(ctx context.Context, bucket, key string) error
    GeneratePresignedURL(ctx context.Context, bucket, key string, ttl time.Duration) (string, error)
}

// RegistryProvider abstracts container image registry auth.
// AWS: ECR. GCP: Artifact Registry. Azure: ACR.
type RegistryProvider interface {
    GetAuthToken(ctx context.Context) (username, password string, expiry time.Time, err error)
    EnsureRepositoryExists(ctx context.Context, name string) error
    GetImageURI(repository, tag string) string
}

// DNSProvider abstracts DNS record management.
// AWS: Route53. GCP: Cloud DNS. Azure: Azure DNS.
type DNSProvider interface {
    UpsertRecord(ctx context.Context, zone, name, recordType, value string, ttl int) error
    DeleteRecord(ctx context.Context, zone, name, recordType string) error
}

// NetworkProvider abstracts private connectivity.
// AWS: PrivateLink. GCP: Private Service Connect. Azure: Private Link.
type NetworkProvider interface {
    CreatePrivateEndpoint(ctx context.Context, req CreatePrivateEndpointRequest) (*PrivateEndpoint, error)
    DeletePrivateEndpoint(ctx context.Context, id string) error
    ListPrivateEndpoints(ctx context.Context, filter PrivateEndpointFilter) ([]*PrivateEndpoint, error)
}
```

### 6.2 Provider Registration

Selected at startup via `FLOWDECK_CLOUD_PROVIDER` env var and injected via dependency container:

```go
func NewCloudProvider(cfg Config) (CloudProvider, error) {
    switch cfg.Provider {
    case "aws":
        return aws.NewProvider(cfg.AWS)     // Phase 1: built
    case "gcp":
        return gcp.NewProvider(cfg.GCP)     // Phase 4: built
    case "azure":
        return azure.NewProvider(cfg.Azure) // Phase 5: built
    default:
        return nil, fmt.Errorf("unsupported provider: %s", cfg.Provider)
    }
}
```

### 6.3 Code Conventions (Enforced in Code Review)

- No AWS SDK imports (`github.com/aws/aws-sdk-go-v2/...`) outside of `internal/cloud/aws/`
- No GCP SDK imports outside of `internal/cloud/gcp/`
- No Azure SDK imports outside of `internal/cloud/azure/`
- All cross-cutting concerns accessed only via the `CloudProvider` interface
- Integration tests run with a `mock` provider — no real cloud credentials needed in CI

---

## 7. System Components

| Component | Tier | Purpose | AWS Service | Technology |
|---|---|---|---|---|
| FlowDeck API Server | Control Plane | Central management API | EKS pod | Go / gRPC + REST |
| FlowDeck UI | Control Plane | Web dashboard | EKS pod + CloudFront | React / Next.js |
| FlowDeck CLI (`flowdeck`) | Client | Developer & operator tooling | GitHub Releases / Homebrew | Go (single binary) |
| Commander | Data Plane | Desired-state reconciler | EKS pod (or agent) | Go |
| Config Syncer | Data Plane | Secret & config mirroring | EKS pod (or agent) | Go |
| Agent | Customer env | Embeds Commander + Syncer | Customer k8s (Helm) | Go single binary |
| Airflow Runtime | Data Plane | Apache Airflow 3.x | EKS namespace | Python / Helm |
| Metadata DB | Data Plane | Airflow state | RDS Aurora PostgreSQL | PostgreSQL 15 |
| Platform DB | Control Plane | FlowDeck platform state | RDS Aurora PostgreSQL | PostgreSQL 15 |
| NATS JetStream | Control Plane | Event bus | EKS StatefulSet | NATS 2.x |
| Prometheus | Both | Metrics collection | EKS StatefulSet | Prometheus |
| Grafana | Control Plane | Dashboards | EKS pod | Grafana |
| Loki | Control Plane | Log aggregation | EKS pod + S3 backend | Loki |
| Vector | Both | Log forwarding | EKS DaemonSet | Vector |
| PgBouncer | Control Plane | DB connection pooling | EKS sidecar | PgBouncer |
| KEDA | Data Plane | Worker autoscaling | EKS add-on | KEDA |
| Sentinel | Agent | Agent health monitoring | Customer k8s sidecar | Go |

---

## 8. Deployment Mode 1 — Public Cloud (Fully Managed on AWS)

### 8.1 Overview

FlowDeck manages everything. The customer interacts only with the UI, CLI, and Airflow itself. All infrastructure runs in FlowDeck's AWS account (`flowdeck-prod`), region `us-east-1`.

### 8.2 Architecture

```
FlowDeck AWS Account — us-east-1
│
├── flowdeck-control EKS cluster
│   ├── namespace: flowdeck-system
│   │   ├── api-server (Deployment, 3 replicas → ALB → api.flowdeck.io)
│   │   ├── ui (Deployment, 2 replicas → CloudFront → app.flowdeck.io)
│   │   ├── nats (StatefulSet, 3 replicas, internal only)
│   │   ├── prometheus (StatefulSet, EBS gp3 volume)
│   │   ├── grafana (Deployment, EBS volume)
│   │   ├── loki (StatefulSet, S3 chunk store)
│   │   └── pgbouncer (sidecar on api-server pods)
│   └── namespace: flowdeck-commander
│       └── commander (Deployment, watches flowdeck-data EKS cluster)
│
├── flowdeck-data EKS cluster
│   └── namespace: flowdeck-dep-<id>   (one per customer Deployment)
│       ├── airflow-scheduler
│       ├── airflow-workers (KEDA ScaledObject, Spot instances)
│       ├── airflow-api-server
│       ├── airflow-triggerer
│       ├── airflow-dag-processor
│       ├── vector (sidecar → Loki)
│       └── config-syncer
│
├── RDS Aurora Cluster
│   ├── flowdeck_platform DB        (API Server state)
│   └── flowdeck_dep_<id> schema   (per Airflow Deployment)
│
├── ECR
│   ├── flowdeck/airflow-runtime:<version>
│   └── <org>/dag-bundles:<git-sha>
│
├── S3
│   ├── flowdeck-dag-bundles-prod   (DAG bundle ZIPs / images)
│   ├── flowdeck-logs-prod          (task logs, Vector sink)
│   └── flowdeck-loki-prod          (Loki chunk store)
│
├── AWS Secrets Manager
│   ├── /flowdeck/platform/db-password
│   ├── /flowdeck/platform/nats-password
│   └── /flowdeck/dep/<id>/connections/<conn-id>
│
└── Route53 + ACM
    ├── api.flowdeck.io             → ALB (API Server)
    ├── app.flowdeck.io             → CloudFront → ALB (UI)
    └── <dep-id>.airflow.flowdeck.io → ALB (per Airflow UI)
```

### 8.3 Deployment Lifecycle

1. Customer calls `flowdeck deployment create` or uses the UI.
2. API Server validates, writes desired state to RDS Aurora, publishes `deployments.desired` event to NATS.
3. Commander picks up the event from NATS and runs `helm install flowdeck-airflow` in a new namespace on the data EKS cluster.
4. Config Syncer reads deployment config from the API Server and populates: ECR pull secret, Airflow connections (from Secrets Manager via IRSA), environment variables.
5. KEDA `ScaledObject` is created targeting the deployment's worker queue depth.
6. Airflow pods start; DAG Processor serializes DAGs from the customer's bundle.
7. Commander reports actual state back via NATS `deployments.status`.
8. Route53 CNAME `<dep-id>.airflow.flowdeck.io` is created pointing to the data ALB.
9. Customer sees "Running" in the FlowDeck UI within ~90 seconds.

### 8.4 DAG Deployment Flow

```
flowdeck deploy <deployment-id>
    │
    ├── flowdeck dev parse       (validate DAGs locally, fail fast)
    │
    ├── docker build → push to ECR (flowdeck/<org>/<dep>:<git-sha>)
    │
    ├── POST /v1/deploy/<dep-id>  { image: "...", sha: "..." }
    │
    ├── API Server: write deploy record → NATS event
    │
    ├── Commander: helm upgrade (updates Airflow image tag)
    │
    ├── Kubernetes rolling update (Scheduler + Workers restart)
    │
    └── DAG Processor: parses new DAGs → serializes to Metadata DB
```

### 8.5 Resource Isolation per Deployment

| Resource | Mechanism | AWS Implementation |
|---|---|---|
| Compute | Kubernetes namespace + ResourceQuota + LimitRange | EKS namespaced quotas |
| Network | NetworkPolicy: deny all cross-namespace traffic | EKS + VPC CNI |
| Database | Separate Postgres schema (enterprise: separate Aurora instance) | RDS Aurora schema isolation |
| Secrets | Secrets Manager path-scoped per deployment | IRSA condition on `secretsmanager:SecretId` prefix |
| Logs | S3 prefix-scoped | `logs/<org-id>/<dep-id>/` prefix |
| Container images | ECR repository per organization | ECR resource policy |
| IAM | IRSA role per deployment namespace | `sts:AssumeRoleWithWebIdentity` scoped to namespace SA |

### 8.6 Worker Autoscaling (KEDA + SQS)

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata:
  name: airflow-worker-<dep-id>
spec:
  scaleTargetRef:
    name: airflow-worker
  minReplicaCount: 1
  maxReplicaCount: 20
  triggers:
    - type: aws-sqs-queue
      metadata:
        queueURL: https://sqs.us-east-1.amazonaws.com/<acct>/<dep-id>-celery
        awsRegion: us-east-1
        targetQueueLength: "5"
```

Spot instance node groups handle worker nodes (cost savings ~70%). On-Demand for scheduler and triggerer nodes (no interruption tolerance).

---

## 9. Deployment Mode 2 — Agent-Based Managed Airflow

### 9.1 Overview

Customer installs the FlowDeck Agent Helm chart into their own Kubernetes cluster. FlowDeck manages Airflow via an outbound-only agent connection to the FlowDeck Control Plane on AWS. Works with any CNCF-conformant Kubernetes: **Amazon EKS**, **Google GKE**, **Azure AKS**, **Rancher**, **OpenShift**, or self-hosted k8s 1.27+. The agent has zero AWS SDK dependencies.

### 9.2 Architecture

```
FlowDeck AWS Account (us-east-1)         Customer's Environment
                                          (EKS / GKE / AKS / any k8s)
┌─────────────────────────┐              ┌──────────────────────────────────┐
│   FlowDeck Control Plane │              │  namespace: flowdeck-system      │
│                         │              │  ┌──────────────────────────────┐ │
│  API Server             │◄─ HTTPS ────┤  │     FlowDeck Agent           │ │
│  NATS JetStream         │   outbound   │  │  Commander                   │ │
│  RDS Aurora             │   only       │  │  Config Syncer               │ │
│  UI / Grafana / Loki    │              │  │  Heartbeat Reporter          │ │
│                         │              │  │  Sentinel (sidecar)          │ │
└─────────────────────────┘              │  └──────────────────────────────┘ │
                                         │                                    │
                                         │  namespace: flowdeck-dep-<id>     │
                                         │  ┌──────────────────────────────┐ │
                                         │  │  Airflow Scheduler           │ │
                                         │  │  Airflow Workers             │ │
                                         │  │  Airflow API Server          │ │
                                         │  │  Airflow Triggerer           │ │
                                         │  │  Airflow Postgres (customer) │ │
                                         │  │  DAG code (Git/S3/local)     │ │
                                         │  │  Secrets (customer-managed)  │ │
                                         │  └──────────────────────────────┘ │
                                         └──────────────────────────────────┘
```

### 9.3 Agent Installation

```bash
helm repo add flowdeck https://charts.flowdeck.io
helm repo update

helm install flowdeck-agent flowdeck/agent \
  --namespace flowdeck-system \
  --create-namespace \
  --set agent.token=<cluster-token-from-ui> \
  --set agent.controlPlaneUrl=https://api.flowdeck.io \
  --set agent.clusterName="my-prod-eks"
```

For air-gap or private registry (e.g. ECR mirror):

```bash
# Mirror to private ECR
docker pull public.ecr.aws/flowdeck/agent:latest
docker tag ... 123456789.dkr.ecr.us-east-1.amazonaws.com/flowdeck-agent:latest
docker push ...

helm install flowdeck-agent flowdeck/agent \
  --set image.repository=123456789.dkr.ecr.us-east-1.amazonaws.com/flowdeck-agent \
  --set agent.token=<token> \
  --set agent.controlPlaneUrl=https://api.flowdeck.io
```

### 9.4 Secrets Backend Options (Agent Mode)

Config Syncer supports pluggable backends so customers use their own secrets infrastructure:

| Backend | AWS | GCP | Azure | On-Prem |
|---|---|---|---|---|
| **AWS Secrets Manager** | ✓ Native (IRSA) | Via cross-account | N/A | N/A |
| **HashiCorp Vault** | ✓ | ✓ | ✓ | ✓ |
| **GCP Secret Manager** | N/A | ✓ Native (Workload Identity) | N/A | N/A |
| **Azure Key Vault** | N/A | N/A | ✓ Native (Workload Identity) | N/A |
| **Kubernetes Secrets** | ✓ (dev only) | ✓ (dev only) | ✓ (dev only) | ✓ (dev only) |
| **External Secrets Operator** | ✓ | ✓ | ✓ | ✓ |

```yaml
# Agent Helm values — AWS Secrets Manager
secretBackend:
  type: aws-secrets-manager
  aws:
    region: us-east-1
    pathPrefix: /mycompany/airflow/
    irsaRoleArn: arn:aws:iam::123456789:role/flowdeck-agent-secrets-role
```

### 9.5 Database Options (Agent Mode)

| Option | Notes |
|---|---|
| **Customer's Amazon RDS** | Recommended for AWS customers. IRSA access. |
| **Customer's Cloud SQL (GCP)** | Recommended for GCP customers. Workload Identity. |
| **Customer's Azure Database for PG** | Recommended for Azure customers. |
| **Customer's self-hosted Postgres** | Any Postgres 13+ accessible from the cluster. |
| **FlowDeck-provisioned (Helm)** | Ephemeral Postgres via chart. Dev/test only. |

### 9.6 Agent RBAC Scope

```yaml
rules:
  - apiGroups: [""]
    resources: ["namespaces"]
    verbs: ["get", "list", "watch", "create", "delete"]
    # Only namespaces with label: flowdeck.io/managed: "true"

  - apiGroups: ["", "apps", "batch"]
    resources: ["pods", "deployments", "statefulsets", "services",
                "configmaps", "secrets", "persistentvolumeclaims", "jobs"]
    verbs: ["*"]
    # Only within managed namespaces

  - apiGroups: [""]
    resources: ["nodes"]
    verbs: ["get", "list"]    # Resource reporting only
```

No access to existing customer namespaces, secrets, cluster-scoped resources beyond managed namespaces, or any existing customer workloads.

---

## 10. Deployment Mode 3 — FlowDeck CLI

The `flowdeck` CLI is a single compiled Go binary — the primary interface for developers, operators, and CI/CD pipelines. Full parity with the Astronomer `astro` CLI plus FlowDeck-specific extensions for cluster and agent management.

### 10.1 Distribution

| Channel | Command |
|---|---|
| Homebrew (macOS/Linux) | `brew install flowdeck/tap/flowdeck` |
| Scoop (Windows) | `scoop install flowdeck` |
| winget (Windows) | `winget install FlowDeck.CLI` |
| apt (Debian/Ubuntu) | `apt install flowdeck` (FlowDeck apt repo) |
| rpm (RHEL/Amazon Linux) | `yum install flowdeck` (FlowDeck rpm repo) |
| Direct binary | `https://releases.flowdeck.io/cli/latest/` |
| Docker | `docker run flowdeckio/cli:latest` |
| GitHub Action | `uses: flowdeck/setup-cli@v1` |
| **AWS CodeBuild** | `public.ecr.aws/flowdeck/cli:latest` (pre-built image) |

### 10.2 Global Flags

```
-h, --help                    Show help
    --verbosity <string>      debug | info | warn | error | fatal | panic
-g, --global                  Apply config globally
    --output <string>         table | json | yaml  (default: table)
    --workspace-id <string>   Target workspace
    --token <string>          API token for non-interactive / CI use
    --region <string>         FlowDeck region (default: us-east-1)
```

---

### 10.3 `flowdeck login` / `flowdeck logout`

```bash
flowdeck login                          # Browser-based OAuth2 / SSO
flowdeck login --token <api-token>      # CI/CD non-interactive
flowdeck login <domain>                 # Specific installation (Private Cloud)
flowdeck logout
flowdeck logout <domain>
```

---

### 10.4 `flowdeck auth`

```bash
flowdeck auth login
flowdeck auth login --token <token>
flowdeck auth logout
```

---

### 10.5 `flowdeck config`

```bash
flowdeck config get <setting>
flowdeck config set <setting> <value>
flowdeck config --global get <setting>
flowdeck config --global set <setting> <value>
```

**Configurable settings:**

| Setting | Description | Default |
|---|---|---|
| `project.name` | Current project name | Directory name |
| `context` | Active FlowDeck domain | `flowdeck.io` |
| `show_warnings` | Show CLI warnings | `true` |
| `skip_parse` | Skip DAG parse on deploy | `false` |
| `auto_select_workspace` | Skip workspace selection prompt | `false` |
| `container_runtime` | `docker` or `podman` | Auto-detected |
| `webserver_port` | Local Airflow UI port | `8080` |
| `postgres_port` | Local Postgres port | `5432` |
| `duplicate_volumes` | Duplicate Docker volumes on restart | `false` |
| `cloud_provider` | For Private Cloud: `aws`, `gcp`, `azure` | `aws` |

---

### 10.6 `flowdeck context`

```bash
flowdeck context list
flowdeck context switch <domain>
flowdeck context delete <domain>
```

---

### 10.7 `flowdeck version`

```bash
flowdeck version
```

---

### 10.8 `flowdeck completion`

```bash
flowdeck completion bash
flowdeck completion zsh
flowdeck completion fish
flowdeck completion powershell
```

---

### 10.9 `flowdeck dev` — Local Development

Run Airflow locally via Docker or Podman. No AWS credentials required for local dev.

#### Initialization

```bash
flowdeck dev init
flowdeck dev init --airflow-version 3.0.2
flowdeck dev init --from-template <template>
#   Templates: etl | dbt-on-flowdeck | generative-ai | ml-pipeline | learning-airflow
flowdeck dev init --name <project-name>
```

**Generated project structure:**

```
my-project/
├── dags/
│   └── example_dag.py
├── plugins/
├── include/
├── tests/
│   └── dags/
│       └── test_dag_example.py
├── Dockerfile              # FROM public.ecr.aws/flowdeck/airflow:<version>
├── requirements.txt
├── packages.txt
├── airflow_settings.yaml
└── .flowdeck/
    └── config.yaml
```

#### Running the Environment

```bash
flowdeck dev start
flowdeck dev start --no-browser
flowdeck dev start --wait
flowdeck dev start --env-file <path>
flowdeck dev stop
flowdeck dev kill
flowdeck dev restart
flowdeck dev restart --no-cache
flowdeck dev ps
```

#### Testing & Validation

```bash
flowdeck dev parse
flowdeck dev pytest
flowdeck dev pytest <test-path>
flowdeck dev pytest --args "<pytest-flags>"
flowdeck dev upgrade-test
flowdeck dev upgrade-test --version <version>
```

#### Logs

```bash
flowdeck dev logs
flowdeck dev logs --scheduler
flowdeck dev logs --webserver
flowdeck dev logs --triggerer
flowdeck dev logs --follow
```

#### Executing Commands Inside Local Containers

```bash
flowdeck dev run <airflow-command>
flowdeck dev bash
flowdeck dev airflow <command>
```

#### Object Import / Export

```bash
flowdeck dev object import
flowdeck dev object export
```

---

### 10.10 `flowdeck deploy`

Build and push DAG bundle to ECR, then trigger a rolling deploy. Automatically runs `flowdeck dev parse` to prevent broken DAGs reaching production.

```bash
flowdeck deploy
flowdeck deploy <deployment-id>
flowdeck deploy --dags-only
flowdeck deploy --image-name <ecr-uri>
flowdeck deploy --wait
flowdeck deploy --wait-time <seconds>
flowdeck deploy --skip-parse
flowdeck deploy --force
flowdeck deploy --description "<text>"
flowdeck deploy --save
flowdeck deploy --mount-dags
flowdeck deploy --type image | dags | image-and-dags
```

---

### 10.11 `flowdeck run`

```bash
flowdeck run <dag-id>
flowdeck run <dag-id> --deployment-id <id>
flowdeck run <dag-id> --conf '{"key":"val"}'
```

---

### 10.12 `flowdeck deployment`

#### Core Lifecycle

```bash
flowdeck deployment list
flowdeck deployment list --workspace-id <id>
flowdeck deployment list --output json

flowdeck deployment create
flowdeck deployment create \
  --name <name> \
  --workspace-id <id> \
  --cluster-id <id> \
  --airflow-version <version> \
  --executor CeleryExecutor|KubernetesExecutor|LocalExecutor \
  --cloud-provider aws|gcp|azure \
  --region us-east-1 \
  --dag-deploy-enabled \
  --high-availability \
  --development-mode \
  --description "<text>" \
  --workload-identity <arn> \
  --wait

flowdeck deployment delete <deployment-id>
flowdeck deployment delete <deployment-id> --force

flowdeck deployment update <deployment-id>
flowdeck deployment update <deployment-id> \
  --name <name> \
  --airflow-version <version> \
  --executor <executor> \
  --high-availability \
  --workload-identity <arn>

flowdeck deployment inspect <deployment-id>
flowdeck deployment inspect <deployment-id> --key <field>
flowdeck deployment inspect <deployment-id> --show-workload-identity
flowdeck deployment inspect <deployment-id> --output yaml
```

#### Logs

```bash
flowdeck deployment logs <deployment-id>
flowdeck deployment logs <deployment-id> --scheduler
flowdeck deployment logs <deployment-id> --webserver
flowdeck deployment logs <deployment-id> --triggerer
flowdeck deployment logs <deployment-id> --follow
```

#### Hibernate & Wake-up

```bash
flowdeck deployment hibernate <deployment-id>
flowdeck deployment hibernate <deployment-id> --override-until <datetime>
flowdeck deployment wake-up <deployment-id>
flowdeck deployment wake-up <deployment-id> --override-until <datetime>
```

#### Airflow / Runtime Upgrade

```bash
flowdeck deployment airflow upgrade \
  --deployment-id <id> \
  --desired-airflow-version <version>

flowdeck deployment runtime upgrade \
  --deployment-id <id> \
  --desired-runtime-version <version>

flowdeck deployment runtime migrate \
  --deployment-id <id>
```

#### Service Accounts

```bash
flowdeck deployment service-account create \
  --deployment-id <id> --label "<label>" \
  --role DEPLOYMENT_VIEWER|DEPLOYMENT_EDITOR|DEPLOYMENT_ADMIN
flowdeck deployment service-account list --deployment-id <id>
flowdeck deployment service-account delete --deployment-id <id> --service-account-id <id>
```

#### Tokens

```bash
flowdeck deployment token list --deployment-id <id>
flowdeck deployment token create --deployment-id <id> --name "<name>" --role <role> --expiration <days>
flowdeck deployment token update --deployment-id <id> --token-id <id> --name "<name>"
flowdeck deployment token delete --deployment-id <id> --token-id <id>
flowdeck deployment token rotate --deployment-id <id> --token-id <id>
```

#### Users

```bash
flowdeck deployment user list --deployment-id <id>
flowdeck deployment user add --deployment-id <id> --email <email> --role <role>
flowdeck deployment user remove --deployment-id <id> --email <email>
flowdeck deployment user update --deployment-id <id> --email <email> --role <role>
```

#### Teams

```bash
flowdeck deployment team list --deployment-id <id>
flowdeck deployment team add --deployment-id <id> --team-id <id> --role <role>
flowdeck deployment team remove --deployment-id <id> --team-id <id>
flowdeck deployment team update --deployment-id <id> --team-id <id> --role <role>
```

---

### 10.13 `flowdeck deployment airflow-variable`

```bash
flowdeck deployment airflow-variable list --deployment-id <id>
flowdeck deployment airflow-variable create \
  --deployment-id <id> --variable-key <k> --variable-value <v> --description "<text>"
flowdeck deployment airflow-variable update \
  --deployment-id <id> --variable-key <k> --variable-value <v>
flowdeck deployment airflow-variable copy \
  --source-deployment-id <id> --target-deployment-id <id>
```

---

### 10.14 `flowdeck deployment connection`

```bash
flowdeck deployment connection list --deployment-id <id>
flowdeck deployment connection list --deployment-id <id> --output json
flowdeck deployment connection create \
  --deployment-id <id> --conn-id <id> --conn-type <type> \
  --host <host> --port <port> --login <user> --password <pw> \
  --schema <schema> --extra '<json>'
flowdeck deployment connection update --deployment-id <id> --conn-id <id> [flags]
flowdeck deployment connection copy \
  --source-deployment-id <id> --target-deployment-id <id>
```

---

### 10.15 `flowdeck deployment pool`

```bash
flowdeck deployment pool list --deployment-id <id>
flowdeck deployment pool create \
  --deployment-id <id> --name <name> --slots <int> --description "<text>"
flowdeck deployment pool update --deployment-id <id> --name <name> --slots <int>
flowdeck deployment pool copy \
  --source-deployment-id <id> --target-deployment-id <id>
```

---

### 10.16 `flowdeck deployment variable`

```bash
flowdeck deployment variable list --deployment-id <id>
flowdeck deployment variable create --deployment-id <id> --key <KEY> --value <value> --secret
flowdeck deployment variable update --deployment-id <id> --key <KEY> --value <value>
```

---

### 10.17 `flowdeck deployment worker-queue`

```bash
flowdeck deployment worker-queue list --deployment-id <id>
flowdeck deployment worker-queue create \
  --deployment-id <id> --name <name> \
  --worker-concurrency <int> --min-worker-count <int> --max-worker-count <int> \
  --worker-cpu <millicores> --worker-memory <GB> --node-pool-id <id>
flowdeck deployment worker-queue update --deployment-id <id> --name <name> [flags]
flowdeck deployment worker-queue delete --deployment-id <id> --name <name>
```

---

### 10.18 `flowdeck workspace`

```bash
flowdeck workspace list
flowdeck workspace create --name "<name>" --description "<text>" --enforce-cicd
flowdeck workspace update --workspace-id <id> --name "<name>"
flowdeck workspace delete --workspace-id <id>
flowdeck workspace switch <workspace-id-or-name>

flowdeck workspace user list
flowdeck workspace user add --email <email> --role <role>
flowdeck workspace user remove --email <email>
flowdeck workspace user update --email <email> --role <role>

flowdeck workspace team list
flowdeck workspace team add --team-id <id> --role <role>
flowdeck workspace team remove --team-id <id>
flowdeck workspace team update --team-id <id> --role <role>

flowdeck workspace token list
flowdeck workspace token create --name "<name>" --role <role> --expiration <days>
flowdeck workspace token update --token-id <id> --name "<name>"
flowdeck workspace token delete --token-id <id>
flowdeck workspace token rotate --token-id <id>
```

---

### 10.19 `flowdeck organization`

```bash
flowdeck organization list
flowdeck organization switch <org-id-or-name>

flowdeck organization user list
flowdeck organization user invite --email <email> --role <role>
flowdeck organization user remove --email <email>
flowdeck organization user update --email <email> --role <role>

flowdeck organization team list
flowdeck organization team create --name "<name>" --description "<text>"
flowdeck organization team get --team-id <id>
flowdeck organization team update --team-id <id> --name "<name>"
flowdeck organization team delete --team-id <id>
flowdeck organization team user add --team-id <id> --email <email>
flowdeck organization team user remove --team-id <id> --email <email>
flowdeck organization team user list --team-id <id>

flowdeck organization token list
flowdeck organization token create --name "<name>" --role <role> --expiration <days>
flowdeck organization token update --token-id <id> --name "<name>"
flowdeck organization token delete --token-id <id>
flowdeck organization token rotate --token-id <id>
```

---

### 10.20 `flowdeck team`

```bash
flowdeck team list
flowdeck team get --team-id <id>
```

---

### 10.21 `flowdeck user`

```bash
flowdeck user create --email <email> --role <role>
```

---

### 10.22 `flowdeck cluster`

```bash
flowdeck cluster list
flowdeck cluster create
flowdeck cluster create --name "<name>" --cloud-provider aws|gcp|azure|other
flowdeck cluster inspect <cluster-id>
flowdeck cluster delete <cluster-id>
flowdeck cluster token rotate <cluster-id>
flowdeck cluster health <cluster-id>
```

---

### 10.23 `flowdeck remote`

Manage Remote Execution Agents *(available when Remote Execution is enabled on a Deployment — future)*.

```bash
flowdeck remote list --deployment-id <id>
flowdeck remote inspect <agent-id>
flowdeck remote delete <agent-id>
flowdeck remote cordon <agent-id>
flowdeck remote uncordon <agent-id>
flowdeck remote token create --deployment-id <id>
flowdeck remote token list --deployment-id <id>
flowdeck remote token delete --token-id <id>
```

---

### 10.24 `flowdeck dbt`

```bash
flowdeck dbt deploy --deployment-id <id> --dbt-project-path <path> --wait
flowdeck dbt deploy --deployment-id <id> --wait-time <seconds>
```

---

### 10.25 `flowdeck api`

```bash
flowdeck api GET /v1/deployments
flowdeck api POST /v1/deployments --body '{"name":"prod"}'
```

---

### 10.26 `flowdeck ide`

```bash
flowdeck ide init
flowdeck ide start
```

---

### 10.27 `flowdeck telemetry`

```bash
flowdeck telemetry enable
flowdeck telemetry disable
```

---

### 10.28 CI/CD Integration

```yaml
# GitHub Actions
- name: Deploy to FlowDeck
  uses: flowdeck/deploy-action@v1
  with:
    token: ${{ secrets.FLOWDECK_API_TOKEN }}
    deployment-id: ${{ vars.DEPLOYMENT_ID }}
    wait: true

# GitLab CI
deploy-airflow:
  image: public.ecr.aws/flowdeck/cli:latest
  script:
    - flowdeck deploy $DEPLOYMENT_ID --wait --token $FLOWDECK_TOKEN

# AWS CodeBuild (buildspec.yml)
phases:
  install:
    commands:
      - curl -sSL https://releases.flowdeck.io/cli/install.sh | bash
  build:
    commands:
      - flowdeck deploy $DEPLOYMENT_ID --wait --token $FLOWDECK_TOKEN
```

---

## 11. Control Plane Deep Dive

### 11.1 FlowDeck API Server

Stateless Go service deployed on EKS as a `Deployment` with 3 replicas behind AWS ALB. ALB terminates TLS via ACM wildcard certificate. HTTP/2 enabled end-to-end.

**Exposes:**
- REST API (v1) at `https://api.flowdeck.io/v1`
- gRPC API at `https://grpc.flowdeck.io` (agent communication)
- Webhook endpoints at `https://api.flowdeck.io/webhooks` (GitHub, GitLab, Bitbucket push → auto-deploy)

**Scaling:** Stateless; HPA scales 3–10 replicas based on CPU and request rate.

### 11.2 NATS JetStream

3-node StatefulSet on EKS using EBS gp3 volumes for persistence.

| Stream | Retention | Publisher | Consumer |
|---|---|---|---|
| `deployments.desired` | Work-queue (ack-then-delete) | API Server | Commander |
| `deployments.status` | Limits (last 100 per subject) | Commander | API Server |
| `agents.heartbeat` | Limits (last 1 per agent) | Agent | API Server |
| `alerts.platform` | Work-queue | Alertmanager | API Server |

### 11.3 Platform Database

API Server connects via PgBouncer sidecar (transaction mode, 100 server connections per pod). Aurora read replicas handle read-heavy queries. All credentials fetched from AWS Secrets Manager via IRSA at startup.

---

## 12. Data Plane Deep Dive

### 12.1 Airflow Runtime Helm Chart

`flowdeck-airflow` wraps the official Airflow Helm chart with:

- Vector sidecar (ships logs to Loki or customer's S3 via pre-signed URL)
- FlowDeck secrets init container (pulls from Secrets Manager or customer backend)
- KEDA `ScaledObject` for worker autoscaling (SQS or custom metrics)
- Prometheus `ServiceMonitor` for metrics scraping
- AWS ALB `Ingress` annotations for Airflow UI
- IRSA annotation on worker ServiceAccount

### 12.2 Commander (Reconciler)

```
loop every 10s:
  desired = GET /v1/clusters/{id}/deployments
  actual  = list Helm releases in cluster
  diff    = compare(desired, actual)

  for each change:
    CREATE → helm install flowdeck-airflow --namespace flowdeck-dep-<id>
    UPDATE → helm upgrade --atomic (rollback on failure)
    DELETE → helm uninstall + kubectl delete namespace

  report_status(actual) → POST /v1/clusters/{id}/status
```

In Public Cloud mode, Commander runs in the `flowdeck-commander` namespace with a kubeconfig targeting the data EKS cluster. In Agent-Based mode, it uses the in-cluster kubeconfig inside the customer's cluster. Code is identical — only deployment target differs.

### 12.3 Config Syncer (AWS Public Cloud mode)

1. Fetches deployment config from FlowDeck API Server
2. Calls AWS Secrets Manager (`GetSecretValue`) via IRSA
3. Creates/updates Kubernetes secrets in the deployment namespace
4. Rotates ECR pull tokens (ECR tokens expire every 12 hours)

---

## 13. Agent Architecture Deep Dive

### 13.1 Token Rotation

Agent tokens are JWTs (24h). Auto-rotation 1 hour before expiry:

1. Agent sends current token to `POST /v1/clusters/{id}/token/rotate`
2. API Server issues new 24h token (signing key stored in AWS Secrets Manager)
3. Agent persists new token to a Kubernetes secret in `flowdeck-system`
4. Old token invalidated immediately

### 13.2 Sentinel (Health Monitoring)

Sidecar container (available from agent v1.2.0+):
- Monitors the agent main process; restarts on crash
- Watches Airflow pod health in managed namespaces
- Sends enriched telemetry (pod restart counts, OOM events, pending pods) to the API Server
- On AWS EKS: optionally emits CloudWatch metrics if the customer grants `cloudwatch:PutMetricData`

### 13.3 Agent Upgrade Policy

```yaml
agent:
  upgradePolicy: auto         # auto | pin | semver-minor
  pinnedVersion: "1.5.2"     # used when upgradePolicy: pin
```

When the API Server signals a newer version, Commander runs `helm upgrade flowdeck-agent` in `flowdeck-system`. Rolling update replaces pods gracefully.

---

## 14. Networking Architecture on AWS

### 14.1 FlowDeck Control Plane Network

```
Internet
  │
  ├── AWS WAF v2 (OWASP rules, rate limiting 1000 req/min/IP)
  ├── Amazon CloudFront ──── app.flowdeck.io (UI + static assets)
  ├── AWS ALB (flowdeck-control-alb)
  │   ├── api.flowdeck.io        → api-server pods (8080)
  │   ├── grpc.flowdeck.io       → api-server pods (9090, HTTP/2)
  │   └── *.airflow.flowdeck.io  → nginx-ingress on data EKS cluster
  └── Route53 (flowdeck.io hosted zone)
```

### 14.2 Internal Traffic

All internal traffic (API Server ↔ NATS, Commander ↔ API Server) uses Kubernetes `ClusterIP` services. EKS Security Groups for Pods (SGP) is enabled for fine-grained pod-level network isolation.

### 14.3 Agent Connectivity Options

| Option | Description |
|---|---|
| **Public HTTPS** (default) | Agent → `api.flowdeck.io:443`. JWT auth. Optional IP allowlisting. |
| **AWS PrivateLink** | Customer VPC Endpoint → FlowDeck VPC Endpoint Service → API Server. No public internet. |
| **Cross-region PrivateLink** | Customer VPC in `us-west-2` → PrivateLink to `us-east-1`. |
| **AWS Direct Connect** | On-premises agent → Direct Connect → FlowDeck PrivateLink endpoint. |
| **IP Allowlisting** | Restrict API Server to specific customer source IP ranges. |

### 14.4 AWS PrivateLink Setup

```
FlowDeck VPC
  └── NLB (flowdeck-agent-nlb)
        └── Target group → api-server pods (port 9090)
        └── VPC Endpoint Service (com.amazonaws.vpce.us-east-1.vpce-svc-<id>)

Customer VPC
  └── VPC Interface Endpoint → resolves api.flowdeck.io to private IP
  └── Route53 Private Hosted Zone (flowdeck.io → VPC Endpoint IPs)
```

Customer one-time setup:

```bash
aws ec2 create-vpc-endpoint \
  --vpc-id vpc-xxxx \
  --service-name com.amazonaws.vpce.us-east-1.vpce-svc-xxxx \
  --vpc-endpoint-type Interface \
  --subnet-ids subnet-a subnet-b subnet-c \
  --security-group-ids sg-xxxx \
  --private-dns-enabled
```

---

## 15. API Design

### 15.1 REST API (v1)

Base URL: `https://api.flowdeck.io/v1`

```
/organizations                        GET, POST
/organizations/{id}                   GET, PUT, DELETE

/workspaces                           GET, POST
/workspaces/{id}                      GET, PUT, DELETE

/clusters                             GET, POST
/clusters/{id}                        GET, DELETE
/clusters/{id}/health                 GET
/clusters/{id}/token/rotate           POST

/deployments                          GET, POST
/deployments/{id}                     GET, PUT, DELETE
/deployments/{id}/status              GET
/deployments/{id}/logs                GET (SSE stream)
/deployments/{id}/metrics             GET
/deployments/{id}/hibernate           POST
/deployments/{id}/wake-up             POST

/deployments/{id}/airflow-variables   GET, POST
/deployments/{id}/airflow-variables/{key}  PUT
/deployments/{id}/airflow-variables/copy   POST

/deployments/{id}/connections         GET, POST
/deployments/{id}/connections/{id}    PUT
/deployments/{id}/connections/copy    POST

/deployments/{id}/pools               GET, POST
/deployments/{id}/pools/{name}        PUT
/deployments/{id}/pools/copy          POST

/deployments/{id}/environment-variables      GET, POST
/deployments/{id}/environment-variables/{key} PUT

/deployments/{id}/worker-queues       GET, POST
/deployments/{id}/worker-queues/{name} PUT, DELETE

/deployments/{id}/users               GET, POST
/deployments/{id}/users/{userId}      PUT, DELETE

/deployments/{id}/teams               GET, POST
/deployments/{id}/teams/{teamId}      PUT, DELETE

/deployments/{id}/tokens              GET, POST
/deployments/{id}/tokens/{tokenId}    PUT, DELETE
/deployments/{id}/tokens/{tokenId}/rotate  POST

/deploy/{deploymentId}                POST
/deploy/{deploymentId}/history        GET

/remote-agents                        GET
/remote-agents/{id}                   GET, DELETE
/remote-agents/{id}/cordon            POST
/remote-agents/{id}/uncordon          POST
```

### 15.2 gRPC API (Agent Protocol)

```protobuf
syntax = "proto3";
package flowdeck.agent.v1;

service AgentService {
  rpc Heartbeat(HeartbeatRequest) returns (HeartbeatResponse);
  rpc GetDesiredState(GetDesiredStateRequest) returns (DesiredState);
  rpc ReportActualState(ActualState) returns (ReportResponse);
  rpc RotateToken(RotateTokenRequest) returns (RotateTokenResponse);
  rpc StreamEvents(StreamEventsRequest) returns (stream Event);
}
```

### 15.3 Versioning

URL path versioned (`/v1`, `/v2`). API Server supports N and N-1. CLI and Agent embed supported API version range and refuse to connect if incompatible.

---

## 16. Authentication & Authorization

### 16.1 User Authentication

| Method | Notes |
|---|---|
| Username / Password | SCRAM-SHA-256 hashed in RDS Aurora. Starter tier. |
| SSO / OIDC | Okta, Microsoft Entra ID, Google Workspace, any OIDC IdP. |
| SAML 2.0 | Enterprise tier. |
| API Tokens | Scoped, bcrypt-hashed in RDS, shown once at creation. |
| AWS IAM Identity Center | Future — AWS-native enterprise SSO. |

### 16.2 RBAC Model

```
Organization
  └── Workspace
        └── Deployment

Organization:  ORGANIZATION_MEMBER | ORGANIZATION_BILLING_ADMIN | ORGANIZATION_OWNER
Workspace:     WORKSPACE_VIEWER | WORKSPACE_EDITOR | WORKSPACE_OPERATOR | WORKSPACE_OWNER
Deployment:    DEPLOYMENT_VIEWER | DEPLOYMENT_EDITOR | DEPLOYMENT_ADMIN
CI/CD:         DEPLOYER (trigger deploys and DAG runs only)
```

### 16.3 Agent Authentication

Cluster-scoped tokens (not user tokens). Grant only: read desired state for this cluster, write actual state and heartbeats for this cluster. No access to billing, user management, or other clusters.

---

## 17. Observability Stack

### 17.1 Metrics (Prometheus)

**Public Cloud mode:** Federation Prometheus on Control Plane scrapes per-deployment instances on data cluster every 15s.

**Agent-Based mode:** Per-cluster Prometheus (deployed by agent chart) remote-writes curated metrics to FlowDeck Control Plane Prometheus at `https://metrics.flowdeck.io/push`.

**Optional AWS CloudWatch integration:** Customers grant IRSA `cloudwatch:PutMetricData` to export metrics. Opt-in only — not a FlowDeck dependency.

Key metrics in FlowDeck UI: task success/failure rate, scheduler heartbeat lag, queue depth per worker-queue, worker CPU/memory, DAG parsing time, pod restart counts.

### 17.2 Logs (Loki + S3)

```
Airflow pods → Vector sidecar → Loki (S3 chunk store: flowdeck-loki-prod)
                                  └── Queryable in FlowDeck UI via LogQL

Agent-Based mode:
Airflow pods → Vector DaemonSet → Customer's S3 bucket
                                  └── FlowDeck UI: pre-signed URL fetch
                                      (logs never reach FlowDeck S3)
```

Task logs in agent mode are written directly to the customer's S3 bucket via their IRSA role. The FlowDeck UI retrieves a pre-signed URL from the API Server — customer task logs never leave the customer's AWS account.

### 17.3 Alerting (Alertmanager)

| Alert | Trigger | Severity |
|---|---|---|
| Agent heartbeat lost | No heartbeat > 60s | Critical |
| Scheduler not heartbeating | Scheduler dead | Critical |
| Task failure rate > 20% | Last 1h | Warning |
| Worker CPU > 90% | 5 min sustained | Warning |
| Deployment reconcile failed | 3 consecutive errors | Critical |
| RDS Aurora storage > 80% | — | Warning |
| EKS node group at max capacity | — | Warning |

### 17.4 Distributed Tracing (Phase 3)

OpenTelemetry SDK in application code (cloud-agnostic). AWS X-Ray exporter in Phase 3. GCP Cloud Trace exporter in Phase 4. No AWS SDK tracing calls in business logic.

---

## 18. Multi-Tenancy Model

| Layer | Mechanism | AWS Implementation |
|---|---|---|
| Network | Kubernetes NetworkPolicies | EKS + VPC CNI + Security Groups for Pods |
| Compute | ResourceQuota + LimitRange per namespace | EKS namespaced quotas |
| Storage | Separate Postgres schema (enterprise: separate Aurora instance) | RDS Aurora |
| Secrets | Secrets Manager path-scoped by org/deployment | IRSA condition on `secretsmanager:SecretId` prefix |
| Logs | S3 prefix isolation | `/<org-id>/<dep-id>/` prefix |
| Container images | ECR repo per organization | ECR resource policy |
| IAM | IRSA role per deployment namespace | `sts:AssumeRoleWithWebIdentity` scoped to namespace SA |

KEDA prevents noisy-neighbor starvation. Node taints (`flowdeck.io/tier: enterprise`) allow dedicated node pools for enterprise customers.

---

## 19. Extension Path — Remote Execution Mode

> **Target:** 6–12 months post launch. Requires Airflow 3.

### 19.1 What Changes

Scheduler and Metadata DB stay on FlowDeck AWS. Task execution happens in the customer's environment via a lightweight Worker Agent. No Scheduler or Metadata DB installed in customer's cluster.

### 19.2 New AWS Components Required

| Component | AWS Service |
|---|---|
| Task Execution API proxy | New endpoint on API Server (EKS pod) |
| Per-deployment task queue | **Amazon SQS** |
| Agent auth token store | AWS Secrets Manager (rotation enabled) |
| Private agent connectivity | **AWS PrivateLink** VPC Endpoint Service + NLB |
| XCom backend | Customer's own S3 bucket |

### 19.3 Architecture

```
FlowDeck AWS (us-east-1)                  Customer's Environment
┌──────────────────────────────┐          ┌──────────────────────────────────┐
│  Airflow Scheduler           │          │  Worker Agent (Helm chart)       │
│  Airflow API Server          │◄─ HTTPS ┤  Airflow Workers                 │
│  RDS Aurora (Metadata DB)    │  outbound│  Airflow Triggerer               │
│  Task Execution API proxy    │  only    │  DAG Processor Agent             │
│  SQS (task queues)           │          │                                  │
│  FlowDeck UI / API Server    │          │  Customer's Secrets Manager/Vault│
└──────────────────────────────┘          │  Customer's S3 (XCom, logs)      │
                                          │  Customer's data sources (VPC)   │
                                          └──────────────────────────────────┘
```

### 19.4 Network Options

| Option | Implementation |
|---|---|
| Public HTTPS | `api.flowdeck.io:443`, token-auth, IP allowlisting optional |
| **AWS PrivateLink** (recommended) | FlowDeck VPC Endpoint Service → Customer VPC Endpoint |
| AWS Direct Connect | On-premises Worker Agents |
| GCP Private Service Connect *(Phase 4)* | Worker Agents on GCP |
| Azure Private Link *(Phase 5)* | Worker Agents on Azure |

### 19.5 Security Boundary

FlowDeck sees: task state, duration, DAG graph structure, log links.  
FlowDeck never sees: raw task data, customer secrets, DAG Python code, XCom payloads (customer's S3).

---

## 20. Extension Path — Private Cloud Mode

> **Target:** 12–18 months post launch.

### 20.1 What Changes

Everything runs in the customer's environment. FlowDeck delivers software via an installer. No outbound connection to FlowDeck SaaS required after installation (air-gap capable).

### 20.2 Installer

```bash
# Customer's EKS cluster
flowdeck-installer install \
  --cloud-provider aws \
  --region us-east-1 \
  --eks-cluster-name customer-eks \
  --rds-endpoint customer-aurora.cluster-xxxx.us-east-1.rds.amazonaws.com \
  --s3-bucket customer-flowdeck-data \
  --ecr-registry 123456789.dkr.ecr.us-east-1.amazonaws.com \
  --license-key <license>

# GCP GKE (Phase 4)
flowdeck-installer install \
  --cloud-provider gcp \
  --gke-cluster-name customer-gke \
  --cloud-sql-connection customer-project:us-central1:flowdeck-db \
  --gcs-bucket customer-flowdeck-data \
  --license-key <license>

# Azure AKS (Phase 5)
flowdeck-installer install \
  --cloud-provider azure \
  --aks-cluster-name customer-aks \
  --license-key <license>

# Air-gapped (any cloud)
flowdeck-installer generate-image-bundle --output images.tar.gz
flowdeck-installer install --air-gap \
  --private-registry registry.customer.internal/flowdeck \
  --cloud-provider aws ...
```

### 20.3 Cloud Mapping for Private Cloud

| Component | AWS | GCP | Azure | On-Prem |
|---|---|---|---|---|
| Kubernetes | EKS | GKE | AKS | Rancher / OpenShift |
| Database | RDS Aurora PG | Cloud SQL PG | Azure DB for PG | Self-hosted PG |
| Object storage | S3 | GCS | Azure Blob | MinIO |
| Container registry | ECR | Artifact Registry | ACR | Harbor |
| Secrets | Secrets Manager | Secret Manager | Key Vault | Vault |
| DNS | Route53 | Cloud DNS | Azure DNS | CoreDNS / external-dns |
| Load balancer | ALB | Cloud Load Balancing | Azure App Gateway | NGINX / MetalLB |

### 20.4 License Service

Offline validation using a signed JWT with embedded feature flags and expiry. Validated locally against FlowDeck's public key embedded in the installer binary. No network call to FlowDeck after installation.

---

## 21. Extension Path — Multi-Cloud (GCP & Azure)

> **Target:** GCP Phase 4 (months 18–24). Azure Phase 5 (months 24–30).

### 21.1 Strategy

The FlowDeck Control Plane continues to run on AWS. What expands to GCP and Azure is:

1. **Agent-Based mode** — already works on GKE and AKS today (no AWS dependency in agent code)
2. **Remote Execution** — Worker Agents on GCP/Azure. GCP Private Service Connect and Azure Private Link added as connectivity options
3. **Private Cloud** — `flowdeck-installer` gains GCP and Azure provider implementations
4. **Control Plane multi-cloud** (Phase 6+) — Optional second FlowDeck Control Plane on GCP for customers requiring orchestration metadata residency in GCP

### 21.2 GCP Service Mapping (Phase 4)

| AWS (Phase 1) | GCP Equivalent | Go Interface |
|---|---|---|
| Amazon EKS | GKE Autopilot | `ComputeProvider` |
| RDS Aurora PostgreSQL | Cloud SQL for PostgreSQL 15 | `DatabaseProvider` |
| Amazon S3 | Google Cloud Storage (GCS) | `StorageProvider` |
| Amazon ECR | Artifact Registry | `RegistryProvider` |
| AWS Secrets Manager | Google Secret Manager | `SecretsProvider` |
| Route53 | Cloud DNS | `DNSProvider` |
| AWS PrivateLink | Private Service Connect (PSC) | `NetworkProvider` |
| AWS IRSA | Workload Identity Federation | (k8s annotation, not in CloudProvider) |
| KEDA + SQS scaler | KEDA + Pub/Sub scaler | KEDA plugin |

### 21.3 Azure Service Mapping (Phase 5)

| AWS (Phase 1) | Azure Equivalent |
|---|---|
| Amazon EKS | Azure Kubernetes Service (AKS) |
| RDS Aurora PostgreSQL | Azure Database for PostgreSQL — Flexible Server |
| Amazon S3 | Azure Blob Storage |
| Amazon ECR | Azure Container Registry (ACR) |
| AWS Secrets Manager | Azure Key Vault |
| Route53 | Azure DNS |
| AWS PrivateLink | Azure Private Link |
| AWS IRSA | AKS Workload Identity |
| KEDA + SQS scaler | KEDA + Azure Service Bus scaler |

---

## 22. Extension Path — Multi-Region

> **Target:** Phase 3 (months 10–14).

### 22.1 AWS Dual-Region

Second FlowDeck Control Plane deployed in `us-west-2` as hot standby (active-passive). RDS Aurora Global Database provides sub-second replication lag. Route53 health checks fail over `api.flowdeck.io` to `us-west-2` if primary is unavailable (RTO < 60s, RPO < 1s).

`eu-west-1` (Ireland) added for EU customers requiring GDPR data residency.

### 22.2 Regional Deployment Affinity

Customers choose a home region when creating a Workspace. All deployments in that workspace run in the chosen region's data cluster. Future: geo-affinity policies for Remote Execution agents ("tasks must run in EU").

---

## 23. Data Model

### 23.1 Core Entities (RDS Aurora — `flowdeck_platform`)

```sql
CREATE TABLE organizations (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name            TEXT NOT NULL,
  slug            TEXT UNIQUE NOT NULL,
  tier            TEXT NOT NULL DEFAULT 'starter',  -- starter | pro | enterprise
  home_region     TEXT NOT NULL DEFAULT 'us-east-1',
  cloud_provider  TEXT NOT NULL DEFAULT 'aws',
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE workspaces (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
  name            TEXT NOT NULL,
  description     TEXT,
  ci_cd_enforced  BOOLEAN DEFAULT FALSE,
  created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE clusters (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id      UUID NOT NULL REFERENCES organizations(id),
  name                 TEXT NOT NULL,
  mode                 TEXT NOT NULL,       -- 'flowdeck_cloud' | 'agent_based'
  cloud_provider       TEXT,               -- 'aws' | 'gcp' | 'azure' | 'other'
  region               TEXT,               -- 'us-east-1' | 'us-central1' | null
  status               TEXT NOT NULL DEFAULT 'pending',
  last_heartbeat       TIMESTAMPTZ,
  k8s_version          TEXT,
  agent_version        TEXT,
  sentinel_version     TEXT,
  private_link_enabled BOOLEAN DEFAULT FALSE,
  created_at           TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE deployments (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id      UUID NOT NULL REFERENCES workspaces(id) ON DELETE CASCADE,
  cluster_id        UUID REFERENCES clusters(id),  -- null = flowdeck cloud
  name              TEXT NOT NULL,
  description       TEXT,
  airflow_version   TEXT NOT NULL,
  executor          TEXT NOT NULL DEFAULT 'CeleryExecutor',
  resource_profile  TEXT NOT NULL DEFAULT 'medium',
  dag_bundle_url    TEXT,                           -- ECR URI or S3 path
  status            TEXT NOT NULL DEFAULT 'creating',
  high_availability BOOLEAN DEFAULT FALSE,
  development_mode  BOOLEAN DEFAULT FALSE,
  cloud_provider    TEXT NOT NULL DEFAULT 'aws',
  region            TEXT NOT NULL DEFAULT 'us-east-1',
  workload_identity TEXT,                           -- AWS: IAM role ARN
  desired_state     JSONB NOT NULL DEFAULT '{}',
  actual_state      JSONB,
  created_at        TIMESTAMPTZ DEFAULT now(),
  updated_at        TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE deployment_versions (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  deployment_id UUID NOT NULL REFERENCES deployments(id) ON DELETE CASCADE,
  version       INT NOT NULL,
  desired_state JSONB NOT NULL,
  description   TEXT,
  created_by    UUID,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE deployment_tokens (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  deployment_id UUID NOT NULL REFERENCES deployments(id) ON DELETE CASCADE,
  name          TEXT NOT NULL,
  token_hash    TEXT NOT NULL,     -- bcrypt
  role          TEXT NOT NULL,
  expires_at    TIMESTAMPTZ,
  last_used_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE audit_logs (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  actor_id        UUID,
  actor_type      TEXT,            -- 'user' | 'agent' | 'system' | 'ci_cd'
  action          TEXT NOT NULL,   -- 'deployment.create' | 'deploy.trigger' | ...
  resource_type   TEXT,
  resource_id     UUID,
  source_ip       INET,
  user_agent      TEXT,
  metadata        JSONB,
  created_at      TIMESTAMPTZ DEFAULT now()
) PARTITION BY RANGE (created_at);  -- monthly partitions

CREATE INDEX audit_logs_org_created ON audit_logs (organization_id, created_at DESC);
```

---

## 24. Technology Stack

### 24.1 Application Stack

| Layer | Technology | Rationale |
|---|---|---|
| API Server | Go 1.23 + Chi router | Statically compiled, small binary, excellent k8s client |
| gRPC (agent protocol) | protobuf + grpc-go | Efficient binary protocol, streaming, language-agnostic |
| Event bus | NATS JetStream 2.x | Lightweight, durable, no ZooKeeper, embedded mode for dev |
| Platform DB | PostgreSQL 15 (RDS Aurora) | JSONB for flexible desired state, PITR, Aurora Serverless v2 option |
| UI | Next.js 14 + React 18 + Tailwind CSS | SSR, TypeScript end-to-end |
| CLI | Go 1.23 (cobra + viper) | Single binary, no runtime, CI/CD-friendly |
| Agent | Go 1.23 | Same language as API Server; 15 MB binary |
| Airflow packaging | Helm 3 + Docker | Industry standard |
| Connection pooling | PgBouncer 1.22 | Transaction mode; eliminates Aurora connection storms |

### 24.2 AWS Infrastructure Stack

| Component | AWS Service | Notes |
|---|---|---|
| Kubernetes | Amazon EKS 1.30+ | Managed control plane, Bottlerocket nodes |
| Database | RDS Aurora PG 15 | Graviton3 (`r7g`) instances |
| Storage | Amazon S3 | Logs, DAG bundles, Loki chunks |
| Registry | Amazon ECR | Image scanning, lifecycle policies |
| Load balancing | AWS ALB + AWS Load Balancer Controller | Native k8s ingress |
| Autoscaling | KEDA 2.x → Karpenter (Phase 2) | Task-based and node-based scaling |
| Secrets | AWS Secrets Manager | 30-day auto-rotation, KMS CMK |
| IaC | Terraform 1.8 + Terragrunt | All infra as code |
| CI/CD | GitHub Actions + AWS CodeBuild | App deploys and infra changes |
| Metrics | Prometheus + Grafana | Self-hosted on EKS, EBS backend |
| Logs | Loki + Vector | S3 chunk store |
| Tracing | AWS X-Ray → OpenTelemetry (Phase 3) | OTEL SDK in app code; X-Ray exporter first |
| WAF | AWS WAF v2 | OWASP rules on ALB |
| CDN | Amazon CloudFront | UI + static assets |
| DNS | Amazon Route53 | Public + private hosted zones |
| TLS | AWS Certificate Manager | Auto-renewed wildcard certs |

### 24.3 Local Development Stack

| Tool | Purpose |
|---|---|
| Docker / Podman (auto-detected) | `flowdeck dev start` |
| docker-compose / podman-compose | Orchestrate local Airflow containers |
| LocalStack (optional) | Simulate AWS services (S3, SQS, Secrets Manager) locally |
| Kind / Minikube (optional) | Test agent mode locally |

---

## 25. Security Architecture

### 25.1 AWS Security Controls

```
Layer 1: Perimeter
  - AWS WAF v2 on ALB: OWASP Top 10, rate limiting, geo-blocking
  - CloudFront: DDoS protection via AWS Shield Standard
  - VPC: no public subnets for EKS nodes or RDS
  - VPC endpoints for all AWS API calls (no internet)
  - VPC Flow Logs → S3 → Athena

Layer 2: Identity & Access
  - IRSA for all pod-level AWS API access (zero static IAM keys)
  - SCP on AWS Organization: deny IAM key creation
  - AWS IAM Access Analyzer: flag overly permissive policies
  - Short-lived JWTs (1h access, 7d refresh) for user sessions
  - Agent tokens: 24h, bcrypt-hashed in RDS, auto-rotated

Layer 3: Encryption
  - TLS 1.3 on ALB (ELBSecurityPolicy-TLS13-1-2-2021-06)
  - mTLS API Server ↔ NATS (Phase 2)
  - RDS Aurora: AES-256 at rest, KMS CMK
  - S3: SSE-KMS on all buckets
  - Secrets Manager: KMS CMK, 30-day rotation
  - EBS volumes: encrypted with KMS CMK

Layer 4: Application
  - RBAC on every API handler via middleware
  - Organization-scoped WHERE clauses in all DB queries
  - Secrets write-only in API responses (never returned)
  - Full audit log in RDS (monthly partitions); 7-year S3 archive

Layer 5: Runtime
  - EKS Pod Security Standards (restricted profile)
  - Non-root containers, read-only root filesystem
  - NetworkPolicies: deny all cross-namespace traffic
  - Amazon GuardDuty: EKS audit log + S3 data event threat detection
  - AWS Config: CIS Benchmark rules, auto-remediation
  - AWS Security Hub: aggregated findings
  - AWS CloudTrail: all API calls, immutable S3 archive

Layer 6: Supply Chain
  - ECR image scanning on push: block CRITICAL CVEs
  - Dependabot: automated dependency PRs
  - SBOM generation per release
  - Signed container images via cosign + AWS Signer (Phase 2)
```

### 25.2 Compliance Readiness

| Standard | Mode | AWS Controls |
|---|---|---|
| **SOC 2 Type II** | Public Cloud | CloudTrail, GuardDuty, Config, KMS, VPC Flow Logs |
| **HIPAA** | Agent-Based | Customer data never leaves customer VPC; BAA available |
| **GDPR** | All | EU region (`eu-west-1`, Phase 3); no PII in FlowDeck Control Plane |
| **PCI-DSS** | Agent-Based + PrivateLink | CDE workloads stay in customer VPC |
| **FedRAMP Moderate** | Private Cloud *(Phase 6)* | GovCloud (`us-gov-east-1`) deployment |

---

## 26. Cost Architecture on AWS

### 26.1 FlowDeck's AWS Cost Drivers

| Resource | Cost Driver | Optimization |
|---|---|---|
| EKS worker node groups | Worker pods per deployment | Spot instances (80%) + KEDA scale-to-zero |
| RDS Aurora | Storage + ACU (Serverless v2) | Serverless v2 scales to 0.5 ACU when idle |
| S3 | Logs + DAG bundles | Lifecycle: Standard → IA (30d) → Glacier (90d) |
| NAT Gateways | Cross-AZ + outbound transfer | VPC endpoints eliminate most NAT costs |
| ALB | LCU (requests, connections) | CloudFront caching reduces ALB hits |
| ECR | Storage + data transfer | Lifecycle policy: keep last 5 tags per repo |

### 26.2 Cost Attribution per Customer

Every AWS resource is tagged for per-customer Cost Explorer reporting:

```hcl
tags = {
  "flowdeck:org-id"        = var.org_id
  "flowdeck:workspace-id"  = var.workspace_id
  "flowdeck:deployment-id" = var.deployment_id
  "flowdeck:tier"          = var.tier     # starter | pro | enterprise
  "flowdeck:env"           = var.env      # prod | staging
}
```

### 26.3 Customer-Facing Pricing Model

| Tier | Compute | Deployments | Pricing |
|---|---|---|---|
| Starter | Shared node pools, burstable | Up to 3 | Usage-based |
| Pro | Shared node pools, standard | Up to 10 | Usage-based |
| Enterprise | Dedicated node pools (On-Demand) | Unlimited | Committed use |
| Agent-Based | N/A (customer pays their compute) | Unlimited | Flat management fee |

---

## 27. Infrastructure as Code

### 27.1 Repository Structure

```
infra/
├── modules/
│   ├── eks/                    # EKS cluster (AWS impl of ComputeProvider)
│   ├── rds-aurora/             # RDS Aurora module
│   ├── s3/                     # S3 bucket module
│   ├── ecr/                    # ECR repository module
│   ├── vpc/                    # VPC, subnets, NAT GW, VPC endpoints
│   ├── iam/                    # IRSA roles, SCPs, IAM policies
│   ├── alb/                    # ALB, WAF, listener rules
│   ├── route53/                # Hosted zones, records
│   ├── nats/                   # NATS Helm release module
│   ├── keda/                   # KEDA Helm release module
│   └── flowdeck-control-plane/ # Aggregates all control plane modules
│
├── environments/
│   ├── prod/
│   │   ├── us-east-1/          # Phase 1 launch region
│   │   │   ├── vpc/
│   │   │   ├── eks-control/
│   │   │   ├── eks-data/
│   │   │   ├── rds-platform/
│   │   │   └── flowdeck-apps/
│   │   ├── us-west-2/          # Phase 3 DR region
│   │   └── eu-west-1/          # Phase 3 EU region
│   ├── staging/
│   └── dev/
│
└── terragrunt.hcl
```

### 27.2 Terraform State Backend

```hcl
remote_state {
  backend = "s3"
  config = {
    bucket         = "flowdeck-tfstate-${get_aws_account_id()}"
    key            = "${path_relative_to_include()}/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    kms_key_id     = "alias/flowdeck-terraform-state"
    dynamodb_table = "flowdeck-tfstate-locks"
  }
}
```

### 27.3 GitOps (Flux CD)

FlowDeck application deployments to EKS (API Server, NATS, Prometheus, etc.) are managed via **Flux CD**:

```
GitHub (infra repo) → Flux CD → EKS → HelmRelease CRDs
```

Airflow deployment Helm releases are managed by Commander (not Flux). Commander is the authoritative reconciler for all customer workloads.

---

## 28. Rollout & Migration Strategy

### 28.1 Phase 1 — AWS Foundation (Months 1–3)

**Infrastructure:**
- AWS Organization: `flowdeck-prod`, `flowdeck-staging`, `flowdeck-dev`
- VPC, EKS control cluster, RDS Aurora, ECR, S3, Route53, ACM, WAF in `us-east-1`
- Terraform modules + Terragrunt environments
- Flux CD for GitOps app deployment
- GuardDuty, Security Hub, Config, CloudTrail activated

**Platform:**
- Control Plane: API Server, RDS Aurora, NATS, basic UI behind CloudFront
- Public Cloud mode: EKS data cluster, Commander, Config Syncer, Airflow Helm chart
- IRSA for all pods; Secrets Manager for all credentials
- Prometheus + Grafana + Loki with S3 backend

**CLI:**
- `login`, `logout`, `version`, `config`, `context`, `completion`
- `deployment create/list/delete/inspect/logs`
- `deploy`, `dev start/stop/restart/init/parse`

### 28.2 Phase 2 — Agent-Based + Full CLI (Months 4–9)

**Infrastructure:**
- AWS PrivateLink VPC Endpoint Service for agent connectivity
- KEDA on data EKS cluster
- Karpenter replacing Cluster Autoscaler (faster node provisioning)

**Platform:**
- Agent binary: Commander + Config Syncer + Heartbeat Reporter + Sentinel
- Agent Helm chart, cluster token generation, install flow in UI
- gRPC agent protocol (parallel to REST polling)

**CLI:**
- All `flowdeck deployment` subcommands (airflow-variable, connection, pool, variable, worker-queue, token, user, team, service-account)
- `flowdeck workspace/organization/team/user` command groups
- `flowdeck cluster` commands
- `flowdeck dev pytest/upgrade-test/bash/object/logs`
- `flowdeck deployment hibernate/wake-up`
- GitHub Actions `flowdeck/deploy-action@v1`
- AWS CodeBuild CLI image on ECR public (`public.ecr.aws/flowdeck/cli:latest`)

### 28.3 Phase 3 — Multi-Region AWS (Months 10–14)

- `us-west-2` Control Plane (hot standby); RDS Aurora Global Database; Route53 health-check failover
- `eu-west-1` for EU customer GDPR compliance
- AWS X-Ray tracing → OpenTelemetry SDK migration
- CloudFront origin failover

### 28.4 Phase 4 — Remote Execution (Months 15–21)

- Airflow 3 Task Execution API proxy in FlowDeck API Server
- Worker Agent Helm chart (stripped, no Commander)
- DAG Processor Agent + DAG serialization pipeline
- SQS-based task queue for worker polling
- `flowdeck remote` CLI commands
- AWS PrivateLink for Remote Execution agent connectivity
- GCP Private Service Connect support (first GCP footprint)

### 28.5 Phase 5 — Private Cloud + Azure (Months 22–30)

- `flowdeck-installer` CLI with AWS and GCP provider implementations
- Offline license service
- Air-gap image bundle generator
- Azure provider implementation (AKS, Azure DB for PG, Key Vault, ACR)
- `flowdeck deployment runtime upgrade/migrate`

---

## 29. Open Questions & Decisions

| # | Question | Options | Recommendation |
|---|---|---|---|
| 1 | Airflow 2.x scope? | Full parity / Read-only / Airflow 3 only | Support 2.x in Public Cloud + Agent-Based. Remote Execution: Airflow 3 only. |
| 2 | Agent protocol: REST polling vs gRPC? | REST polling (Phase 1) / gRPC streaming (Phase 2) | Start REST polling; graduate to gRPC in Phase 2 for push events. |
| 3 | Postgres isolation: shared vs dedicated? | Shared Aurora, per-dep schema / Dedicated Aurora per customer | Shared for Starter/Pro; dedicated Aurora for Enterprise. |
| 4 | EKS autoscaler: Cluster Autoscaler vs Karpenter? | Cluster Autoscaler (stable) / Karpenter (faster, AWS-native) | Cluster Autoscaler Phase 1; migrate to Karpenter Phase 2. |
| 5 | Spot instances for workers? | On-Demand only / Spot with fallback | Spot (Graviton) for workers. On-Demand for schedulers. |
| 6 | Task logs for agent-based mode? | FlowDeck Loki / Customer's S3 (pre-signed URL) | Customer's S3 with pre-signed URL in FlowDeck UI — logs never leave customer account. |
| 7 | NATS vs Amazon MSK (Kafka)? | NATS JetStream / Amazon MSK | NATS — operationally simpler, no MSK cost. Revisit if volume exceeds 50k events/s. |
| 8 | RDS Aurora Serverless v2 vs provisioned? | Serverless v2 / Provisioned r7g | Serverless v2 for staging/dev. Provisioned `r7g.large` for production. |
| 9 | GovCloud support timing? | Phase 4 / Phase 6 / Never | Phase 6 via Private Cloud installer targeting `us-gov-east-1`. |
| 10 | Local dev against real AWS? | LocalStack / Real AWS dev account | LocalStack for unit tests. Optional real AWS dev account for integration tests (budget alarms set). |
| 11 | Container runtime default? | Docker / Podman / Auto-detect | Auto-detect; Podman default on macOS (rootless, no daemon). |
| 12 | First GCP region? | `us-central1` / `us-east1` | `us-central1` (Iowa) — lowest GCP latency from `us-east-1`. |

---

*Document maintained by the FlowDeck Platform Architecture Team. Last updated: 2026-04-27.*
