# Flowdeck Enterprise Architecture - Final Reference (v5.0)

> **Version:** 5.0  
> **Status:** Final Architecture Baseline  
> **Last Updated:** April 2026  
> **Primary Scope:** AWS Public Cloud (GA target)  
> **Extension Scope:** Hybrid Agent, Remote Execution, Private Cloud, Multi-Cloud Data Plane  
> **Audience:** Platform engineering, security, SRE, compliance, product, customer architecture teams

---

## 1. Executive Overview

### 1.1 Purpose

This document defines the enterprise-scale reference architecture for Flowdeck. It establishes one canonical model for responsibilities, reliability, security, compliance, and operations, while keeping the implementation AWS-first and contract-compatible with hybrid, private, remote execution, and other cloud providers.

### 1.2 Architectural North Star

- Deliver a managed Airflow platform that scales from startup to regulated enterprise.
- Keep policy and governance centralized while allowing runtime portability.
- Make tenancy, isolation, and reliability explicit and measurable.
- Support strict customer network boundaries using outbound-only control from non-control environments.
- Preserve one product contract across all deployment modes.

### 1.3 In-Scope vs Out-of-Scope

**In scope for implementation:**
- AWS Public Cloud managed mode.
- Hybrid agent mode (control plane managed by Flowdeck, data/runtime in customer cloud).

**In scope for architecture contract (extension tracks):**
- Remote execution mode (Astronomer-like split scheduling/execution model).
- Private cloud packaging model.
- Multi-cloud data plane support (Azure/GCP).

**Out of scope for this version:**
- Full private-cloud implementation playbooks.
- Control plane deployment on non-AWS providers.

---

## 2. Canonical Terms and Principles

### 2.1 Canonical Terms

- **Control Plane:** Central policy, API, identity, billing, audit, desired state.
- **Data Plane:** Reconciliation and orchestration workers that apply desired state.
- **Runtime Plane:** Airflow workloads and supporting runtime components.
- **Desired State Version:** Monotonic deployment configuration version.
- **Effective-Once Outcome:** At-least-once delivery with idempotent convergence guarantees.

### 2.2 Non-Negotiable Principles

1. **Three-plane separation** is mandatory.
2. **Outbound-only control communication** from data planes is mandatory.
3. **API-first contract** governs UI, CLI, and automation parity.
4. **Idempotent reconciliation** is mandatory for all mutating operations.
5. **Tenant isolation by tier** is mandatory and policy-enforced.
6. **Auditability of all mutations** is mandatory.
7. **SLO and error-budget governance** is mandatory.

---

## 3. System Model and Trust Boundaries

### 3.1 Plane Responsibilities

| Plane | Responsibilities | Cannot Do |
|---|---|---|
| Control Plane | Identity, RBAC, desired state, orchestration intent, billing, audit, notifications | Execute customer DAG tasks directly |
| Data Plane | Converge actual state to desired state, provision infrastructure, roll out runtime config, report status/health | Override control-plane governance policy |
| Runtime Plane | Execute DAGs, schedule tasks, run workers/triggerers/webserver | Store authoritative platform governance policy |

### 3.2 Trust Boundaries

- **Boundary A:** Public client to control plane edge.
- **Boundary B:** Control plane to data plane control contract.
- **Boundary C:** Data plane to runtime infrastructure APIs.
- **Boundary D:** Tenant runtime endpoint access.

All boundaries require identity, authorization, encryption in transit, and audit trails.

### 3.3 Deployment Modes

| Mode | Control Plane | Data Plane | Runtime Plane | Primary Operator |
|---|---|---|---|---|
| Public Cloud | Flowdeck AWS | Flowdeck AWS | Flowdeck AWS | Flowdeck |
| Hybrid Agent | Flowdeck AWS | Customer cloud/VPC | Customer cloud/VPC | Shared |
| Remote Execution (extension) | Flowdeck AWS | Split coordinator + site executors | Customer/edge execution pools | Shared |
| Private Cloud (extension) | Customer-hosted package or vendor-hosted control profile | Customer environment | Customer environment | Customer with vendor support |

---

## 4. Control Contract (Normative)

### 4.1 API and Versioning Contract

- REST APIs are major-versioned by path (`/v1`, `/v2`).
- gRPC contracts use semantic versioning; no breaking changes within a major release.
- Deprecation support window is at least 12 months after successor GA.
- Client compatibility matrix is maintained for UI, CLI, and SDK.

### 4.2 Desired State Contract

Each deployment has:
- `deployment_id`
- `desired_state_version` (monotonic)
- `spec_hash`
- `revision_author`
- `policy_context`
- `effective_at`

