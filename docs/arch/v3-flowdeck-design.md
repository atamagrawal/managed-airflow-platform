# FlowDeck Enterprise Airflow Deployment Architecture

## Document Metadata

- **Document Type:** Architecture Design Record
- **Version:** 2.0
- **Status:** Detailed Design
- **Last Updated:** April 28, 2026
- **Target Audience:** Platform Engineers, SREs, Security Engineers, Contributors

---

# 1. Introduction

## 1.1 Purpose

This document describes the detailed enterprise-grade deployment architecture for FlowDeck, a managed Apache Airflow platform. The scope is limited to the provisioning, lifecycle management, and runtime architecture of customer Airflow environments.

The architecture follows a strict three-plane model:

- **Control Plane** — Centralized management and orchestration
- **Data Plane** — Infrastructure provisioning and reconciliation
- **Execution Plane** — Isolated Apache Airflow runtime

This separation enables:

- Strong tenant isolation
- Horizontal scalability
- Operational safety
- Secure multi-tenancy
- Independent lifecycle management
- Zero-downtime upgrades

---

# 2. Design Goals

## Functional Goals

- Provision Airflow environments in under 10 minutes
- Support thousands of environments
- Enable one-click upgrades
- Provide strict tenant isolation
- Support multiple Airflow versions simultaneously
- Support enterprise networking requirements

## Non-Functional Goals

- 99.95% control plane SLA
- 99.9% environment SLA
- Zero shared runtime components across tenants
- Recovery Time Objective (RTO) < 30 minutes
- Recovery Point Objective (RPO) < 5 minutes
- No single point of failure

---

# 3. High-Level Architecture

```text
┌──────────────────────────────────────────────────────────────┐
│                        Control Plane                         │
│                                                              │
│  API Gateway                                                 │
│  Authentication Service                                      │
│  Organization Service                                        │
│  Environment Service                                         │
│  Deployment Orchestrator                                     │
│  Billing Service                                              │
│  Audit Service                                                │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            │ Desired State
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                         Data Plane                           │
│                                                              │
│  Kubernetes Operator                                         │
│  Terraform Runner                                            │
│  Secret Synchronizer                                         │
│  Certificate Manager                                         │
│  Policy Engine                                                │
│  Reconciliation Controller                                   │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            │ Actual State
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                      Execution Plane                         │
│                                                              │
│  Dedicated Namespace                                          │
│  Apache Airflow Runtime                                       │
│  PostgreSQL Database                                          │
│  Redis Broker                                                  │
│  Object Storage                                                │
│  Logging + Metrics                                             │
└──────────────────────────────────────────────────────────────┘
```

---

# 4. Control Plane

## 4.1 Responsibilities

The control plane acts as the system of record.

Responsibilities include:

- Tenant management
- Authentication and authorization
- Environment lifecycle management
- Deployment orchestration
- Version management
- Billing and metering
- Audit logging
- Policy enforcement

## 4.2 Core Services

### API Gateway

Handles:

- Request routing
- Authentication
- Rate limiting
- Request tracing
- API versioning

Recommended technologies:

- Kong
- Envoy Gateway
- AWS API Gateway

### Identity Service

Supports:

- OIDC
- SAML 2.0
- SCIM provisioning
- MFA enforcement
- Just-In-Time user creation

### Environment Service

Responsible for:

- CRUD operations
- Desired state persistence
- Environment specifications
- Version pinning
- Lifecycle state machine

### Deployment Orchestrator

Asynchronous orchestration engine that manages:

- Provisioning workflows
- Upgrade workflows
- Rollback workflows
- Deletion workflows

Recommended implementation:

- Temporal
- Cadence
- Durable Task Framework

## 4.3 Control Plane Database Schema

Core tables:

- organizations
- workspaces
- environments
- deployments
- airflow_versions
- audit_events
- billing_usage
- secrets_metadata

PostgreSQL is strongly recommended.

---

# 5. Data Plane

## 5.1 Responsibilities

The data plane translates desired state into running infrastructure.

It performs:

