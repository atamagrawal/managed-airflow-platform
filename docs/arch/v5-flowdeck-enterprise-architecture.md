# Flowdeck Platform — Enterprise Architecture Design Document

> **Version:** 5.0  
> **Last Updated:** April 2026  
> **Scope:** AWS Public Cloud first, designed to extend to Hybrid Agent, Remote Execution, Private Cloud, and other cloud providers  
> **Status:** Final Architecture Baseline (consolidates v3 → v4.6)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [AWS Reference Architecture](#3-aws-reference-architecture)
4. [Control Plane](#4-control-plane)
5. [Data Plane](#5-data-plane)
6. [Runtime Plane](#6-runtime-plane)
7. [End-to-End Flows](#7-end-to-end-flows)
8. [Network and Security](#8-network-and-security)
9. [Reliability, DR, and SLOs](#9-reliability-dr-and-slos)
10. [Observability and Operations](#10-observability-and-operations)
11. [Tenancy, Quotas, and Compliance](#11-tenancy-quotas-and-compliance)
12. [Extensibility — Hybrid, Remote Execution, Private, Multi-Cloud](#12-extensibility--hybrid-remote-execution-private-multi-cloud)
13. [Implementation Roadmap](#13-implementation-roadmap)

---

## 1. Executive Summary

Flowdeck is a managed Airflow platform that lets enterprises run production data pipelines without owning the underlying orchestration platform. The core challenge is operational: customers want managed simplicity, but enterprise buyers also want their data and compute to stay inside their own networks, satisfy regional residency rules, and integrate with their existing security tooling. A single deployment topology cannot satisfy all of these audiences.

This document defines an architecture that solves this problem by separating *what the platform decides* from *where customer workloads run*. Flowdeck operates a centralised Control Plane that owns identity, policy, billing, audit, and the desired state of every deployment. Wherever a customer chooses to run, an outbound-only Data Plane reconciles real infrastructure to that desired state, and a Runtime Plane runs the actual Airflow workloads. The same APIs and the same control contracts work whether the workloads run inside Flowdeck's AWS account, inside a customer VPC under a hybrid agent, in a remote-execution model where only task workers are customer-owned, in a packaged private deployment, or eventually on Azure or GCP.

The implementation baseline for this version is **AWS public cloud**. AWS is what we build, harden, and ship first. Every other deployment mode is treated as a strict extension that preserves the same contracts, never as a parallel product. This decision is what makes the architecture realistic to operate at enterprise scale: there is one Control Plane, one set of APIs, one state machine, one audit pipeline, and one reconciliation loop, regardless of where the customer's data plane lives.

The rest of this document explains how that works, end-to-end.

---

## 2. Architecture Overview

### 2.1 The Three-Plane Model

Flowdeck is organised into three planes, and the boundary between them is the most important design decision in the entire system.

The **Control Plane** is the brain. It is always operated by Flowdeck, runs in Flowdeck's AWS account, and is the only place where business policy lives. When a user creates an organisation, invites a teammate, defines a workspace, configures SSO, sets a quota, or describes a deployment, that intent is recorded here. The Control Plane does not run any Airflow code. It does not schedule DAGs. It does not execute tasks. Its job is to know the *desired* state of the world and to publish that intent to the right place.

The **Data Plane** is the hands. It picks up the desired state, compares it to what is actually running, and converges the two. It runs Helm releases, talks to a Kubernetes API server, provisions managed services like RDS and ElastiCache, rotates secrets, and reports back what it observes. In public cloud mode, the Data Plane runs inside Flowdeck's AWS VPC. In hybrid mode, exactly the same code runs inside a customer's VPC. The deployment target moves; the contract does not.

The **Runtime Plane** is the workload. It is the actual Airflow installation: webserver, scheduler, triggerer, workers, the metadata database, the Celery broker, and the DAG storage. The Runtime Plane never makes business decisions. It runs whatever the Data Plane has applied to it.

This separation matters because each plane has a fundamentally different lifecycle and risk profile. The Control Plane is multi-tenant and rarely changes per customer. The Data Plane is per-environment, performs all the actual cloud mutations, and is the natural unit of *blast radius*. The Runtime Plane changes constantly — DAGs are deployed, workers scale up and down, tasks succeed and fail. By keeping these concerns physically separate, Flowdeck can upgrade a runtime cluster without touching the Control Plane, replace the Data Plane reconciler without touching customer workloads, and move the Data Plane to a different network without rewriting the Control Plane.

### 2.2 The Outbound-Only Model

The single most consequential constraint in the architecture is that the Control Plane never opens an inbound connection into a Data Plane. All communication originates from the Data Plane Agent, flows outbound to the Control Plane's public endpoints, and uses long-lived authenticated streams for command delivery and status reporting.

This is not a stylistic preference. Enterprise security teams will not accept an architecture that requires them to open inbound firewall holes from a vendor's network into theirs. By making the Data Plane outbound-only by design, hybrid mode requires nothing more than an outbound HTTPS allow-list, the same contract we already use in public cloud mode. It is also what allows private connectivity options like AWS PrivateLink to be added later without changing the protocol.

Because the channel is outbound-only and long-lived, the Control Plane treats it as a backpressure-aware command stream rather than a synchronous RPC. Commands carry a monotonically increasing `desired_state_version` for each deployment, the Data Plane acknowledges them after durable acceptance (not after completion), and on reconnect the Data Plane replays from its last committed checkpoint. The result is at-least-once delivery with effectively-once outcomes, because the underlying reconciliation is idempotent.

### 2.3 Deployment Modes

The same architecture supports multiple deployment modes by changing only *where the Data Plane lives*.

| Mode | Control Plane | Data Plane | Runtime Plane | Network Direction |
|---|---|---|---|---|
| **Public Cloud** (this doc) | Flowdeck AWS | Flowdeck VPC | Flowdeck VPC | Internal |
| **Hybrid Agent** | Flowdeck AWS | Customer VPC | Customer VPC | Outbound from customer |
| **Remote Execution** | Flowdeck AWS | Flowdeck VPC (scheduler), customer pools (workers) | Mixed | Outbound from worker pools |
| **Private Cloud** | Customer-hosted Control Plane profile | Customer VPC | Customer VPC | Fully internal to customer |

Public Cloud is the GA mode and the focus of this document. Hybrid is a first-class extension that this architecture is explicitly designed to support, and the contracts described later in this document are the same contracts hybrid uses. Remote execution and private cloud are extension tracks that are described in Section 12.

### 2.4 High-Level Topology

At the highest level, Flowdeck looks like this on AWS:

```
                ┌────────────────────────────────────────────────┐
                │           FLOWDECK AWS ORGANISATION            │
                │                                                │
   Users / CI ──┼──► flowdeck-prod-control                       │
   CLI / UI     │   ┌─────────────────────────────────────┐      │
                │   │           CONTROL PLANE             │      │
                │   │  WAF · CloudFront · ALB             │      │
                │   │  API Gateway · Identity · RBAC      │      │
                │   │  Org · Workspace · Deployment Svc   │      │
                │   │  DP Registry · Audit · Notification │      │
                │   │  Aurora PostgreSQL · Redis · S3     │      │
                │   │  NATS JetStream (event bus)         │      │
                │   └────────────────┬────────────────────┘      │
                │                    │ outbound mTLS gRPC        │
                │   ┌────────────────▼────────────────────┐      │
                │   │  flowdeck-prod-runtime              │      │
                │   │  ┌─────────────────────────────┐    │      │
                │   │  │      DATA PLANE             │    │      │
                │   │  │  DP Agent (gRPC client)     │    │      │
                │   │  │  Deployment Controller      │    │      │
                │   │  │  Health Monitor             │    │      │
                │   │  │  Metrics Collector          │    │      │
                │   │  │  Secret Distributor         │    │      │
                │   │  └────────────┬────────────────┘    │      │
                │   │               │ Kubernetes API      │      │
                │   │  ┌────────────▼────────────────┐    │      │
                │   │  │      RUNTIME PLANE          │    │      │
                │   │  │  Per-deployment namespace   │    │      │
                │   │  │  Webserver · Scheduler      │    │      │
                │   │  │  Triggerer · Workers        │    │      │
                │   │  │  RDS · ElastiCache · S3     │    │      │
                │   │  └─────────────────────────────┘    │      │
                │   └─────────────────────────────────────┘      │
                │                                                │
                │   flowdeck-security · flowdeck-observability   │
                └────────────────────────────────────────────────┘
```

The remainder of this document walks through each layer in detail and then shows how the same picture extends to hybrid, remote, private, and multi-cloud.

---

## 3. AWS Reference Architecture

### 3.1 Account Strategy

Flowdeck operates inside an AWS Organisation with separate accounts for production control, production runtime, security tooling, and observability. The reason for separating control and runtime is blast radius. The Control Plane handles every customer's identity, billing, and audit data, so it requires a stricter IAM and change-management posture than the runtime accounts that come and go as customer deployments are created and destroyed. Putting them in different accounts means an IAM mistake or a compromised credential in one cannot compromise the other, and AWS Service Control Policies at the Organisation level enforce guardrails that no individual account administrator can override — for example, disallowing the creation of long-lived static IAM access keys anywhere in the organisation.

| Account | Purpose |
|---|---|
| `flowdeck-prod-control` | Control Plane services, platform database, audit data |
| `flowdeck-prod-runtime` | Data Plane and Runtime Plane EKS clusters, customer deployments |
| `flowdeck-security` | GuardDuty, Security Hub, CloudTrail archive, AWS Config aggregator |
| `flowdeck-observability` | Long-term metrics, log archive, traces |
| `flowdeck-staging` / `flowdeck-dev` | Mirror of production topology for engineering |

### 3.2 Regional Strategy

In Phase 1, Flowdeck operates a single primary AWS region with a warm-standby secondary region for disaster recovery. The warm standby holds replicated platform data and a minimal footprint of control-plane services, ready to take over if the primary region fails. Customer runtime data is replicated to the secondary region only for enterprise-tier deployments that have opted into cross-region DR.

In Phase 2, multiple primary regions are introduced for data residency. Each customer organisation is bound to a "home region" at creation time, and all of that customer's deployments, metadata, audit events, and backups stay within that region's boundary. The Control Plane services run in every primary region, but they are not active-active for writes — each customer is served by exactly one region's writer, with cross-region replication used only for read availability and DR. This avoids the consistency complexity of active-active writes while still satisfying residency requirements.

### 3.3 Network Topology

Each account hosts its own VPC. The Control Plane VPC and the Runtime VPC are intentionally not peered; the only path between them is the public Control Plane API endpoint, which the Data Plane reaches through outbound HTTPS. This is deliberate: it means hybrid deployments behave identically to managed deployments, because the managed Data Plane uses exactly the same outbound channel that a customer-hosted agent would use.

Within each VPC, subnets are split into public and private tiers across at least two availability zones. Only the load balancers and NAT gateways live in public subnets; everything else — EKS nodes, RDS, ElastiCache — lives in private subnets. AWS service traffic (S3, KMS, Secrets Manager, ECR, STS) goes through VPC interface and gateway endpoints rather than NAT, which both reduces NAT cost and keeps that traffic off the public internet.

```
┌───────────────────────── Region (us-east-1) ─────────────────────────┐
│                                                                      │
│  ┌──────────── Control Plane VPC (10.0.0.0/16) ─────────────┐        │
│  │  Public:  ALB, WAF                                        │        │
│  │  Private: EKS (control services), Aurora, Redis, NATS     │        │
│  │  Endpoints: S3, KMS, SecretsManager, STS, ECR, Logs       │        │
│  └────────────────────────────┬──────────────────────────────┘        │
│                               │ public HTTPS (outbound from DP)       │
│  ┌────────────────────────────▼──────────────────────────────┐        │
│  │  Runtime VPC (10.10.0.0/16)                               │        │
│  │  Private: EKS Data Plane, EKS Runtime, RDS (per-tenant),  │        │
│  │           ElastiCache (per-tenant), NLB for tenant ALBs   │        │
│  │  Endpoints: S3, KMS, SecretsManager, ECR, Logs            │        │
│  └────────────────────────────────────────────────────────────┘        │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.4 Tenant Isolation Tiers

Flowdeck supports three isolation tiers because customer needs vary widely. Most workloads do not require a dedicated cluster — they require strong namespace isolation, predictable performance, and a clean blast-radius boundary at the cloud-account level. A small fraction of regulated and high-criticality workloads do require a dedicated cluster. The architecture supports both without forking the codebase.

In **Starter** tier, deployments share an EKS cluster, share a node pool, and are isolated by Kubernetes namespace, ResourceQuota, NetworkPolicy, IRSA-scoped service accounts, and per-tenant Secrets Manager paths. In **Pro** tier, deployments share an EKS cluster but get their own dedicated node pool tainted to that organisation, which removes the noisy-neighbour problem at the node level. In **Enterprise** tier, the organisation gets its own EKS cluster, its own RDS and ElastiCache, and its own ALB. **Hybrid** customers are always cluster-isolated by definition because they are running in their own VPC.

Tier selection is encoded in the desired state of each deployment, validated at admission time, and drives the Data Plane's provisioning logic. Importantly, switching tiers is itself a controlled operation: an Enterprise customer can be onboarded by initially provisioning the dedicated cluster and then registering the deployment, all through the same API and the same reconciliation loop.

---

## 4. Control Plane

### 4.1 What the Control Plane Is

The Control Plane is the system of record for everything Flowdeck decides. It owns who you are, what organisations you belong to, what workspaces those organisations contain, what deployments live in those workspaces, and what each of those deployments is supposed to look like. It also owns every audit event, every billing event, and every policy decision the platform has ever made.

Importantly, the Control Plane does not own *what is currently running*. That is observed state, which the Data Plane reports. The Control Plane owns *what should be running* — desired state — and the Data Plane is responsible for closing the gap. This split is what makes the system resilient: if the Data Plane disconnects, customers can still update desired state through the API, and the Data Plane catches up when it reconnects.

### 4.2 Ingress Layer

External traffic enters the Control Plane through a layered ingress designed to absorb attacks before they reach application code. AWS WAF v2 sits at the perimeter and applies OWASP rule sets, IP-level rate limiting, and reputation lists. Static UI assets are served by CloudFront, which terminates TLS at edge locations and provides AWS Shield Standard for DDoS mitigation. Dynamic API traffic goes to an Application Load Balancer with two target groups: an HTTP/2 target group for REST traffic on `api.flowdeck.io`, and a gRPC target group for Data Plane Agent traffic on `grpc.flowdeck.io`. A separate WebSocket endpoint at `wss://ws.flowdeck.io` carries real-time deployment status and log streams to the UI.

The reason for splitting REST and gRPC at the load balancer is that the two have different authentication models. REST traffic uses bearer JWTs or API keys, validated at the application layer. gRPC traffic from the Data Plane uses both mTLS *and* a JWT — neither alone is sufficient to impersonate an agent. Putting them on different hostnames lets us apply different ALB listener policies and different ACM certificates to each.

### 4.3 API Gateway Service

The API Gateway is the only inbound entry point to Control Plane application logic. It is a stateless Kubernetes Deployment, runs three replicas at minimum, and scales horizontally based on CPU and request rate. It does four things: it terminates the protocol (REST, gRPC, or WebSocket), it authenticates the request, it applies rate limits, and it forwards to the appropriate downstream service.

REST authentication accepts either a bearer JWT from a logged-in user or an API key from a CLI/CI agent. gRPC authentication requires both an mTLS client certificate signed by Flowdeck's internal CA and a JWT in the gRPC metadata. WebSocket authentication uses a short-lived (5-minute) token issued specifically for the WebSocket session, separate from the API JWT — this means a stolen WebSocket token does not grant API access.

Rate limits are layered. WAF enforces per-IP limits at the edge. The API Gateway enforces per-user and per-organisation limits using atomic counters in Redis, with burst allowances so that legitimate spikes are not rejected. A circuit breaker on every downstream call opens at a 50% error rate over a 10-second window, returning 503 with a `Retry-After` header until the downstream service recovers; this prevents one degraded service from queueing up the entire API.

### 4.4 Identity Service

Every request that gets past the API Gateway is validated by the Identity Service before it reaches any business logic. Identity is the only service in the platform allowed to mint or validate authentication tokens.

Human users authenticate by email and password (bcrypt cost 12, with TOTP-based MFA), or through an enterprise IdP via SAML 2.0 or OIDC. SSO group memberships from the IdP are mapped to Flowdeck workspace roles, so adding a user to the right Okta group automatically grants the right access. Successful authentication returns a JWT signed with RS256, with a 1-hour expiry, plus an opaque refresh token that is rotated on every use and has a 30-day window. The JWT carries the user ID, organisation ID, role assignments, scopes, and a unique `jti` used for revocation.

Service authentication uses API keys for CLI and CI/CD, and short-lived service tokens for Data Plane Agents. API keys are stored as SHA-256 hashes — the plaintext is shown to the user exactly once, at creation. Data Plane service tokens are scoped so that they can only call gRPC endpoints; they cannot impersonate a user against the REST API even if leaked.

Authorisation is a hierarchical RBAC model. At the platform level, only Flowdeck staff hold the super-administrator role. Inside an organisation, `org:owner` has full control including billing, `org:admin` manages workspaces and members, and `org:member` has access only to assigned workspaces. Inside a workspace, `workspace:admin` manages everything in the workspace, `workspace:developer` can deploy and modify DAGs and connections, and `workspace:viewer` is read-only. Roles are additive and inherited downward; an `org:owner` is implicitly an admin of every workspace. Role decisions are cached in Redis for five minutes to keep authorisation cheap on the hot path.

Token revocation is implemented as a Redis blocklist keyed by `jti`. Every request validates the JWT signature and then checks the blocklist; entries expire at the token's natural expiry time. This is what lets us instantly revoke access on logout, password change, or security incident without waiting for tokens to expire.

### 4.5 Organisation, Workspace, and Deployment Services

These three services form the customer-facing hierarchy. An **Organisation** is the billing and policy boundary — usually a company. Creating an organisation provisions a default workspace, a quota record sized for the chosen plan, and a billing record linked to a Stripe customer ID. Organisation slugs are globally unique because they appear in URLs and in tenant subdomains. Quotas — maximum active deployments, total CPU/memory/storage — are enforced by the Organisation Service and checked by the Deployment Service before any resource creation, with a short-lived Redis lock around the check-and-create window to prevent races.

A **Workspace** is a logical grouping of deployments inside an organisation, typically aligned to a team or environment such as "data-engineering" or "production". Workspaces have their own member lists and role assignments independent of the organisation, and they carry shared configuration like the default Airflow version, the allowed executor types, and the CI/CD enforcement flag. When CI/CD enforcement is enabled, deployments in that workspace can only be modified by service-account API tokens with the `DEPLOYER` role; humans can read but cannot push changes through the UI or CLI. This is how customers enforce "all production changes go through the pipeline".

A **Deployment** is one running Airflow environment. The Deployment Service is the orchestrator of its lifecycle. It holds the desired state for every deployment as a JSON document containing the Airflow version, executor type, resource profile, environment variables, connection references, and variable references. When that document changes, the Deployment Service increments a monotonic `desired_state_version` and emits a versioned command to the Data Plane. The deployment moves through a state machine — `PENDING → PROVISIONING → CONFIGURING → STARTING → RUNNING`, with branches for `UPDATING`, `SCALING`, `STOPPING`, `STOPPED`, `TERMINATING`, `TERMINATED`, and `ERROR` — and every transition is recorded in the audit log with the actor, timestamp, reason, and correlation ID.

### 4.6 Data Plane Registry

The Data Plane Registry is what allows the Control Plane to talk to many Data Planes — Flowdeck-managed ones in different regions, hybrid ones in customer VPCs, and eventually private and remote-execution ones — through a single contract.

Each Data Plane registers itself on startup, presenting its mTLS certificate and a registration token. The registry stores its ID, type (`MANAGED`, `HYBRID`, `PRIVATE`, `REMOTE`), region, gRPC endpoint, current health status (`HEALTHY`, `DEGRADED`, `OFFLINE`), capacity, last heartbeat timestamp, and cloud account metadata. When the Deployment Service emits a command, it asks the registry which Data Plane should receive it, and the registry routes the command based on the deployment's region and the target Data Plane's health. If the target is `HEALTHY`, the command goes through. If `DEGRADED`, it goes through but raises an alert. If `OFFLINE`, it queues in NATS until the Data Plane reconnects and replays.

Heartbeats arrive every 15 seconds. After 60 seconds of silence the Data Plane is marked `DEGRADED`; after five minutes it is marked `OFFLINE` and customers whose deployments are affected are notified through their configured alert channels. This separation between "I haven't heard from you" (degraded, internal alert) and "you are clearly down" (offline, customer alert) is deliberate and avoids alert fatigue during normal network blips.

### 4.7 Event Bus (NATS JetStream)

Internal communication between Control Plane services and to the Data Plane Agents is carried over NATS JetStream, deployed as a 3-node StatefulSet on the Control Plane EKS cluster with EBS gp3 persistence. NATS gives us durable, ordered, acknowledged delivery with replay, which is exactly what the desired-state model needs.

Three streams matter most. `deployments.commands` is a work-queue stream from the Deployment Service to Data Plane Agents — a command is removed only after the agent acknowledges acceptance, so an agent that crashes mid-handling will see the command again on restart. `deployments.status` is a limits-based stream that keeps the last 100 status updates per deployment, which is enough for the UI's history view and for debugging without growing unbounded. `agents.heartbeat` keeps only the most recent heartbeat per agent and is what the registry reads to update health.

Crucially, the Data Plane Agent always initiates the NATS connection outbound over TLS using its mTLS certificate. The NATS cluster never reaches into a customer's network. This is what makes the same code work in public cloud and hybrid mode without protocol changes.

### 4.8 Audit, Billing, and Notifications

The **Audit Logger** sits as middleware on the API Gateway and writes an entry for every mutating action. Each entry records the organisation, workspace, actor (user or service account), source IP, action type (`deployment.create`, `connection.update`, etc.), resource type and ID, before/after state snapshots, and the correlation ID. Audit entries are immutable once written and partitioned monthly in Aurora. After 90 days they are exported to S3 in Parquet format for Athena queries and dropped from the database; the S3 archive is retained for seven years to support SOC 2 and customer compliance audits.

The **Billing Service** consumes hourly aggregates from the Data Plane's Metrics Collector — worker-hours, DAG run count, task execution count, storage GB-hours — and rolls them into monthly invoices synced to Stripe. It also enforces spend limits: when an organisation crosses its configured spend cap, new deployments are blocked and the owner is notified, but existing deployments keep running so production isn't broken.

The **Notification Service** routes alerts and platform events to customer-configured destinations: email through SES, Slack via webhook, PagerDuty via the Events v2 API, or generic HTTP webhooks. Routing rules are configured at the workspace level so different teams can send different alert classes to different places.

### 4.9 Data Layer

The Control Plane's primary store is **Aurora PostgreSQL 15** in Multi-AZ configuration, with one writer and two readers. Read traffic — deployment listing, audit log retrieval, metrics aggregation — goes to readers; writes go to the writer. PgBouncer in transaction mode runs as a sidecar to API Gateway pods, allowing many short-lived application connections to share a small pool of Aurora connections; without this, connection storms during deploys would saturate Aurora. All data is encrypted at rest with KMS customer-managed keys, automated backups are retained for seven days with point-in-time recovery, and monthly snapshots are exported to S3 for long-term retention.

The desired state of every deployment is stored as JSONB in the `deployments` table, which lets the schema evolve without migrations every time we add a configuration field. Observed state is stored separately, so that the Data Plane's status updates never collide with desired-state writes from the API.

**ElastiCache Redis** in Cluster mode handles short-lived, high-throughput data: the JWT revocation blocklist, per-user and per-organisation rate-limit counters, WebSocket session state, the 5-minute RBAC decision cache, and the distributed locks used during quota check-and-create. All Redis traffic is TLS-encrypted in transit and at rest.

**S3** holds anything that should persist beyond the lifetime of a database row: archived audit logs, Terraform remote state, CLI release artifacts, and periodic platform configuration snapshots used for disaster recovery.

---

## 5. Data Plane

### 5.1 What the Data Plane Is

The Data Plane is where intent meets reality. It receives commands from the Control Plane, provisions and manages cloud resources and Kubernetes workloads, monitors their health, and reports actual state back. In public cloud mode it runs in Flowdeck's runtime VPC. In hybrid mode it runs in a customer's VPC. The code is identical — only the deployment target changes. That property is the single biggest reason the Data Plane is a separate component rather than part of the Control Plane.

The Data Plane is composed of one externally-facing component (the **Agent**) and a small set of internal controllers behind it. Only the Agent has credentials to talk to the Control Plane; the internal controllers receive work from the Agent through in-cluster channels.

### 5.2 The Data Plane Agent

The Agent is the sole bridge between the Control Plane and everything else inside the Data Plane. It is a Kubernetes Deployment in the `flowdeck-system` namespace, runs as a single replica per Data Plane (with leader election if scaled), and is the only component allowed to hold Control Plane credentials.

On startup, the Agent registers itself with the Control Plane's Data Plane Registry by presenting its mTLS client certificate (initially issued by Vault PKI with a 30-day validity) and a registration token. Once registered, it opens a persistent bidirectional gRPC stream to the Control Plane API Gateway. That stream is the channel for everything: command delivery from the Control Plane to the Agent, status updates from the Agent back to the Control Plane, and 15-second heartbeats. Authentication is dual-layer — both mTLS and a JWT — and the Agent always initiates the connection.

When a command arrives on the `deployments.commands` NATS stream, the Agent does four things in order. First, it validates that the command's `desired_state_version` is greater than the version it has already applied for that deployment; if not, the command is a duplicate and is silently acknowledged. Second, it dispatches the command to the appropriate internal controller — usually the Deployment Controller. Third, it acknowledges the NATS message *only after* the controller has accepted the work and persisted the intent locally (not after completion). Fourth, as the controller progresses, the Agent publishes status updates back to `deployments.status` so the Control Plane and the user-facing UI can follow along.

The acknowledge-on-acceptance rule is what gives us at-least-once delivery without losing work if the Agent restarts mid-execution. If the Agent crashes after accepting but before completing, the controller's local state will show the work in progress on restart and resume; the NATS message has already been acknowledged so it won't be re-delivered, but the local state machine is the source of truth.

The Agent also manages its own credential rotation. Seven days before the mTLS certificate expires, it requests a new one from the Control Plane, swaps it into the gRPC connection, and the old certificate is revoked. JWTs are rotated every 24 hours with a 30-minute overlap window during which both old and new are valid. This means there is no rotation downtime and no operator action required for normal credential lifecycle.

The Agent itself runs a small state machine: `REGISTERING → HEALTHY → RECONCILING ↔ HEALTHY → DEGRADED → OFFLINE`. `DEGRADED` is entered after 60 seconds without a successful heartbeat round-trip, and `OFFLINE` is entered after five minutes — the same thresholds the registry uses on the Control Plane side, so both sides agree on the agent's state.

### 5.3 Deployment Controller

The Deployment Controller is the workhorse of the Data Plane. When the Agent dispatches a command, the Controller is what actually does the work of converging real infrastructure to desired state.

Provisioning a new deployment runs in three phases, each with bounded timeouts and explicit failure modes. **Phase 1 — Infrastructure** creates the cloud-managed dependencies: an RDS PostgreSQL cluster for the Airflow metadata DB, an ElastiCache Redis cluster for the Celery broker, a per-deployment S3 bucket or EFS volume for DAG storage, an IAM role for IRSA, and the security groups that wire them together. These are created via Terraform, with state stored remotely in S3 and locked via DynamoDB. **Phase 2 — Runtime** applies the Airflow Helm chart into a per-deployment namespace, with values rendered from desired state — the Airflow version, executor, resource limits, environment variables, and connection references. **Phase 3 — Validation** waits for readiness probes to succeed, the scheduler heartbeat to register, and the webserver to respond on its health endpoint, and then publishes the deployment's endpoint information back to the Control Plane.

If a phase fails, the recovery policy is phase-specific. Phase 1 failures are usually retryable — quota errors, transient AWS API failures — and the Controller retries with exponential backoff up to a configurable budget. Phase 2 failures are usually configuration errors and are surfaced with a reason code so the user can see what's wrong. Phase 3 failures usually mean the workload itself is broken (DAG parse errors, dependency conflicts) and the deployment is left in a `DEGRADED` state with logs surfaced to the customer rather than rolled back automatically — automatic rollback of a partial deployment can do more harm than good when the customer needs to debug.

Updates and scaling are simpler. The Controller computes the diff between the new desired state and the current observed state, generates the minimal Helm release update, and applies it. Because Helm itself is idempotent and the controller uses release versioning, applying the same desired-state version twice is a no-op. This is what makes the at-least-once command channel safe.

### 5.4 Health Monitor

The Health Monitor watches every deployment's runtime components continuously, independent of the command channel. It checks the webserver's `/health` endpoint, the scheduler's heartbeat in the metadata DB, the triggerer's liveness, the worker pod count and readiness, the metadata DB connection, and the broker connection. Each component reports `HEALTHY`, `DEGRADED`, or `UNHEALTHY`, and these roll up into a deployment-level health state.

Component-level checks are tier-aware. Enterprise deployments get tighter thresholds because they pay for tighter SLOs; Starter deployments get looser thresholds to avoid alert noise on shared infrastructure. When a deployment transitions to `DEGRADED` or `UNHEALTHY`, the Monitor publishes an event to `deployments.status` and the Control Plane's Notification Service routes it to the customer's configured channels. For some failure modes — a crashed scheduler pod, a stuck worker — the Controller can take automated remediation (restart the pod, drain and replace the node) before the customer ever sees the alert.

### 5.5 Metrics Collector and Secret Distributor

The **Metrics Collector** scrapes Prometheus metrics from every Airflow component in every deployment and writes them in two places. Operational metrics — pod CPU, memory, scheduler latency — are written to the regional Prometheus/AMP instance for SRE and customer dashboards. Billable metrics — worker-hours, DAG run count, task execution count, storage usage — are aggregated hourly and pushed to the Control Plane's Billing Service via the same gRPC stream.

The **Secret Distributor** is what keeps customer secrets in sync between the configured backend (AWS Secrets Manager, Vault, or for hybrid customers, their own backend) and the Airflow runtime. Connection passwords, API keys, and Airflow Variables marked as secret are never stored in the Control Plane database; the Control Plane stores only references, and the Distributor materialises them as Kubernetes Secrets in the deployment namespace at runtime, with IRSA scoping the read permissions to exactly the secrets that deployment is allowed to see. Rotation is event-driven: when a secret changes in the backend, the Distributor updates the Kubernetes Secret and triggers a rolling restart of the affected pods if needed.

---

## 6. Runtime Plane

### 6.1 Composition

A deployment's Runtime Plane is a per-deployment Kubernetes namespace inside the runtime EKS cluster, plus the cloud-managed dependencies that namespace talks to. The namespace contains the Airflow webserver, scheduler, triggerer, and worker pool, the Helm release that owns them, the ConfigMaps and Secrets that configure them, and the IRSA-bound service accounts that give them scoped AWS permissions. Outside the namespace, but dedicated to this deployment, sit its RDS metadata database, its ElastiCache broker (if using CeleryExecutor), and its S3 bucket or EFS volume for DAG storage.

Per-deployment namespaces matter because they are how Kubernetes-native isolation works. A `ResourceQuota` caps the deployment's CPU, memory, and pod count. A `NetworkPolicy` defaults to deny-all-ingress and explicitly allows only the ingress controller. A `LimitRange` prevents misconfigured DAGs from requesting absurd resources. Pod Security Standards are set to `restricted`, which blocks privileged containers, host networking, and writable root filesystems. Together these mean that even if a customer's DAG runs malicious code in a worker pod, that code cannot reach another customer's namespace.

### 6.2 Executor Choice

Flowdeck supports both **CeleryExecutor** and **KubernetesExecutor**, and the choice is declared in the deployment's desired state and validated at admission time. Celery is the default for most workloads because it has predictable performance, low task-startup overhead, and works well with autoscaled long-running worker pools. Kubernetes is offered for workloads that need pod-per-task isolation, heterogeneous resource profiles per task (a small task next to a 32-core ML training task), or per-task IAM scoping. The Data Plane's reconciliation logic is the same for both — only the rendered Helm values differ.

### 6.3 DAG Delivery

How DAGs get into a deployment is a deceptively important design question. The default enterprise path is **OCI DAG bundles**: the customer's CI pipeline builds a versioned, immutable container image containing the DAGs and their Python dependencies, signs it with cosign, pushes it to a registry, and submits the resulting digest to the Flowdeck API. The Data Plane pulls the image into the deployment, verifies the signature, and rolls the workers and scheduler to pick up the new bundle. This gives reproducibility, rollback, supply-chain provenance, and cache-friendly distribution at the cost of requiring a build step.

For customers migrating from existing setups, **git-sync** and **object sync from S3** are also supported, with controlled branch/tag policy and integrity checks. These paths are explicitly second-class — they exist for compatibility, not as the recommended pattern — and CI/CD enforcement at the workspace level can be configured to require OCI bundles in production.

In every case, the artifact's digest is recorded in the deployment's audit trail along with the `desired_state_version` it was applied at, so a customer can reconstruct exactly which DAG code was running at any point in time.

### 6.4 Autoscaling

Worker scaling is driven by KEDA, which watches the Celery queue depth in Redis (or scheduler queue depth for KubernetesExecutor) and scales the worker Deployment up or down to keep pending tasks below a configurable threshold. KEDA is good at this because it is event-driven; it can scale to zero during quiet hours and back up in seconds when work appears.

Node scaling is managed independently by Karpenter on the runtime EKS cluster. Karpenter watches for unschedulable pods and provisions nodes that fit them, choosing instance types from a configured list to balance cost and availability. The choice to keep worker scaling and node scaling independent — rather than tying them together with the older Cluster Autoscaler model — is what lets us scale workers in seconds rather than minutes during burst loads.

Scale-down has a drain policy. When KEDA scales workers down, the scaler first asks Airflow's worker to stop accepting new tasks, waits for in-flight tasks to finish (up to a tier-configured timeout), and only then terminates the pod. This dramatically reduces task interruption from scale-down events at the cost of a slightly slower shrink — the right trade-off for production workloads.

### 6.5 Routing to Tenant Airflow UIs

Each deployment gets its own Airflow webserver, and customers reach it at a per-deployment hostname like `<org-slug>-<deployment-slug>.airflow.flowdeck.io`. A single ALB in the runtime VPC fronts every webserver in the cluster, with host-based routing rules generated from the Data Plane Registry. The customer-facing TLS certificate is issued by ACM with SNI for the wildcard `*.airflow.flowdeck.io`, and the Identity Service's SSO is enforced at the ALB layer via JWT validation before traffic reaches the webserver pod, so a user who is not authenticated against Flowdeck cannot even reach Airflow's own login page.

---

## 7. End-to-End Flows

The architecture is easier to understand as flows than as components. The five flows below describe the most important paths through the system.

### 7.1 Deployment Creation

When a user submits `POST /v1/deployments`, the request enters through WAF, CloudFront, and the ALB, lands at the API Gateway, and is authenticated and authorised by the Identity Service. The Deployment Service validates the request against the workspace's policy and the organisation's quota, holds a brief Redis lock to prevent racing the quota check, and writes a new deployment row in Aurora with `desired_state_version = 1` and state `PENDING`. It then publishes a `CREATE` command to `deployments.commands` carrying the deployment ID and the full desired-state JSON.

The Data Plane Agent pulls the command, validates its version, and dispatches it to the Deployment Controller. The Controller transitions the deployment to `PROVISIONING` and publishes that status back. It then runs Phase 1 (infrastructure), Phase 2 (runtime), and Phase 3 (validation), with a status update emitted at each transition. Once the webserver responds healthy, the Controller registers the tenant ALB hostname in Route 53, transitions the deployment to `RUNNING`, and publishes the endpoint information. The user sees their new deployment go from `PENDING` to `RUNNING` over the WebSocket stream in roughly 8–12 minutes for a Pro-tier deployment, dominated by RDS provisioning.

```
User → ALB → API Gateway → Identity → Deployment Service
                                  │
                                  ▼
                  Aurora (desired_state_version=1, PENDING)
                                  │
                                  ▼
                  NATS deployments.commands (CREATE)
                                  │
                                  ▼
                          Data Plane Agent
                                  │
                  ┌───────────────┼───────────────┐
                  ▼               ▼               ▼
             Phase 1          Phase 2         Phase 3
          (Terraform)         (Helm)        (Validation)
                                  │
                                  ▼
                    Status: PROVISIONING → CONFIGURING
                          → STARTING → RUNNING
                                  │
                                  ▼
                  NATS deployments.status → Control Plane
                                  │
                                  ▼
                       WebSocket → User UI
```

### 7.2 DAG Deployment

A user's CI pipeline builds an OCI bundle, signs it with cosign, pushes it to ECR, and calls `POST /v1/deployments/{id}/dags` with the image digest and a config bundle. The Deployment Service validates that the workspace allows OCI bundles, that the signature is valid, and that the caller has the `DEPLOYER` role. It writes a new desired state with `desired_state_version` incremented and emits an `UPDATE` command.

The Agent receives the command. The Deployment Controller computes that only the DAG image reference has changed and applies a Helm `upgrade` with the new image. Kubernetes performs a rolling restart of the worker and scheduler pods. As each pod comes back, it pulls the new image, runs `airflow dags list` to verify the DAGs parse cleanly, and reports ready. Once all pods are ready, the Controller transitions back to `RUNNING` and publishes the new digest into the deployment's audit trail.

If parsing fails on the new image, the rolling update halts (because the new pods never become ready), the deployment stays on the old image, and the Controller emits a `DAG_PARSE_FAILED` event with the parser output. The customer sees a clear failure in the UI and can fix and re-deploy without ever having had production go down.

### 7.3 Autoscaling

A spike in DAG runs increases the Celery queue depth. KEDA's `ScaledObject` for the deployment polls Redis every 30 seconds, observes that queue depth has crossed the threshold, and increases the worker Deployment's replica count. Kubernetes attempts to schedule the new worker pods; if no node has capacity, Karpenter sees the unschedulable pods and provisions a new node from the configured instance pool. New workers start, register with Celery, and begin draining the queue. A few minutes after the spike subsides, KEDA scales workers back down with the drain policy, and Karpenter consolidates underutilised nodes.

Throughout this flow, the Metrics Collector is publishing pod-level metrics to AMP and aggregating worker-hours for billing. The customer sees worker count and queue depth in their dashboard in near-real-time, the SRE team sees them in the platform dashboard, and the Billing Service captures exactly how many worker-hours this spike consumed.

### 7.4 Health Detection and Recovery

The Health Monitor polls the scheduler's heartbeat in the metadata DB every 30 seconds. If two consecutive polls fail, the deployment transitions to `DEGRADED` and the Notification Service alerts the customer. The Deployment Controller attempts automated remediation — first restarting the scheduler pod, then if that fails, restarting the broker connection, then if that still fails, rolling the entire scheduler StatefulSet. If automated remediation succeeds, the deployment returns to `HEALTHY` and the customer gets a "recovered" notification. If it fails, the deployment moves to `UNHEALTHY` with a reason code, an SRE page is fired, and the customer is informed with a runbook link.

This flow is what keeps the platform from waking up an on-call engineer for every transient issue. Most "scheduler stuck" incidents are resolved by a pod restart that the customer never sees, because the system tries the cheap fix before escalating.

### 7.5 Outbound Reconnection After Network Loss

If the Data Plane loses connectivity to the Control Plane — a NAT outage, a DNS hiccup, or in hybrid mode, a customer firewall change — the Agent enters `DEGRADED` after 60 seconds. While disconnected, the Agent keeps reconciling existing deployments based on the last desired state it has, but it cannot accept new commands. Status updates are buffered locally with bounded retention.

When connectivity returns, the Agent reconnects to NATS and replays from its last committed offset. Any commands that arrived during the outage are delivered in order, deduplicated by `desired_state_version`, and applied. Buffered status updates are flushed to the Control Plane. Within 60 seconds of reconnection, the agent is back to `HEALTHY` and the registry reflects the recovery.

This flow is the proof that the architecture handles real network conditions. Customer firewalls go down. AWS networking has bad days. The system is designed so that none of those events cause data loss or split-brain — they cause delay, and the delay is bounded by the Agent's local persistence.

---

## 8. Network and Security

### 8.1 Defence in Depth

Security in Flowdeck is layered intentionally so that no single failure compromises the platform. AWS WAF blocks known attack patterns at the edge before they cost the API anything. CloudFront and Shield absorb volumetric DDoS. The ALB enforces TLS 1.2+ and routes only to internal services. The API Gateway validates every JWT and applies rate limits. The Identity Service makes the actual authorisation decision. The application services validate their own inputs. The database has its own IAM-authenticated connection. Each layer assumes the layer above might fail.

### 8.2 Network Isolation

The Control Plane VPC and the Runtime VPC are not peered. The only path between them is the public Control Plane endpoint, reached over HTTPS. This is what lets us treat managed and hybrid identically and what means a compromised runtime workload cannot move laterally to the Control Plane.

Within each VPC, security groups are tight by default. The Control Plane EKS cluster's security group only accepts traffic from the ALB security group. The Aurora security group only accepts traffic from the EKS security group. The runtime EKS cluster's security group only accepts traffic from the runtime ALB and the inter-pod CNI. NAT Gateway egress is allowed for VPC subnets that need it, but most AWS-API traffic goes through VPC endpoints and never traverses the public internet.

NetworkPolicies inside Kubernetes apply the same logic to pod-to-pod traffic. Each deployment namespace defaults to deny-all-ingress except from the ingress controller, deny-all-egress except to the metadata DB, broker, S3 endpoint, and the Data Plane's secret distributor. Pods cannot reach other deployment namespaces, cannot reach the Kubernetes API directly without an explicit RBAC binding, and cannot reach the AWS metadata service except through IRSA's pod-identity webhook.

### 8.3 Secrets

Customer secrets — connection passwords, API keys, sensitive Variables — are never stored in the Control Plane database. The Control Plane stores only references and metadata: which secret backend, which path, which version, who has access. The actual values live in the configured secret backend, which is AWS Secrets Manager by default in public cloud mode, HashiCorp Vault for customers who require it, and the customer's own backend in hybrid mode.

The Data Plane's Secret Distributor is what makes secrets available to runtime pods. Every deployment has its own IRSA-bound service account scoped to exactly the secret paths it is allowed to read. The Distributor reads the secret using that service account, materialises it as a Kubernetes Secret in the deployment namespace, mounts it into the relevant pods, and watches for changes in the backend so it can rotate transparently. When a secret is deleted from the backend, the Distributor deletes the Kubernetes Secret and triggers a rolling restart so no pod keeps a stale value cached in memory.

KMS keys are organised hierarchically. There is a platform-wide key for Control Plane data, a per-region key for runtime data, and an optional customer-managed key (BYOK) for enterprise customers who require it. Key rotation is annual by default and can be triggered on demand. Every key access is logged in CloudTrail with the calling principal, which is the audit evidence that proves a specific user or service accessed a specific secret at a specific time.

### 8.4 Identity and Authorisation

We covered authentication in Section 4.4. The authorisation model deserves its own treatment because it is what enterprise customers care most about.

Roles are scoped to organisation, workspace, or deployment. Inheritance is downward, never upward — a `workspace:admin` does not become an `org:admin`. Role assignments can be made to individual users, to teams (groups of users defined inside the organisation), or to service accounts. Service-account credentials can be scoped further: an API key can be restricted to a specific workspace, or even a specific deployment, so a CI pipeline that only deploys one environment cannot accidentally touch another.

For enterprise customers, RBAC is extended with attribute-based controls on specific resources — for example, "this deployment can only be deployed during business hours by users in the `prod-deployers` IdP group". These rules are evaluated by the Identity Service and the decision (along with the rule that produced it) is recorded in the audit log. SCIM v2 provisioning from the customer's IdP keeps user lifecycle in sync — when a user is removed from the IdP, they are automatically deactivated in Flowdeck within minutes, not at the next manual review.

Break-glass access for Flowdeck SREs requires a documented incident, an explicit approval through PagerDuty, and produces the same kind of immutable audit record as any other action, with the additional break-glass justification field. There is no "back door" admin access.

### 8.5 Compliance Posture

Flowdeck's controls map to SOC 2, GDPR, HIPAA (in qualifying configurations), and PCI DSS (in qualifying configurations). The architecture supports these by ensuring that audit logs are immutable, retained per the appropriate framework, and exportable; that data is encrypted at rest and in transit; that access is least-privilege and reviewed; that change management is enforced through CI/CD and audited; and that residency policies bind tenants to specific regions. The Control Plane stores operational metadata, account information, and audit data; whether and where customer workload data and DAG content reside is determined by the deployment mode and the configured region, which is precisely what enterprise compliance teams need to know.

---

## 9. Reliability, DR, and SLOs

### 9.1 Availability Architecture

Every Control Plane stateless service runs at minimum three replicas spread across at least two availability zones, behind a load balancer that health-checks them every few seconds. The platform database (Aurora) uses Multi-AZ with synchronous replication; failover from writer to a reader is automatic and typically completes in 30–60 seconds. ElastiCache Redis runs in cluster mode with multi-AZ replicas. NATS JetStream runs as a 3-node Raft quorum, so it tolerates the loss of any single node without data loss. Each runtime EKS cluster's control plane is managed by AWS across three AZs.

Per-deployment runtime resources are tier-dependent. Starter and Pro deployments share the runtime cluster; their availability is bounded by the shared cluster's availability. Enterprise deployments get a dedicated cluster, dedicated RDS, and dedicated ElastiCache, all of which can be configured for cross-AZ replication and, optionally, cross-region replication for warm-standby DR.

### 9.2 Backups and Restore

Aurora has continuous PITR with seven days of restore granularity, plus automated daily snapshots retained for 30 days, plus monthly snapshots exported to S3 retained per compliance policy. NATS JetStream's persistent disks are snapshotted hourly; loss of a single node is recovered automatically by the quorum, and loss of the entire cluster is recovered from snapshots within a documented RTO. Per-deployment RDS instances follow the same pattern — daily snapshots, PITR, optional cross-region copy for enterprise.

A restore is itself an orchestrated flow through the Control Plane. The customer (or SRE) issues a `RESTORE` command for a deployment with a point-in-time target, the Deployment Service emits a versioned command, the Data Plane provisions a new RDS instance from the snapshot, points the Helm release at it, and validates that the new metadata DB is consistent before transitioning the deployment back to `RUNNING`. This is exactly the same control path as any other change, which means restores are tested by being used.

### 9.3 Disaster Recovery

Disaster recovery in Flowdeck is structured around three failure domains and four recovery scenarios.

| Scenario | RPO Target | RTO Target | Mechanism |
|---|---:|---:|---|
| Single pod or node failure | near zero | < 5 min | Kubernetes self-healing |
| Deployment namespace corruption | ≤ 15 min | < 15 min | Rebuild namespace from desired state |
| Runtime cluster loss | ≤ 1 hour | 30–60 min | Standby cluster, restore from snapshots |
| Regional failover (enterprise) | ≤ 5 min | < 10 min | Warm-standby region with replicated state |

Pod and node failures are absorbed by Kubernetes itself; the Health Monitor surfaces them and the Deployment Controller intervenes if needed. Namespace corruption is handled by treating the namespace as ephemeral state — the Data Plane rebuilds it from the deployment's desired state and restores the metadata DB from snapshot. Cluster loss triggers the SRE runbook for cluster failover: a standby cluster in the same region is promoted, deployments are re-registered with the new cluster, and per-deployment restores run in parallel. Regional failover is the most complex and is reserved for enterprise customers who have opted in; it requires that the warm-standby region has been kept up-to-date with replicated platform data and per-deployment data, and the failover decision is made by SRE based on AWS regional status and a documented criteria matrix.

### 9.4 SLOs and Error Budgets

The platform commits to a small number of measurable SLOs, instrumented from canonical telemetry queries owned by SRE and reviewed every release cycle.

| SLO | Target |
|---|---|
| Control Plane API availability | 99.9% monthly |
| Deployment create/update success (excluding user error) | 99.5% monthly |
| Deployment reconciliation latency, P95 | < 5 minutes |
| Data Plane reconnect time, P95 | < 60 seconds |
| Audit ingestion durability | 99.99% |

Each SLO has an error budget — for 99.9% availability over 28 days, that is roughly 40 minutes of allowable unavailability. Multi-window burn-rate alerts page when the budget is being consumed too fast (a 1-hour window detecting a 14.4× burn rate, for example), and ticket-level alerts fire when the medium-term budget is at risk. Error budget consumption is reviewed at every release planning meeting; sustained burn freezes feature work in favour of reliability investment, and persistent over-performance signals that the SLO can be tightened or that resources can be redirected to feature work.

This is the operational discipline that makes "99.9%" mean something. Without burn-rate alerts and budget reviews, an availability target is just a number on a slide.

---

## 10. Observability and Operations

### 10.1 The Three Signals

Flowdeck's observability stack is standard but applied rigorously. Every service emits structured JSON logs with a correlation ID, an organisation ID, a workspace ID, and a deployment ID where applicable; logs flow to CloudWatch Logs in the local account and are aggregated to the observability account for cross-account analysis. Every service exposes Prometheus metrics; metrics are scraped by AWS Managed Prometheus (AMP) in each region and federated to a central AMP for platform-wide dashboards. Every API request, every command dispatch, and every reconciliation is wrapped in an OpenTelemetry trace that follows the request from the API Gateway through the Deployment Service through NATS through the Data Plane Agent through the Deployment Controller to the Kubernetes API call that did the work.

The single most useful operational practice that comes out of this is end-to-end correlation. When a customer reports that their deployment update took 30 minutes, an SRE can paste the correlation ID into the trace tool and see exactly where the time was spent — which service was slow, which AWS API was rate-limited, which Helm step took longest. This is what turns an architecture diagram into an actually-debuggable system.

### 10.2 Dashboards

There are three audiences for dashboards. Customers see per-deployment dashboards in the Flowdeck UI: DAG run success rate, scheduler latency, worker count, queue depth, task duration percentiles, and recent errors. Customer admins see organisation-level rollups: total deployments, total worker-hours, audit feed, billing forecast. Flowdeck SRE sees the platform dashboards: API availability, NATS lag, Data Plane Agent health by region, error budgets, top noisy tenants, and capacity headroom. The same metrics back all three; the difference is which dimensions are exposed and to whom.

### 10.3 Alerting

Alerts are routed by ownership. Platform alerts — API availability burn rate, NATS replication lag, regional KMS errors — page Flowdeck SRE through PagerDuty. Tenant-specific alerts — your scheduler is stuck, your worker count hit the quota — are routed to the customer's configured channels (Slack, PagerDuty, webhook), never to Flowdeck staff. This separation is enforced by the Notification Service and is what stops Flowdeck from accidentally getting on-call for customer DAGs.

Every actionable alert carries a runbook link, a severity, and a reason code. Runbooks are stored in version control and reviewed quarterly. The set of required runbooks — provisioning failure, upgrade rollback, secret rotation failure, control-channel instability, cluster saturation, regional DR — is mandated, and a service cannot go to GA without its runbooks reviewed and tested.

### 10.4 Continuous Operations

Day-to-day operations are dominated by deploys and upgrades. Flowdeck rolls out platform changes through three release channels — `stable` (default for all customers), `canary` (5% of organisations, typically internal and willing customers), and `preview` (opt-in feature flagging). Database migrations follow a strict expand-then-contract pattern: a column is added, the application is updated to write both old and new, then to read new with old as fallback, then to read only new, and finally the old column is dropped. Each step is a separately deployable change, which means a failed migration step never leaves the system in an inconsistent state.

Airflow version upgrades are particularly delicate. New major versions enter `preview`, then `canary` for a release cycle, then `stable`. The N-1 major is supported for a documented deprecation window (typically 12 months) so customers can plan their own upgrades. Forced upgrades happen only for critical security or compliance issues, with at least 30 days notice except in genuine emergency.

---

## 11. Tenancy, Quotas, and Compliance

### 11.1 The Tenant Hierarchy

A request arriving at Flowdeck always identifies a tenant: `organization → workspace → deployment → resource`. Quotas, policies, audit records, and billing all attach at one of these scopes. An organisation has a maximum number of active deployments, a maximum total CPU and memory, a maximum total storage, and a maximum API request rate. A workspace inherits these from its organisation but can have its own narrower limits. A deployment has its own runtime resource limits enforced by Kubernetes ResourceQuota.

The hierarchy is what allows enterprise customers with hundreds of teams to give each team a workspace, give each team an independent quota inside the organisation's overall quota, and have all the audit and billing rolled up cleanly to the organisation level. It is also what allows cross-team isolation without requiring separate AWS accounts: an enterprise customer can have one organisation containing a `production` workspace and a `dev` workspace, each with its own RBAC, each with its own deployments, and the deployments cannot reach each other's data.

### 11.2 Admission Control

Quota enforcement happens at admission time, before any cloud resources are created. The Deployment Service holds a short-lived Redis lock during the check-and-create window, reads the current consumption, validates that the request fits, and only then writes the new desired state. If the check fails, the API returns a deterministic error with a remediation hint — "you have used 18 of 20 deployments in your Pro plan; upgrade your plan or delete unused deployments". This determinism is what makes quota errors actionable rather than mysterious.

Policy decisions go through the same admission path. The Policy Engine is consulted on every mutating action with the requested change and the current tenant context, and it returns either an allow with the matched rule, or a deny with a reason. The decision and the matched rule are recorded in the audit log alongside the action itself, which is what lets an enterprise prove that a particular change was approved by a particular policy.

### 11.3 Audit and Compliance Evidence

Audit logging in Flowdeck is not a feature bolted on for compliance — it is the system of record for every change. Every mutating API call produces an audit entry with the actor, organisation, workspace, action, before/after state, correlation ID, source IP, and timestamp. Entries are immutable, partitioned monthly, and exported to S3 in Parquet for long-term retention. From those records, compliance evidence — change records, access logs, configuration changes, role assignment changes — is generated automatically rather than reconstructed manually.

Data classification is explicit. Class A is platform metadata: organisation, workspace, deployment definitions, role assignments. It lives in Aurora, encrypted with platform KMS keys. Class B is operational telemetry: metrics, logs, traces. It lives in AMP and CloudWatch, retained for 90 days hot and longer cold. Class C is customer runtime data: DAG code, task logs, XCom values. It lives in the per-deployment storage and metadata DB, encrypted with a per-region or per-customer key. Class D is secrets: connection passwords, API keys. It lives in the configured secret backend, never in Aurora. Each class has its own retention, residency, and access-control rules, and the Policy Engine enforces them.

Residency is encoded as a binding from organisation to home region. All Class A data for that organisation lives in that region's Aurora. All Class C data for that organisation's deployments lives in that region's runtime VPC. Cross-region operations — DR replication, support-team queries — require an explicit policy exception that is itself audited.

---

## 12. Extensibility — Hybrid, Remote Execution, Private, Multi-Cloud

The architecture in Sections 3–10 describes Flowdeck on AWS in public cloud mode. The point of this section is to show that nothing about that architecture is AWS-specific in ways that would block extension, and to be explicit about what changes for each future mode.

### 12.1 The Stable Contract

What stays the same across every mode is the *contract*. There is one set of public APIs. There is one desired-state schema. There is one state machine. There is one outbound-only control channel with the same delivery, ordering, ack, replay, and backpressure semantics. There is one audit event format. There is one identity model. A customer who builds CI/CD against Flowdeck on public cloud can move that customer to hybrid, private, or multi-cloud without changing a single line of CI code, because the contract is what they write against.

What changes between modes is *placement and operator responsibility*. The Data Plane moves from Flowdeck's account to the customer's. The Runtime Plane moves with it. In some modes, even the Control Plane moves. But the contract between the planes is constant.

### 12.2 Hybrid Agent Mode

Hybrid is the most important extension and the one this architecture is most carefully designed for. In hybrid, the Control Plane stays in Flowdeck's AWS account exactly as described above. The Data Plane and Runtime Plane move into the customer's VPC. The customer installs the Flowdeck Agent via Helm or a CLI bundle, the agent registers itself with the Control Plane using a one-time bootstrap token that is exchanged for short-lived workload-identity credentials, and from that point forward it behaves identically to a managed Data Plane Agent.

Because the channel is outbound-only, the customer's network requirement is simple: outbound HTTPS from the Data Plane subnet to `api.flowdeck.io`, `grpc.flowdeck.io`, `nats.flowdeck.io`, and the customer's chosen secret backend. No inbound rules. For regulated customers, this can be tightened further by routing the outbound channel over AWS PrivateLink (or Azure Private Link, GCP Private Service Connect when those clouds are added), keeping the traffic off the public internet entirely.

The Data Plane code is identical to the managed Data Plane code. There is no separate "hybrid build". This is enforced architecturally: hybrid is just a different value in the Data Plane Registry's `type` field. Operationally, it means a bug fix in the reconciler ships to managed and hybrid customers in the same release, and SRE only has to debug one codepath.

What differs in hybrid is the operator responsibility split. The customer owns the underlying AWS account, the VPC, the EKS cluster's node lifecycle, and any per-deployment cloud resources (RDS, ElastiCache, S3). Flowdeck owns the agent's lifecycle, the Helm release, the Airflow runtime, and everything visible through the API. The shared responsibility matrix is part of the customer contract and is referenced by the support model — when an issue arises, the matrix determines who investigates first.

### 12.3 Remote Execution Mode

Remote execution is conceptually similar to Astronomer's remote workers. The Control Plane and the scheduler stay centralised — Flowdeck still owns scheduling, the metadata DB, and the webserver. What moves out is the *worker pool*. Customers who have data that can't leave their network, or who want to run tasks adjacent to compute they already have (a Kubernetes cluster on-prem, a remote training cluster), register that cluster as a remote executor.

The remote executor presents an attested workload identity to the Control Plane and is granted scope to claim tasks from a specific tenant queue. When a DAG task is scheduled, the central scheduler writes the task into the appropriate queue; the remote executor pulls the task, runs it in the local cluster with the local data, and reports the result back. The task's payload is encrypted with a per-task envelope key that only the authorised executor can unwrap, so even a compromised queue cannot leak task content. The audit chain links the DAG run, the task run, the executor identity, and the policy decision that authorised the assignment, so a customer can prove exactly which executor ran which task.

The reason this is an extension and not a different product is that it uses the same desired-state model and the same registry. A "remote executor" is just another type of Data Plane component registered with the Data Plane Registry; the difference is that it claims tasks rather than reconciling deployments. The contract between it and the Control Plane is the same outbound-only stream with the same versioning and replay semantics.

### 12.4 Private Cloud Mode

Private cloud is for customers who cannot use a vendor-hosted Control Plane at all — usually for residency, sovereignty, or air-gap reasons. There are two profiles. The **vendor-managed control profile** keeps the Control Plane vendor-hosted but in an isolated single-tenant deployment, with the Data Plane and Runtime Plane in the customer's environment. The **customer-hosted platform profile** packages the entire Control Plane, Data Plane, and Runtime Plane as an installable product that runs entirely inside the customer's environment, with no outbound dependency on Flowdeck.

The packaged install is a Helm-based installer that brings up everything — the Control Plane services, the Aurora-equivalent (for AWS-private installs, Aurora; for true on-prem, a customer-managed PostgreSQL), Redis, NATS, the Data Plane agents, and the runtime cluster — using the same images as the managed product. Upgrades are idempotent and rollback-safe. License entitlements gate which features are available. An air-gapped artifact mirror is supported so the install does not require outbound internet access.

The reason private is an extension track and not part of the GA scope is that it requires a different support model — Flowdeck cannot SSH into the customer's environment, so diagnostics, upgrades, and support all have to be designed for that constraint. The GA work is to ship public cloud and hybrid first, validate the contract is stable, and then package the same code for private.

### 12.5 Multi-Cloud Data Planes

The Control Plane stays on AWS. What we extend to other clouds is the Data Plane and the Runtime Plane. To do this without forking the codebase, the Data Plane is structured around a small set of provider interfaces: `ClusterProvider` (provisions and reconciles a Kubernetes cluster), `StorageProvider` (object storage and persistent volumes), `SecretsProvider` (secret backend), `NetworkProvider` (load balancer and ingress), and `IdentityProvider` (workload identity).

For AWS, the implementations are EKS, S3 + EBS, Secrets Manager (or Vault), ALB + Route 53, and IRSA. For Azure, they would be AKS, Blob Storage + Azure Disks, Key Vault, Application Gateway + Azure DNS, and Workload Identity. For GCP, they would be GKE, Cloud Storage + Persistent Disk, Secret Manager, Cloud Load Balancer + Cloud DNS, and Workload Identity Federation. The Deployment Controller's reconciliation logic is unchanged; only the providers are different.

Going GA on a new cloud requires that the provider implementations satisfy a parity matrix covering security (identity, encryption, secrets), reliability (backup, restore, failover), observability (metrics, logs, traces, alerts), tenancy (isolation tiers, quotas), and metering (the billing dimensions the Control Plane expects). The parity matrix is enforced through a conformance test suite that the new provider must pass before the Data Plane Registry will accept that cloud as a valid `type`.

This is the path that lets Flowdeck reach Azure and GCP customers without becoming three different products.

---

## 13. Implementation Roadmap

The architecture above is not delivered in one release. It is delivered in phases, each of which is internally consistent and shippable.

**Phase 1 — AWS Public Cloud Hardening** is what the GA release looks like. The Control Plane, the Data Plane, the Runtime Plane, the SLO instrumentation, the audit pipeline, the supply-chain controls, the tiered isolation policies, and the basic compliance evidence are all in place. Public cloud is the launch product.

**Phase 2 — Hybrid Agent Maturity** ships the hybrid mode as a first-class option. The Agent is hardened for fleet-management at scale, version skew is supported across N and N-1 majors, and private connectivity options (PrivateLink, equivalents in Azure and GCP later) are added for regulated customers. By the end of this phase, hybrid customers see exactly the same product as public-cloud customers, with the only difference being where their data plane runs.

**Phase 3 — Remote Execution and Private Profiles** extends the architecture to the two more complex modes. Remote execution ships first as an alpha for customers who have a strong need (data sovereignty, edge compute), then matures into a GA product. Private cloud is delivered as a packaged install with idempotent upgrades and an enterprise support model — the engineering work is significant because diagnostics in customer-managed environments require different tooling.

**Phase 4 — Multi-Cloud Data Plane Expansion** adds Azure and GCP. The provider abstraction work happens earlier (during Phase 1, in fact, as a forcing function on clean interfaces), but the actual Azure and GCP implementations and their conformance test passes happen here. GA on each cloud is gated on parity with AWS, not on calendar.

Each phase delivers value to a specific customer segment. Phase 1 covers the vast majority of new customers. Phase 2 unlocks regulated mid-market. Phase 3 unlocks the largest enterprise customers and a category of edge use cases. Phase 4 expands the addressable market to non-AWS organisations. Crucially, every phase ships against the same architecture document — the contracts established in Phase 1 are the contracts honoured in Phase 4.

---

## Closing Note

The reason this architecture works at enterprise scale is not that any single component is exotic. It is that the boundaries between components — between planes, between control and data, between intent and observation, between Flowdeck's responsibility and the customer's — are drawn precisely and held without exception. AWS is the first place we run it. The same architecture is what runs everywhere else.