No mutable reconciliation action can run without referencing a concrete desired version.

### 4.3 Control Channel Semantics

- Delivery: at-least-once.
- Ordering: guaranteed per deployment by desired version.
- Ack point: after data plane admission and persistence of command receipt.
- Replay: resumes from last committed version/offset.
- Backpressure: explicit throttle and retry directives.
- Timeout: command-level timeout policy with dead-letter routing and reason codes.

### 4.4 Status and Audit Contract

Every state transition event includes:
- actor (human/service/system)
- request and correlation IDs
- before/after state
- desired and observed version
- timestamp and region
- policy decision references

Audit records are immutable and exportable with retention policy controls.

---

## 5. AWS Public Cloud Reference Implementation (Primary)

### 5.1 Regional and Account Topology

- Multi-account strategy: control, shared-services, runtime, security, logging.
- At least two AZs for all production critical services.
- Region strategy:
  - Phase 1: single active region with warm DR.
  - Phase 2: active-active control read path, active-passive write path (as needed by scale/compliance).

### 5.2 Core AWS Services

- **Compute:** EKS for control/data/runtime Kubernetes workloads.
- **Database:** Aurora PostgreSQL (platform system of record and metadata variants).
- **Cache/Broker support:** ElastiCache Redis (queue/session/ephemeral state needs).
- **Object Storage:** S3 for artifacts, logs, backups, lineage exports.
- **Network:** VPC, private subnets, NLB/ALB, VPC endpoints, Transit Gateway as needed.
- **Security:** IAM/IRSA, KMS, Secrets Manager (or Vault), WAF, Shield.
- **Observability:** Managed Prometheus/AMP equivalents, CloudWatch, OpenTelemetry pipeline.

### 5.3 Control Plane Service Set

- API Gateway service.
- Identity and federation service.
- Org/workspace lifecycle service.
- Deployment service and state machine.
- Data plane registry and liveness service.
- Policy and quota service.
- Billing/metering service.
- Audit/event service.

### 5.4 Data Plane Service Set

- Agent gateway client (outbound control channel).
- Reconciler/orchestrator workers.
- Provisioning controller (infrastructure and release actions).
- Secrets sync and rotation workers.
- Health and metrics reporters.
- Drift detection loop.

### 5.5 Runtime Plane Composition

Per deployment:
- Airflow webserver.
- Scheduler.
- Triggerer.
- Worker pool (Celery and/or Kubernetes executor).
- Metadata DB and broker integrations.
- DAG and dependency delivery subsystem.

### 5.6 DAG Delivery Strategy

- **Default enterprise path:** OCI DAG bundles with signed provenance.
- **Optional compatibility path:** git-sync or object sync for migration scenarios.
- All delivery paths require integrity checks and version pinning.

### 5.7 Isolation Tiers

| Tier | Isolation Unit | Recommended Use |
|---|---|---|
| Shared | Namespace + network + quota | Cost-sensitive workloads |
| Enhanced | Dedicated node pools + strict policy | Production teams with moderate compliance needs |
| Dedicated | Cluster/account boundary | Regulated and high-scale enterprise tenants |

Tenant tier selection drives policy templates, quotas, SLO envelope, and support model.

---

## 6. Hybrid Agent Extension (Production Track)

### 6.1 Ownership Model

- Flowdeck owns control plane reliability and policy APIs.
- Customer owns underlying cloud account/network controls where data/runtime plane runs.
- Shared responsibility matrix is contractual and audited.

### 6.2 Bootstrap and Registration

- Agent installed by Helm/CLI bundle.
- One-time bootstrap token exchanged for short-lived workload identity credentials.
- Continuous cert/token rotation with expiry alarms.

### 6.3 Network Requirements

- Outbound-only egress from customer environment to control plane endpoints.
- Optional private transport patterns (AWS PrivateLink, equivalent in other clouds) for regulated customers.
- No inbound control-plane initiated connectivity required.

### 6.4 Operational Constraints

- Agent version skew policy: support N and N-1 major lines.
- Graceful degraded mode for transient control disconnects.
- Buffered non-terminal status with bounded local persistence.

---

## 7. Remote Execution Extension (Astronomer-Like Model)

### 7.1 Model Overview

- Scheduling and governance remain under Flowdeck control contract.
- Task execution workers can run in remote customer or edge execution pools.
- Assignment protocol routes task intents to authorized remote executors.

### 7.2 Core Requirements

- Executor identity and attestation before task acceptance.
- Work queue partitioning and fairness controls.
- Data egress policy per task class and tenant policy.
- Deterministic retry semantics and poison-task handling.

### 7.3 Security and Compliance