- Kubernetes reconciliation
- Infrastructure provisioning
- Secret synchronization
- Certificate issuance
- Runtime upgrades
- Drift remediation
- Health monitoring

## 5.2 Kubernetes Operator

The operator is the heart of FlowDeck.

### Why Operator Pattern?

- Declarative management
- Automatic reconciliation
- Native Kubernetes integration
- Robust failure handling
- Upgrade orchestration
- Self-healing

### Custom Resources

#### FlowDeckEnvironment

```yaml
apiVersion: platform.flowdeck.io/v1alpha1
kind: FlowDeckEnvironment
metadata:
  name: acme-prod
spec:
  organizationId: org_123
  workspaceId: ws_456
  airflowVersion: 3.0.1
  pythonVersion: 3.12
  executor: CeleryKubernetesExecutor
  size: large
  dagBundle:
    image: registry.flowdeck.io/acme/dags:v42
  networking:
    privateOnly: true
  scaling:
    minWorkers: 2
    maxWorkers: 50
  storage:
    logs: s3://flowdeck-logs/acme-prod
status:
  phase: Ready
  endpoint: https://acme-prod.flowdeck.io
```

## 5.3 Reconciliation Loop

```text
Observe Desired State
        ↓
Observe Actual State
        ↓
Compute Diff
        ↓
Apply Changes
        ↓
Verify Health
        ↓
Update Status
```

Reconciliation must be idempotent.

---

# 6. Execution Plane

## 6.1 Isolation Model

Each customer environment receives:

- Dedicated Kubernetes namespace
- Dedicated Airflow deployment
- Dedicated PostgreSQL database
- Dedicated Redis instance
- Dedicated object storage prefix
- Dedicated service account
- Dedicated TLS certificates

No runtime components are shared.

## 6.2 Airflow Components

### Mandatory Components

- Scheduler
- Webserver
- Triggerer
- Workers
- Flower (optional)
- StatsD exporter

### Supporting Services

- PostgreSQL
- Redis
- Log storage
- Metrics exporter

---

# 7. Airflow Runtime Topology

```text
Namespace: fd-acme-prod

├── airflow-webserver (2 replicas)
├── airflow-scheduler (2 replicas)
├── airflow-triggerer (2 replicas)
├── airflow-worker (autoscaled)
├── airflow-flower (optional)
├── statsd-exporter
├── pgbouncer
└── service mesh sidecars
```

---

# 8. DAG Deployment Architecture

## 8.1 Recommended Approach: OCI DAG Bundles

Each deployment produces an immutable OCI image containing:

- DAG files
- Plugins
- Python dependencies
- Shared libraries
- Configuration overlays

## 8.2 Build Pipeline

```text
Git Push
   ↓
CI Validation
   ↓
Unit Tests
   ↓
DAG Linting
   ↓
Docker Build
   ↓
Security Scan
   ↓
Push to Registry
   ↓
Deploy to FlowDeck
```

## 8.3 Benefits

- Immutable artifacts
- Deterministic deployments
- Fast rollback
- Dependency isolation
- Security scanning
- Version traceability

---

# 9. Metadata Database Architecture

## 9.1 Design

Recommended topology:

- Shared PostgreSQL cluster
- Dedicated database per environment
- PgBouncer connection pooling

## 9.2 High Availability

- Multi-AZ deployment
- Automatic failover
- Point-in-time recovery
- Continuous WAL archiving

## 9.3 Backup Policy

- Full backup daily
- Incremental backup every 5 minutes
- Retention: 35 days

---

# 10. Redis Architecture

## Requirements

- Dedicated Redis logical instance per environment
- TLS enabled
- Persistence enabled
- Automatic failover

Recommended:

- Redis Sentinel
- AWS ElastiCache
- Google Memorystore

---

# 11. Networking Architecture

## Network Isolation

- Namespace-level NetworkPolicies
- Default deny ingress
- Default deny egress
- Explicit service allowlists

## Enterprise Connectivity

- PrivateLink
- VPC Peering
- Transit Gateway
- Customer-managed ingress allowlists