- Remote workers never gain broad control-plane mutation privileges.
- Sensitive payload handling follows encrypted envelope patterns.
- Execution audit chain links DAG run, task run, executor identity, and policy decision.

---

## 8. Private Cloud Extension (Enterprise Track)

### 8.1 Packaging Profiles

- **Vendor-managed control profile:** control plane still vendor-hosted, private data/runtime plane.
- **Customer-hosted platform profile:** packaged control + data + runtime for strict residency/isolation cases.

### 8.2 Private Cloud Requirements

- Installer with idempotent upgrade and rollback.
- Air-gapped artifact mirror support.
- License, entitlement, and feature gating model.
- Health telemetry export with customer approval controls.

### 8.3 Support Boundaries

- Clearly defined managed vs customer responsibilities.
- Upgrade cadence and support window policy.
- Escalation workflow and secure diagnostic artifact exchange.

---

## 9. Multi-Cloud Data Plane Strategy (Azure/GCP)

### 9.1 Abstraction Boundaries

Abstract only where necessary:
- cluster provisioning interface
- network policy interface
- secrets backend interface
- storage interface
- identity/workload auth interface
- load-balancing and ingress interface

Do not abstract provider-specific operational strengths when not required by product contract.

### 9.2 Capability Parity Matrix

For each provider, GA requires:
- security parity (identity, encryption, secrets)
- reliability parity (backup, restore, failover)
- observability parity (metrics, logs, traces, alerts)
- tenancy parity (isolation tiers, quota enforcement)
- cost-metering parity (billing dimensions)

### 9.3 Cloud-Specific Mappings

- AWS: EKS, Aurora, ElastiCache, S3, IAM, KMS.
- Azure: AKS, Azure PostgreSQL, Azure Cache for Redis, Blob Storage, Entra ID, Key Vault.
- GCP: GKE, Cloud SQL, Memorystore, GCS, IAM, Cloud KMS.

---

## 10. Security Architecture Contract

### 10.1 Threat Model Baseline

Threat classes:
- tenant cross-access
- credential theft
- supply chain compromise
- API abuse and privilege escalation
- control channel spoofing/replay
- data exfiltration

Each class maps to preventive, detective, and corrective controls.

### 10.2 Identity and Access

- SSO federation (SAML/OIDC) for human users.
- SCIM for enterprise lifecycle provisioning.
- RBAC baseline with ABAC extension hooks.
- Scoped service accounts with default expiry and rotation policy.
- Break-glass access with explicit approval and immutable audit.

### 10.3 Crypto and Secrets

- TLS 1.2+ in transit (policy target TLS 1.3 where supported).
- Encryption at rest via KMS-managed keys.
- BYOK option for enterprise tiers.
- Secrets never logged in plaintext; secret access requests are audited.

### 10.4 Supply Chain and Runtime Hardening

- Signed images and provenance attestation required for production rollout.
- SBOM generation and vulnerability policy gates.
- Admission controls enforce image source and policy checks.
- Runtime policies enforce least privilege and restricted pod security.

---

## 11. Reliability, HA, DR, and SLO Governance

### 11.1 Availability Model

- Control plane services deployed across multiple AZs.
- Stateful services configured for HA and automated failover.
- Data plane reconcilers horizontally scalable and idempotent.
- Runtime autoscaling independent from control-plane capacity.

### 11.2 Recovery Targets

| Failure Scenario | Target RPO | Target RTO |
|---|---:|---:|
| Single pod/node failure | near-zero | <5 min |
| Namespace/service disruption | <=15 min | <15 min |
| Cluster-level runtime loss | <=1 hour | 30-60 min |
| Regional warm-standby failover (enterprise) | <=5 min | <10 min |

### 11.3 SLO Catalog (Initial)

| SLO | Objective |
|---|---|
| Control plane API availability | 99.9% monthly |
| Deployment reconciliation success (platform-attributed) | 99.5% monthly |
| Reconciliation latency (P95) | <5 minutes |
| Data plane reconnect (P95) | <60 seconds |
| Audit event ingestion durability | 99.99% successful writes |

### 11.4 Error Budget Policy

- Fast burn rates trigger incident paging.
- Moderate burn rates trigger release freeze review.
- Sustained burn rates require reliability remediation before feature acceleration.

---

## 12. Tenancy, Quotas, and Governance

### 12.1 Tenant Hierarchy

`organization -> workspace -> deployment -> runtime resources`

### 12.2 Quota and Admission Controls

- API rate limits at org/workspace scopes.
- Deployment count, worker count, and storage quotas by tier.
- Fair-use enforcement for noisy-neighbor mitigation.
- Deterministic quota errors with actionable remediation hints.