---

# 12. Secrets Management

## Supported Backends

- HashiCorp Vault
- AWS Secrets Manager
- Google Secret Manager
- Azure Key Vault

## Secret Flow

```text
Customer Secret
      ↓
Control Plane Encryption
      ↓
External Secrets Operator
      ↓
Kubernetes Secret
      ↓
Airflow Runtime
```

---

# 13. Autoscaling

## Worker Autoscaling

Based on:

- Celery queue depth
- CPU utilization
- Memory utilization
- Task backlog age

Technology:

- KEDA
- Horizontal Pod Autoscaler

---

# 14. Upgrade Strategy

## Blue-Green Upgrades

Steps:

1. Clone environment
2. Restore metadata snapshot
3. Deploy new Airflow version
4. Run health checks
5. Shift traffic
6. Monitor
7. Retain rollback window

Rollback must complete within 5 minutes.

---

# 15. Observability Stack

## Metrics

- Prometheus
- OpenTelemetry Collector

## Logging

- Fluent Bit
- Loki

## Tracing

- OpenTelemetry
- Tempo or Jaeger

## Dashboards

- Grafana

---

# 16. Security Controls

## Runtime Security

- Pod Security Standards
- Non-root containers
- Read-only root filesystem
- Seccomp profiles
- Image signature verification
- CVE scanning

## Access Security

- SSO
- RBAC
- Just-In-Time elevation
- Audit trails

---

# 17. Failure Domains

## Blast Radius

A single environment failure must never affect:

- Other tenants
- Control plane
- Shared data plane services

Isolation boundaries are enforced at:

- Kubernetes namespace
- Database
- Redis
- Object storage
- IAM roles

---

# 18. Disaster Recovery

## Recovery Objectives

- Control Plane RTO: 15 minutes
- Environment RTO: 30 minutes
- RPO: 5 minutes

## Recovery Mechanisms

- Automated PostgreSQL PITR
- Cross-region object replication
- Declarative environment recreation
- Infrastructure state backup

---

# 19. Provisioning Workflow

```text
User Request
    ↓
API Validation
    ↓
Persist Desired State
    ↓
Start Temporal Workflow
    ↓
Create FlowDeckEnvironment CR
    ↓
Operator Reconciliation
    ↓
Provision Database
    ↓
Provision Redis
    ↓
Deploy Helm Release
    ↓
Run Smoke Tests
    ↓
Mark Ready
```

---

# 20. Deletion Workflow

```text
Suspend Airflow
    ↓
Drain Workers
    ↓
Snapshot Metadata
    ↓
Archive Logs
    ↓
Destroy Runtime
    ↓
Retain Backups
    ↓
Mark Deleted
```

---

# 21. Recommended Technology Stack

| Layer | Technology |
|--------|-----------|
| API | FastAPI / Go |
| Workflow Engine | Temporal |
| Operator | Kubebuilder |
| Packaging | Helm |
| Database | PostgreSQL |
| Cache | Redis |
| Metrics | Prometheus |
| Logs | Loki |
| Tracing | OpenTelemetry |
| Secrets | Vault / External Secrets |

---

# 22. Capacity Planning

## Typical Large Environment

- Scheduler: 2 vCPU / 4 GiB × 2
- Webserver: 2 vCPU / 4 GiB × 2
- Triggerer: 1 vCPU / 2 GiB × 2
- Workers: Autoscaled 2–100
- Redis: 2 GiB
- PostgreSQL: Managed HA

---

# 23. Future Enhancements

- Multi-region active-active control plane
- Customer-managed Kubernetes support
- Dedicated cluster option
- Airflow fleet upgrades
- Cost optimization engine
- Intelligent scheduler tuning

---

# 24. Summary

FlowDeck's enterprise deployment architecture is designed to provide:

- Strong tenant isolation
- Deterministic deployments
- Operational simplicity
- High availability
- Secure multi-tenancy
- Enterprise scalability

The Control Plane, Data Plane, and Execution Plane separation forms a robust foundation for a world-class managed Apache Airflow platform.