### 12.3 Policy Engine

- Central policy decisions for naming, network posture, allowed executors, secret backend types, and max scale parameters.
- Policy evaluation references are attached to audit records.

---

## 13. Compliance, Data Residency, and Audit

### 13.1 Data Classification

- **Class A:** Control metadata (org/workspace/deployment state).
- **Class B:** Operational telemetry.
- **Class C:** Customer runtime artifacts/logs.
- **Class D:** Secrets and credentials.

### 13.2 Residency Policy

- Tenant region binding policy at org/workspace/deployment scopes.
- Residency-aware placement and backup controls.
- Explicit exceptions registry for cross-region operations.

### 13.3 Control Framework Mapping

Architecture supports mapping to:
- SOC 2
- HIPAA (mode and configuration dependent)
- GDPR
- PCI DSS (configuration dependent)

Evidence is generated through immutable audit logs, policy events, change records, and access logs with retention controls.

---

## 14. Observability and Operations

### 14.1 Telemetry Standards

- Metrics with tenant and component labels.
- Structured logs with correlation IDs.
- OpenTelemetry traces across API to reconciliation to runtime actions.

### 14.2 Alerting and Response

- Multi-window burn-rate alerts for SLOs.
- Component and tenant health alerts with ownership routing.
- Runbook links attached to actionable alerts.

### 14.3 Operational Runbooks

Required runbook classes:
- onboarding/provisioning failures
- upgrade rollback
- secret rotation failures
- control-channel instability
- cluster saturation
- regional DR invocation

---

## 15. Upgrade and Change Management

### 15.1 Release Channels

- `stable`: default enterprise channel.
- `canary`: selected tenant/workspace cohorts.
- `preview`: feature validation with explicit risk acceptance.

### 15.2 Upgrade Safety

- Expand/contract database migration strategy.
- Backward-compatible API changes within major versions.
- Controlled rollout with abort thresholds.
- Version compatibility matrix for Airflow and executors.

### 15.3 Airflow Version Policy

- New major Airflow support introduced via controlled release train.
- N-1 major supported for defined deprecation window.
- Forced upgrade only for critical security/compliance events.

---

## 16. Cost Governance and Metering

### 16.1 Metering Dimensions

- deployment lifecycle usage
- compute/runtime consumption
- storage and artifact retention
- network egress classes
- premium isolation and support tiers

### 16.2 FinOps Controls

- Cost anomaly detection and threshold alerts.
- Policy-driven scale limits and guardrails by tier.
- Chargeback/showback exports per organization/workspace.

### 16.3 Unit Economics Visibility

Track and review:
- cost per active deployment
- cost per successful task run
- storage cost per tenant lifecycle stage
- control-plane overhead ratios

---

## 17. Architecture Decision Register (Current)

### Frozen Decisions

1. Three-plane architecture remains canonical.
2. Outbound-only control communication is mandatory.
3. AWS public cloud is the primary implementation target.
4. Effective-once outcomes rely on idempotency + reconciliation, not exactly-once transport.
5. Tiered tenant isolation is mandatory.

### Open Decisions (Next ADR Cycle)

1. Final private-cloud packaging profile split and support scope.
2. Remote execution queue technology and scheduler placement specifics.
3. BYOK/HYOK key hierarchy for regulated enterprise profiles.
4. Multi-region active-active write strategy timeline.

---

## 18. Implementation Roadmap (Architecture Delivery)

### Phase 1 - AWS Public Cloud Hardening (Now)

- Finalize control contract and state semantics.
- Complete SLO instrumentation and error-budget automation.
- Enforce supply chain and admission controls.
- Publish tiered isolation policy packs.

### Phase 2 - Hybrid Production Maturity

- Expand private connectivity options.
- Improve agent fleet lifecycle controls and skew management.
- Add advanced customer-facing diagnostics and policy visibility.

### Phase 3 - Remote and Private Extensions

- Deliver remote execution alpha contract.
- Deliver private-cloud packaging reference profile.
- Define enterprise support operating model for private deployments.

### Phase 4 - Multi-Cloud Data Plane Expansion

- Launch provider conformance tests.
- Deliver Azure and GCP data plane profiles.
- Gate GA on parity matrix satisfaction.

---

## 19. Final Statement

This architecture intentionally separates **normative product contracts** from **mode-specific implementation details**. AWS public cloud is the production baseline. Hybrid, remote, private, and multi-cloud paths are designed as strict extensions of the same contracts rather than alternate products. This provides enterprise-grade reliability and governance now, while preserving long-term portability and deployment flexibility.

