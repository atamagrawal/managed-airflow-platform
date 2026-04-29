# AirflowCloud Platform - System Design Document

**Version:** 1.0  
**Date:** April 2026  
**Status:** Draft  
**Author:** Platform Architecture Team

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Product Vision & Goals](#2-product-vision--goals)
3. [System Architecture Overview](#3-system-architecture-overview)
4. [Deployment Models](#4-deployment-models)
   - 4.1 Public Cloud (Managed)
   - 4.2 Agent-Based Deployment (Hybrid)
   - 4.3 Private Cloud (Future)
   - 4.4 Remote Execution (Future)
5. [Core Platform Components](#5-core-platform-components)
6. [CLI Design](#6-cli-design)
7. [AWS Reference Architecture](#7-aws-reference-architecture)
8. [Multi-Cloud Extensibility](#8-multi-cloud-extensibility)
9. [Security Architecture](#9-security-architecture)
10. [API Design](#10-api-design)
11. [Data Model](#11-data-model)
12. [Observability & Monitoring](#12-observability--monitoring)
13. [Extensibility Framework](#13-extensibility-framework)
14. [Migration & Upgrade Strategy](#14-migration--upgrade-strategy)
15. [Future Roadmap](#15-future-roadmap)
16. [Appendix](#16-appendix)

---

## 1. Executive Summary

This document outlines the architecture for **AirflowCloud**, a managed Apache Airflow platform that provides enterprise-grade workflow orchestration capabilities. The platform supports multiple deployment models to accommodate varying customer requirements for control, compliance, and infrastructure ownership.

### Key Differentiators

| Feature | Description |
|---------|-------------|
| **Multi-Deployment Models** | Public cloud, agent-based hybrid, private cloud options |
| **Enterprise CLI** | Full lifecycle management via command line |
| **Cloud Agnostic** | Extensible architecture supporting AWS, GCP, Azure |
| **Zero-Ops Experience** | Automated upgrades, scaling, and maintenance |
| **Enterprise Security** | SOC2, HIPAA-ready with customer-managed keys |

---

## 2. Product Vision & Goals

### 2.1 Vision Statement

Provide the most flexible, secure, and developer-friendly managed Airflow platform that adapts to customer infrastructure requirements while maintaining operational excellence.

### 2.2 Strategic Goals

1. **Deployment Flexibility**: Support customers across the spectrum from fully-managed to self-hosted
2. **Developer Experience**: CLI-first approach with GitOps integration
3. **Enterprise Ready**: Security, compliance, and governance built-in
4. **Operational Excellence**: 99.9% SLA with automated recovery
5. **Extensibility**: Plugin architecture for custom integrations

### 2.3 Target Personas

| Persona | Needs | Deployment Model |
|---------|-------|------------------|
| **Startup Data Team** | Quick setup, low ops overhead | Public Cloud |
| **Enterprise (Regulated)** | Data residency, compliance | Agent-Based / Private |
| **Platform Engineering** | Full control, custom infra | Private Cloud |
| **Hybrid Organizations** | Mixed workloads | Agent-Based |

---

## 3. System Architecture Overview

### 3.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AIRFLOWCLOUD PLATFORM                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │   Control Plane │  │   Data Plane    │  │  Execution Plane│              │
│  │                 │  │                 │  │                 │              │
│  │ • API Gateway   │  │ • Metadata DB   │  │ • Schedulers    │              │
│  │ • Auth Service  │  │ • DAG Storage   │  │ • Workers       │              │
│  │ • Billing       │  │ • Logs/Metrics  │  │ • Triggerers    │              │
│  │ • Orchestrator  │  │ • Secrets Mgmt  │  │ • Web Servers   │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
│           │                    │                    │                        │
│           └────────────────────┼────────────────────┘                        │
│                                │                                             │
│  ┌─────────────────────────────┴─────────────────────────────────────────┐  │
│  │                    DEPLOYMENT ABSTRACTION LAYER                        │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────┐ │  │
│  │  │ Public Cloud │  │ Agent-Based  │  │ Private Cloud│  │  Remote    │ │  │
│  │  │   Adapter    │  │   Adapter    │  │   Adapter    │  │  Executor  │ │  │
│  │  └──────────────┘  └──────────────┘  └──────────────┘  └────────────┘ │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 Core Architectural Principles

1. **Separation of Concerns**: Control plane, data plane, and execution plane are independently scalable
2. **Deployment Agnostic**: Core services abstract away deployment-specific details
3. **API-First**: All operations available via REST/gRPC APIs
4. **Event-Driven**: Asynchronous communication via message queues
5. **Infrastructure as Code**: All deployments reproducible via Terraform/Pulumi

### 3.3 Component Interaction Model

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│     CLI      │────▶│  API Gateway │────▶│ Auth Service │
└──────────────┘     └──────┬───────┘     └──────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│ Deployment   │   │  Workspace   │   │   DAG        │
│ Service      │   │  Service     │   │   Service    │
└──────┬───────┘   └──────────────┘   └──────────────┘
       │
       ▼
┌──────────────────────────────────────────────────────┐
│              Deployment Orchestrator                  │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐     │
│  │ Kubernetes │  │   Agent    │  │  Terraform │     │
│  │  Operator  │  │  Manager   │  │  Executor  │     │
│  └────────────┘  └────────────┘  └────────────┘     │
└──────────────────────────────────────────────────────┘
```

---

## 4. Deployment Models

### 4.1 Public Cloud (Managed) Deployment

**Overview**: Fully managed Airflow deployments running in AirflowCloud's infrastructure. Customers get isolated environments with zero infrastructure management.

#### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    AIRFLOWCLOUD INFRASTRUCTURE                   │
│                         (AWS Account)                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Shared Services VPC                   │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │    │
│  │  │ API Gateway │  │ Auth/IAM    │  │ Monitoring  │     │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘     │    │
│  └─────────────────────────────────────────────────────────┘    │
│                              │                                   │
│         ┌────────────────────┼────────────────────┐             │
│         ▼                    ▼                    ▼             │
│  ┌─────────────┐      ┌─────────────┐      ┌─────────────┐     │
│  │ Customer A  │      │ Customer B  │      │ Customer C  │     │
│  │   VPC       │      │   VPC       │      │   VPC       │     │
│  │             │      │             │      │             │     │
│  │ ┌─────────┐ │      │ ┌─────────┐ │      │ ┌─────────┐ │     │
│  │ │Airflow  │ │      │ │Airflow  │ │      │ │Airflow  │ │     │
│  │ │Cluster  │ │      │ │Cluster  │ │      │ │Cluster  │ │     │
│  │ │(EKS)    │ │      │ │(EKS)    │ │      │ │(EKS)    │ │     │
│  │ └─────────┘ │      │ └─────────┘ │      │ └─────────┘ │     │
│  │ ┌─────────┐ │      │ ┌─────────┐ │      │ ┌─────────┐ │     │
│  │ │RDS      │ │      │ │RDS      │ │      │ │RDS      │ │     │
│  │ │(Postgres)│ │      │ │(Postgres)│ │      │ │(Postgres)│ │     │
│  │ └─────────┘ │      │ └─────────┘ │      │ └─────────┘ │     │
│  └─────────────┘      └─────────────┘      └─────────────┘     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### Key Components

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Compute** | EKS (Kubernetes) | Container orchestration for Airflow components |
| **Database** | RDS PostgreSQL | Airflow metadata database |
| **Storage** | S3 | DAG storage, logs, artifacts |
| **Networking** | VPC per customer | Network isolation |
| **Secrets** | AWS Secrets Manager | Connection and variable storage |
| **Load Balancer** | ALB | Ingress for Airflow webserver |

#### Isolation Model

```python
# Namespace-based isolation within shared EKS cluster (cost-optimized)
# OR
# Dedicated EKS cluster per customer (enterprise tier)

class IsolationStrategy:
    NAMESPACE = "namespace"      # Shared cluster, namespace isolation
    CLUSTER = "cluster"          # Dedicated cluster per customer
    ACCOUNT = "account"          # Dedicated AWS account (enterprise+)
```

#### Provisioning Flow

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  User    │───▶│   API    │───▶│ Workflow │───▶│Terraform │───▶│  Ready   │
│ Request  │    │ Gateway  │    │ Engine   │    │ Executor │    │  State   │
└──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
     │                               │                               │
     │         1. Validate           │                               │
     │         2. Create workspace   │                               │
     │         3. Queue provisioning │                               │
     │                               │                               │
     │                               │    4. Provision VPC           │
     │                               │    5. Deploy EKS/RDS          │
     │                               │    6. Install Airflow         │
     │                               │    7. Configure networking    │
     │                               │                               │
     │                               │                    8. Health check
     │                               │                    9. DNS setup
     │◀──────────────────────────────┴───────────────────────────────│
     │                    10. Return endpoint                        │
```

#### Resource Specifications

| Tier | Scheduler | Workers | Database | Storage |
|------|-----------|---------|----------|---------|
| **Starter** | 1 x 0.5 CPU | 2 x 1 CPU | db.t3.small | 50 GB |
| **Standard** | 2 x 1 CPU | 5 x 2 CPU | db.r5.large | 200 GB |
| **Enterprise** | 3 x 2 CPU | 20 x 4 CPU | db.r5.xlarge | 1 TB |

---

### 4.2 Agent-Based Deployment (Hybrid)

**Overview**: Customer installs a lightweight agent in their infrastructure. The agent communicates with AirflowCloud control plane and manages Airflow deployment locally.

#### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AIRFLOWCLOUD CONTROL PLANE                            │
│                         (Our Infrastructure)                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ API Gateway │  │   Agent     │  │  Config     │  │  Metrics    │        │
│  │             │  │  Registry   │  │  Service    │  │  Collector  │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
│         │                │                │                │                │
│         └────────────────┴────────────────┴────────────────┘                │
│                                   │                                          │
│                                   │ Secure Tunnel (mTLS)                    │
│                                   │                                          │
└───────────────────────────────────┼──────────────────────────────────────────┘
                                    │
                    ════════════════╪════════════════
                         CUSTOMER NETWORK BOUNDARY
                    ════════════════╪════════════════
                                    │
┌───────────────────────────────────┼──────────────────────────────────────────┐
│                        CUSTOMER INFRASTRUCTURE                               │
│                         (Customer's AWS Account)                             │
├───────────────────────────────────┼──────────────────────────────────────────┤
│                                   │                                          │
│  ┌────────────────────────────────┴────────────────────────────────────┐    │
│  │                      AIRFLOWCLOUD AGENT                              │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │    │
│  │  │  Heartbeat  │  │  Config     │  │  Deployment │  │  Metrics   │ │    │
│  │  │  Manager    │  │  Sync       │  │  Controller │  │  Exporter  │ │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘ │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │    │
│  │  │  Log        │  │  Secret     │  │  Health     │                 │    │
│  │  │  Forwarder  │  │  Manager    │  │  Monitor    │                 │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │    │
│  └─────────────────────────────────┬────────────────────────────────────┘    │
│                                    │                                         │
│                                    ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    CUSTOMER KUBERNETES CLUSTER                       │    │
│  │  ┌─────────────────────────────────────────────────────────────┐   │    │
│  │  │                    Airflow Namespace                         │   │    │
│  │  │  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐ │   │    │
│  │  │  │ Scheduler │  │ Webserver │  │ Triggerer │  │  Workers  │ │   │    │
│  │  │  └───────────┘  └───────────┘  └───────────┘  └───────────┘ │   │    │
│  │  └─────────────────────────────────────────────────────────────┘   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │ Customer RDS    │  │ Customer S3     │  │ Customer        │              │
│  │ (Metadata DB)   │  │ (DAGs/Logs)     │  │ Secrets Manager │              │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘              │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

#### Agent Components

```python
class AirflowCloudAgent:
    """
    Lightweight agent deployed in customer infrastructure.
    Manages Airflow lifecycle while reporting to control plane.
    """

    components = {
        "heartbeat_manager": {
            "purpose": "Maintain connection to control plane",
            "interval": "30 seconds",
            "protocol": "gRPC with mTLS"
        },
        "config_sync": {
            "purpose": "Sync configuration from control plane",
            "features": ["Airflow version", "Resource specs", "Environment vars"]
        },
        "deployment_controller": {
            "purpose": "Manage Kubernetes resources",
            "operations": ["Deploy", "Scale", "Upgrade", "Rollback"]
        },
        "metrics_exporter": {
            "purpose": "Export metrics to control plane",
            "metrics": ["DAG runs", "Task duration", "Resource usage"]
        },
        "log_forwarder": {
            "purpose": "Forward logs (optional, customer controlled)",
            "destinations": ["Control plane", "Customer's logging system"]
        },
        "secret_manager": {
            "purpose": "Bridge to customer's secret store",
            "backends": ["AWS Secrets Manager", "Vault", "K8s Secrets"]
        },
        "health_monitor": {
            "purpose": "Monitor Airflow component health",
            "checks": ["Scheduler", "Webserver", "Database", "Workers"]
        }
    }
```

#### Communication Protocol

```
┌─────────────────────────────────────────────────────────────────┐
│                    AGENT COMMUNICATION FLOW                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  OUTBOUND ONLY (Agent → Control Plane)                          │
│  ═══════════════════════════════════════                        │
│                                                                  │
│  Agent initiates all connections (no inbound firewall rules)    │
│                                                                  │
│  ┌─────────┐         ┌─────────────────┐         ┌─────────┐   │
│  │  Agent  │────────▶│  Control Plane  │◀────────│  Agent  │   │
│  │  (Cust) │  gRPC   │   (Our Infra)   │  gRPC   │  (Cust) │   │
│  └─────────┘  mTLS   └─────────────────┘  mTLS   └─────────┘   │
│                                                                  │
│  Message Types:                                                  │
│  ─────────────                                                   │
│  • Heartbeat (every 30s)                                        │
│  • Config poll (every 60s)                                      │
│  • Metrics push (every 60s)                                     │
│  • Log stream (real-time, optional)                             │
│  • Command acknowledgment                                        │
│                                                                  │
│  Commands (Control Plane → Agent via response):                 │
│  ──────────────────────────────────────────────                 │
│  • Deploy/Upgrade Airflow                                       │
│  • Scale workers                                                │
│  • Update configuration                                         │
│  • Trigger health check                                         │
│  • Collect diagnostics                                          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### Agent Installation

```bash
# Installation via CLI
$ airflowcloud agent install \
    --cluster-name my-eks-cluster \
    --namespace airflowcloud \
    --registration-token <TOKEN>

# Or via Helm
$ helm repo add airflowcloud https://charts.airflowcloud.io
$ helm install airflowcloud-agent airflowcloud/agent \
    --namespace airflowcloud \
    --set registrationToken=<TOKEN> \
    --set controlPlane.endpoint=agent.airflowcloud.io
```

#### Agent Configuration

```yaml
# agent-config.yaml
apiVersion: airflowcloud.io/v1
kind: AgentConfig
metadata:
  name: airflowcloud-agent
spec:
  # Control plane connection
  controlPlane:
    endpoint: agent.airflowcloud.io:443
    registrationToken: ${REGISTRATION_TOKEN}

  # Kubernetes configuration
  kubernetes:
    namespace: airflow
    serviceAccount: airflowcloud-agent

  # Resource management
  resources:
    agent:
      cpu: 100m
      memory: 256Mi

  # Feature flags
  features:
    logForwarding: true
    metricsExport: true
    autoUpgrade: false

  # Security
  security:
    mtls:
      enabled: true
      certRotationDays: 30
    rbac:
      clusterRole: airflowcloud-agent
```

#### Data Flow & Privacy

```
┌─────────────────────────────────────────────────────────────────┐
│                    DATA RESIDENCY MODEL                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  STAYS IN CUSTOMER INFRASTRUCTURE:                              │
│  ═════════════════════════════════                              │
│  ✓ DAG files and code                                           │
│  ✓ Airflow metadata database                                    │
│  ✓ Task logs (unless forwarding enabled)                        │
│  ✓ Connections and secrets                                      │
│  ✓ XCom data                                                    │
│  ✓ All processed data                                           │
│                                                                  │
│  SENT TO CONTROL PLANE:                                         │
│  ══════════════════════                                         │
│  • Agent health status                                          │
│  • Aggregated metrics (DAG count, task success rate)            │
│  • Component versions                                           │
│  • Error summaries (no sensitive data)                          │
│  • Logs (only if customer enables forwarding)                   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

### 4.3 Private Cloud Deployment (Future)

**Overview**: Full platform deployment in customer's cloud account with complete infrastructure ownership. AirflowCloud provides the software and management layer.

#### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      CUSTOMER'S CLOUD ACCOUNT                                │
│                    (Complete Infrastructure Ownership)                       │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    AIRFLOWCLOUD CONTROL PLANE                          │  │
│  │                    (Deployed in Customer Account)                      │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │ API Gateway │  │ Orchestrator│  │  Billing    │  │  Monitoring │  │  │
│  │  │             │  │             │  │  (Optional) │  │             │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                         │                                    │
│         ┌───────────────────────────────┼───────────────────────────────┐   │
│         ▼                               ▼                               ▼   │
│  ┌─────────────┐                 ┌─────────────┐                 ┌─────────┐│
│  │ Workspace A │                 │ Workspace B │                 │Workspace││
│  │ (Team 1)    │                 │ (Team 2)    │                 │   C     ││
│  │             │                 │             │                 │         ││
│  │ ┌─────────┐ │                 │ ┌─────────┐ │                 │┌───────┐││
│  │ │Airflow  │ │                 │ │Airflow  │ │                 ││Airflow│││
│  │ │Cluster  │ │                 │ │Cluster  │ │                 ││Cluster│││
│  │ └─────────┘ │                 │ └─────────┘ │                 │└───────┘││
│  └─────────────┘                 └─────────────┘                 └─────────┘│
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    SHARED INFRASTRUCTURE                               │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │ EKS Cluster │  │ RDS Cluster │  │ S3 Buckets  │  │ VPC/Network │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ License validation &
                                    │ Optional telemetry
                                    ▼
                    ┌───────────────────────────────┐
                    │   AIRFLOWCLOUD LICENSE SERVER  │
                    │      (Our Infrastructure)      │
                    └───────────────────────────────┘
```

#### Deployment Options

| Option | Description | Use Case |
|--------|-------------|----------|
| **Terraform Module** | IaC templates for self-deployment | Platform teams with IaC expertise |
| **Installer Script** | Guided installation wizard | Quick setup with less customization |
| **Kubernetes Operator** | GitOps-friendly deployment | Teams using ArgoCD/Flux |

#### Private Cloud Components

```yaml
# private-cloud-deployment.yaml
apiVersion: airflowcloud.io/v1
kind: PrivateCloudDeployment
metadata:
  name: acme-corp-airflowcloud
spec:
  # License
  license:
    key: ${LICENSE_KEY}
    validationEndpoint: license.airflowcloud.io

  # Control plane configuration
  controlPlane:
    replicas: 3
    database:
      type: rds-postgresql
      instanceClass: db.r5.large
    storage:
      type: s3
      bucket: acme-airflowcloud-control

  # Workspace defaults
  workspaceDefaults:
    airflowVersion: "2.8.1"
    executor: KubernetesExecutor

  # Networking
  networking:
    vpcId: vpc-xxx
    subnetIds:
      - subnet-xxx
      - subnet-yyy
    ingressType: internal  # or public

  # Observability
  observability:
    metrics:
      enabled: true
      destination: prometheus
    logging:
      enabled: true
      destination: elasticsearch
    tracing:
      enabled: true
      destination: jaeger
```

---

### 4.4 Remote Execution (Future)

**Overview**: Execute Airflow tasks on remote infrastructure while maintaining centralized orchestration. Useful for data locality, compliance, or specialized compute requirements.

#### Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    AIRFLOWCLOUD (Control + Orchestration)                    │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         Airflow Scheduler                            │    │
│  │  • DAG parsing and scheduling                                       │    │
│  │  • Task state management                                            │    │
│  │  • Execution routing                                                │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│                    ┌───────────────┼───────────────┐                        │
│                    ▼               ▼               ▼                        │
│             ┌───────────┐   ┌───────────┐   ┌───────────┐                  │
│             │  Local    │   │  Remote   │   │  Remote   │                  │
│             │  Executor │   │  Executor │   │  Executor │                  │
│             │  (Default)│   │  (AWS)    │   │  (On-Prem)│                  │
│             └───────────┘   └─────┬─────┘   └─────┬─────┘                  │
│                                   │               │                         │
└───────────────────────────────────┼───────────────┼─────────────────────────┘
                                    │               │
                    ════════════════╪═══════════════╪════════════════
                                    │               │
                                    ▼               ▼
                    ┌───────────────────┐   ┌───────────────────┐
                    │  CUSTOMER AWS     │   │  CUSTOMER         │
                    │  ACCOUNT          │   │  ON-PREMISES      │
                    │                   │   │                   │
                    │  ┌─────────────┐  │   │  ┌─────────────┐  │
                    │  │ Remote      │  │   │  │ Remote      │  │
                    │  │ Worker Pool │  │   │  │ Worker Pool │  │
                    │  │             │  │   │  │             │  │
                    │  │ • Spark     │  │   │  │ • GPU Tasks │  │
                    │  │ • EMR Jobs  │  │   │  │ • Sensitive │  │
                    │  │ • Glue      │  │   │  │   Data      │  │
                    │  └─────────────┘  │   │  └─────────────┘  │
                    │                   │   │                   │
                    │  ┌─────────────┐  │   │  ┌─────────────┐  │
                    │  │ Customer    │  │   │  │ Customer    │  │
                    │  │ Data Lake   │  │   │  │ Database    │  │
                    │  └─────────────┘  │   │  └─────────────┘  │
                    │                   │   │                   │
                    └───────────────────┘   └───────────────────┘
```

#### Remote Executor Configuration

```python
# DAG with remote execution
from airflow import DAG
from airflow.operators.python import PythonOperator
from airflowcloud.executors import RemoteExecutor

with DAG("data_pipeline", ...) as dag:

    # Task runs locally (default)
    extract = PythonOperator(
        task_id="extract",
        python_callable=extract_data,
    )

    # Task runs on customer's AWS infrastructure
    transform = PythonOperator(
        task_id="transform_sensitive_data",
        python_callable=transform_data,
        executor_config={
            "remote_executor": {
                "target": "customer-aws-pool",
                "resources": {
                    "cpu": "4",
                    "memory": "16Gi"
                }
            }
        }
    )

    # Task runs on customer's on-prem GPU cluster
    ml_inference = PythonOperator(
        task_id="ml_inference",
        python_callable=run_inference,
        executor_config={
            "remote_executor": {
                "target": "customer-onprem-gpu",
                "resources": {
                    "gpu": "1",
                    "gpu_type": "nvidia-a100"
                }
            }
        }
    )

    extract >> transform >> ml_inference
```

---

## 5. Core Platform Components

### 5.1 Control Plane Services

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CONTROL PLANE SERVICES                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                           API LAYER                                  │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │    │
│  │  │ REST API    │  │ GraphQL API │  │ gRPC API    │  │ WebSocket  │ │    │
│  │  │ (Public)    │  │ (Dashboard) │  │ (Internal)  │  │ (Real-time)│ │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                         │                                    │
│  ┌──────────────────────────────────────┴──────────────────────────────┐    │
│  │                        CORE SERVICES                                 │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │    │
│  │  │ Auth Service    │  │ Workspace Svc   │  │ Deployment Svc  │     │    │
│  │  │                 │  │                 │  │                 │     │    │
│  │  │ • SSO/SAML      │  │ • CRUD ops      │  │ • Provisioning  │     │    │
│  │  │ • RBAC          │  │ • Team mgmt    │  │ • Scaling       │     │    │
│  │  │ • API keys      │  │ • Permissions   │  │ • Upgrades      │     │    │
│  │  │ • MFA           │  │ • Quotas        │  │ • Rollbacks     │     │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘     │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │    │
│  │  │ DAG Service     │  │ Secrets Service │  │ Billing Service │     │    │
│  │  │                 │  │                 │  │                 │     │    │
│  │  │ • Sync/Deploy   │  │ • Vault backend │  │ • Usage tracking│     │    │
│  │  │ • Validation    │  │ • Rotation      │  │ • Metering      │     │    │
│  │  │ • Versioning    │  │ • Audit logs    │  │ • Invoicing     │     │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘     │    │
│  │                                                                      │    │
│  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │    │
│  │  │ Agent Service   │  │ Metrics Service │  │ Alerting Svc    │     │    │
│  │  │                 │  │                 │  │                 │     │    │
│  │  │ • Registration  │  │ • Collection    │  │ • Rules engine  │     │    │
│  │  │ • Health checks │  │ • Aggregation   │  │ • Notifications │     │    │
│  │  │ • Commands      │  │ • Retention     │  │ • Escalation    │     │    │
│  │  └─────────────────┘  └─────────────────┘  └─────────────────┘     │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Service Specifications

| Service | Technology | Database | Message Queue |
|---------|------------|----------|---------------|
| **API Gateway** | Kong / AWS API Gateway | - | - |
| **Auth Service** | Go + OAuth2/OIDC | PostgreSQL | - |
| **Workspace Service** | Go | PostgreSQL | SQS/Kafka |
| **Deployment Service** | Go + Temporal | PostgreSQL | SQS/Kafka |
| **DAG Service** | Python | PostgreSQL + S3 | SQS/Kafka |
| **Secrets Service** | Go + Vault | Vault | - |
| **Billing Service** | Go | PostgreSQL | SQS/Kafka |
| **Agent Service** | Go + gRPC | PostgreSQL + Redis | - |
| **Metrics Service** | Go + ClickHouse | ClickHouse | Kafka |
| **Alerting Service** | Go | PostgreSQL | SQS/Kafka |

### 5.3 Deployment Orchestrator

```python
# deployment_orchestrator.py
from enum import Enum
from typing import Protocol
from dataclasses import dataclass

class DeploymentType(Enum):
    PUBLIC_CLOUD = "public_cloud"
    AGENT_BASED = "agent_based"
    PRIVATE_CLOUD = "private_cloud"

class DeploymentAdapter(Protocol):
    """Interface for deployment adapters"""

    async def provision(self, spec: "DeploymentSpec") -> "DeploymentResult":
        """Provision new Airflow deployment"""
        ...

    async def scale(self, deployment_id: str, scale_spec: "ScaleSpec") -> None:
        """Scale deployment resources"""
        ...

    async def upgrade(self, deployment_id: str, version: str) -> None:
        """Upgrade Airflow version"""
        ...

    async def destroy(self, deployment_id: str) -> None:
        """Destroy deployment"""
        ...

    async def health_check(self, deployment_id: str) -> "HealthStatus":
        """Check deployment health"""
        ...

@dataclass
class DeploymentSpec:
    workspace_id: str
    deployment_type: DeploymentType
    airflow_version: str
    executor: str  # KubernetesExecutor, CeleryExecutor
    resources: dict
    environment: dict
    networking: dict

class DeploymentOrchestrator:
    """
    Central orchestrator that routes deployment operations
    to the appropriate adapter based on deployment type.
    """

    def __init__(self):
        self.adapters: dict[DeploymentType, DeploymentAdapter] = {
            DeploymentType.PUBLIC_CLOUD: PublicCloudAdapter(),
            DeploymentType.AGENT_BASED: AgentBasedAdapter(),
            DeploymentType.PRIVATE_CLOUD: PrivateCloudAdapter(),
        }

    async def deploy(self, spec: DeploymentSpec) -> DeploymentResult:
        adapter = self.adapters[spec.deployment_type]

        # Validate spec
        await self._validate_spec(spec)

        # Create deployment record
        deployment = await self._create_deployment_record(spec)

        # Execute deployment workflow
        result = await adapter.provision(spec)

        # Update deployment status
        await self._update_deployment_status(deployment.id, result)

        return result
```

---

## 6. CLI Design

### 6.1 CLI Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           AIRFLOWCLOUD CLI                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         CLI CORE                                     │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │    │
│  │  │ Command     │  │ Config      │  │ Auth        │  │ Output     │ │    │
│  │  │ Parser      │  │ Manager     │  │ Handler     │  │ Formatter  │ │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                         │                                    │
│  ┌──────────────────────────────────────┴──────────────────────────────┐    │
│  │                       COMMAND GROUPS                                 │    │
│  │                                                                      │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │    │
│  │  │  auth    │ │workspace │ │deployment│ │   dag    │ │  agent   │ │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ │    │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ │    │
│  │  │  config  │ │  logs    │ │ secrets  │ │   run    │ │  plugin  │ │    │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘ │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                         │                                    │
│  ┌──────────────────────────────────────┴──────────────────────────────┐    │
│  │                       API CLIENT                                     │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                 │    │
│  │  │ HTTP Client │  │ Retry Logic │  │ Error       │                 │    │
│  │  │             │  │             │  │ Handler     │                 │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                 │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Command Reference

```bash
# ═══════════════════════════════════════════════════════════════════════════
#                        AIRFLOWCLOUD CLI REFERENCE
# ═══════════════════════════════════════════════════════════════════════════

# ─────────────────────────────────────────────────────────────────────────────
# AUTHENTICATION
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud auth login                    # Interactive browser login
$ airflowcloud auth login --token <TOKEN>    # Token-based login
$ airflowcloud auth logout                   # Clear credentials
$ airflowcloud auth status                   # Show current auth status
$ airflowcloud auth api-key create           # Create API key
$ airflowcloud auth api-key list             # List API keys
$ airflowcloud auth api-key revoke <KEY_ID>  # Revoke API key

# ─────────────────────────────────────────────────────────────────────────────
# WORKSPACE MANAGEMENT
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud workspace list                # List all workspaces
$ airflowcloud workspace create <NAME> \
    --description "Production workspace"     # Create workspace
$ airflowcloud workspace delete <NAME>       # Delete workspace
$ airflowcloud workspace switch <NAME>       # Switch active workspace
$ airflowcloud workspace info                # Show current workspace details

# ─────────────────────────────────────────────────────────────────────────────
# DEPLOYMENT MANAGEMENT
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud deployment create <NAME> \
    --airflow-version 2.8.1 \
    --executor kubernetes \
    --scheduler-size medium \
    --worker-count 5                         # Create deployment

$ airflowcloud deployment list               # List deployments
$ airflowcloud deployment info <NAME>        # Show deployment details
$ airflowcloud deployment delete <NAME>      # Delete deployment

$ airflowcloud deployment scale <NAME> \
    --workers 10                             # Scale workers

$ airflowcloud deployment upgrade <NAME> \
    --airflow-version 2.9.0                  # Upgrade Airflow

$ airflowcloud deployment logs <NAME> \
    --component scheduler \
    --follow                                 # Stream logs

$ airflowcloud deployment hibernate <NAME>   # Pause deployment (cost saving)
$ airflowcloud deployment wake <NAME>        # Resume deployment

# ─────────────────────────────────────────────────────────────────────────────
# DAG MANAGEMENT
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud dag deploy ./dags/            # Deploy DAGs from directory
$ airflowcloud dag deploy ./dags/my_dag.py   # Deploy single DAG
$ airflowcloud dag list                      # List deployed DAGs
$ airflowcloud dag info <DAG_ID>             # Show DAG details
$ airflowcloud dag delete <DAG_ID>           # Remove DAG
$ airflowcloud dag validate ./dags/          # Validate DAGs locally
$ airflowcloud dag test <DAG_ID> <TASK_ID>   # Test task locally

# ─────────────────────────────────────────────────────────────────────────────
# DAG RUNS
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud run trigger <DAG_ID>          # Trigger DAG run
$ airflowcloud run trigger <DAG_ID> \
    --conf '{"key": "value"}'                # Trigger with config

$ airflowcloud run list <DAG_ID>             # List runs for DAG
$ airflowcloud run info <DAG_ID> <RUN_ID>    # Show run details
$ airflowcloud run logs <DAG_ID> <RUN_ID> \
    --task <TASK_ID>                         # Show task logs

# ─────────────────────────────────────────────────────────────────────────────
# SECRETS & CONNECTIONS
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud secret create <KEY> <VALUE>   # Create secret
$ airflowcloud secret list                   # List secrets (names only)
$ airflowcloud secret delete <KEY>           # Delete secret

$ airflowcloud connection create <CONN_ID> \
    --type postgres \
    --host db.example.com \
    --port 5432 \
    --login user \
    --password-stdin                         # Create connection

$ airflowcloud connection list               # List connections
$ airflowcloud connection delete <CONN_ID>   # Delete connection

# ─────────────────────────────────────────────────────────────────────────────
# AGENT MANAGEMENT (Hybrid Deployments)
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud agent install \
    --cluster-name my-cluster \
    --namespace airflowcloud                 # Install agent

$ airflowcloud agent status                  # Show agent status
$ airflowcloud agent logs --follow           # Stream agent logs
$ airflowcloud agent upgrade                 # Upgrade agent
$ airflowcloud agent uninstall               # Remove agent

$ airflowcloud agent token create            # Create registration token
$ airflowcloud agent token list              # List tokens
$ airflowcloud agent token revoke <TOKEN_ID> # Revoke token

# ─────────────────────────────────────────────────────────────────────────────
# CONFIGURATION
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud config set <KEY> <VALUE>      # Set config value
$ airflowcloud config get <KEY>              # Get config value
$ airflowcloud config list                   # List all config
$ airflowcloud config reset                  # Reset to defaults

# ─────────────────────────────────────────────────────────────────────────────
# PLUGINS & EXTENSIONS
# ─────────────────────────────────────────────────────────────────────────────

$ airflowcloud plugin install <NAME>         # Install plugin
$ airflowcloud plugin list                   # List installed plugins
$ airflowcloud plugin uninstall <NAME>       # Remove plugin
```

### 6.3 CLI Configuration

```yaml
# ~/.airflowcloud/config.yaml
version: 1

# Active context
current-context: production

# Available contexts
contexts:
  - name: production
    workspace: prod-workspace
    deployment: prod-airflow
    api-endpoint: https://api.airflowcloud.io

  - name: staging
    workspace: staging-workspace
    deployment: staging-airflow
    api-endpoint: https://api.airflowcloud.io

# Global settings
settings:
  output-format: table  # table, json, yaml
  color: true
  pager: true
  timeout: 30s

# Authentication
auth:
  method: oauth  # oauth, api-key, token
  # Credentials stored securely in system keychain
```

### 6.4 CLI Implementation

```go
// cmd/root.go
package cmd

import (
    "github.com/spf13/cobra"
    "github.com/spf13/viper"
)

var rootCmd = &cobra.Command{
    Use:   "airflowcloud",
    Short: "AirflowCloud CLI - Manage your Airflow deployments",
    Long: `AirflowCloud CLI provides full lifecycle management for 
Apache Airflow deployments across public cloud, hybrid, and 
private cloud environments.`,
}

func init() {
    cobra.OnInitialize(initConfig)

    // Global flags
    rootCmd.PersistentFlags().StringP("workspace", "w", "", "Workspace to use")
    rootCmd.PersistentFlags().StringP("deployment", "d", "", "Deployment to use")
    rootCmd.PersistentFlags().StringP("output", "o", "table", "Output format")
    rootCmd.PersistentFlags().Bool("no-color", false, "Disable color output")

    // Bind flags to viper
    viper.BindPFlag("workspace", rootCmd.PersistentFlags().Lookup("workspace"))
    viper.BindPFlag("deployment", rootCmd.PersistentFlags().Lookup("deployment"))

    // Add command groups
    rootCmd.AddCommand(authCmd)
    rootCmd.AddCommand(workspaceCmd)
    rootCmd.AddCommand(deploymentCmd)
    rootCmd.AddCommand(dagCmd)
    rootCmd.AddCommand(runCmd)
    rootCmd.AddCommand(secretCmd)
    rootCmd.AddCommand(connectionCmd)
    rootCmd.AddCommand(agentCmd)
    rootCmd.AddCommand(configCmd)
    rootCmd.AddCommand(pluginCmd)
}

// cmd/deployment.go
var deploymentCmd = &cobra.Command{
    Use:   "deployment",
    Short: "Manage Airflow deployments",
}

var deploymentCreateCmd = &cobra.Command{
    Use:   "create [name]",
    Short: "Create a new Airflow deployment",
    Args:  cobra.ExactArgs(1),
    RunE: func(cmd *cobra.Command, args []string) error {
        name := args[0]

        spec := &DeploymentSpec{
            Name:           name,
            AirflowVersion: viper.GetString("airflow-version"),
            Executor:       viper.GetString("executor"),
            SchedulerSize:  viper.GetString("scheduler-size"),
            WorkerCount:    viper.GetInt("worker-count"),
        }

        client := NewAPIClient()
        deployment, err := client.Deployments.Create(cmd.Context(), spec)
        if err != nil {
            return err
        }

        // Stream deployment progress
        return streamDeploymentProgress(cmd.Context(), deployment.ID)
    },
}

func init() {
    deploymentCmd.AddCommand(deploymentCreateCmd)

    deploymentCreateCmd.Flags().String("airflow-version", "2.8.1", "Airflow version")
    deploymentCreateCmd.Flags().String("executor", "kubernetes", "Executor type")
    deploymentCreateCmd.Flags().String("scheduler-size", "small", "Scheduler size")
    deploymentCreateCmd.Flags().Int("worker-count", 2, "Number of workers")
}
```

---

## 7. AWS Reference Architecture

### 7.1 Infrastructure Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AWS REGION                                      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                         MANAGEMENT VPC                                 │  │
│  │                                                                        │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │                    Control Plane (EKS)                           │  │  │
│  │  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐   │  │  │
│  │  │  │API Svc  │ │Auth Svc │ │Deploy   │ │Billing  │ │Agent    │   │  │  │
│  │  │  │         │ │         │ │Svc      │ │Svc      │ │Svc      │   │  │  │
│  │  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘   │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  │                                                                        │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                   │  │
│  │  │ RDS         │  │ ElastiCache │  │ S3          │                   │  │
│  │  │ (Control DB)│  │ (Redis)     │  │ (Artifacts) │                   │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘                   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                    │                                         │
│                    VPC Peering / Transit Gateway                            │
│                                    │                                         │
│  ┌─────────────────────────────────┴─────────────────────────────────────┐  │
│  │                                                                        │  │
│  │  ┌─────────────────────┐              ┌─────────────────────┐         │  │
│  │  │   CUSTOMER VPC A    │              │   CUSTOMER VPC B    │         │  │
│  │  │                     │              │                     │         │  │
│  │  │  ┌───────────────┐  │              │  ┌───────────────┐  │         │  │
│  │  │  │ EKS Cluster   │  │              │  │ EKS Cluster   │  │         │  │
│  │  │  │ (Airflow)     │  │              │  │ (Airflow)     │  │         │  │
│  │  │  │               │  │              │  │               │  │         │  │
│  │  │  │ ┌───────────┐ │  │              │  │ ┌───────────┐ │  │         │  │
│  │  │  │ │Scheduler  │ │  │              │  │ │Scheduler  │ │  │         │  │
│  │  │  │ │Webserver  │ │  │              │  │ │Webserver  │ │  │         │  │
│  │  │  │ │Workers    │ │  │              │  │ │Workers    │ │  │         │  │
│  │  │  │ └───────────┘ │  │              │  │ └───────────┘ │  │         │  │
│  │  │  └───────────────┘  │              │  └───────────────┘  │         │  │
│  │  │                     │              │                     │         │  │
│  │  │  ┌─────────┐        │              │  ┌─────────┐        │         │  │
│  │  │  │RDS      │        │              │  │RDS      │        │         │  │
│  │  │  │(Airflow │        │              │  │(Airflow │        │         │  │
│  │  │  │Metadata)│        │              │  │Metadata)│        │         │  │
│  │  │  └─────────┘        │              │  └─────────┘        │         │  │
│  │  │                     │              │                     │         │  │
│  │  │  ┌─────────┐        │              │  ┌─────────┐        │         │  │
│  │  │  │S3       │        │              │  │S3       │        │         │  │
│  │  │  │(DAGs/   │        │              │  │(DAGs/   │        │         │  │
│  │  │  │Logs)    │        │              │  │Logs)    │        │         │  │
│  │  │  └─────────┘        │              │  └─────────┘        │         │  │
│  │  └─────────────────────┘              └─────────────────────┘         │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 7.2 AWS Services Mapping

| Component | AWS Service | Configuration |
|-----------|-------------|---------------|
| **Compute** | EKS | Managed node groups, Fargate for workers |
| **Database** | RDS PostgreSQL | Multi-AZ, encrypted, automated backups |
| **Cache** | ElastiCache Redis | Cluster mode for HA |
| **Storage** | S3 | Versioning, lifecycle policies |
| **Secrets** | Secrets Manager | Automatic rotation |
| **Networking** | VPC, ALB, NLB | Private subnets, WAF |
| **DNS** | Route 53 | Health checks, failover |
| **CDN** | CloudFront | Static assets, API caching |
| **Monitoring** | CloudWatch | Logs, metrics, alarms |
| **Security** | IAM, KMS | Fine-grained permissions, CMK |
| **Queue** | SQS | Dead letter queues |
| **Events** | EventBridge | Cross-service orchestration |

### 7.3 Terraform Module Structure

```
terraform/
├── modules/
│   ├── control-plane/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── eks.tf
│   │   ├── rds.tf
│   │   ├── elasticache.tf
│   │   └── iam.tf
│   │
│   ├── customer-vpc/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── vpc.tf
│   │   ├── subnets.tf
│   │   └── security-groups.tf
│   │
│   ├── airflow-deployment/
│   │   ├── main.tf
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   ├── eks-namespace.tf
│   │   ├── rds.tf
│   │   ├── s3.tf
│   │   ├── iam.tf
│   │   └── helm-airflow.tf
│   │
│   └── networking/
│       ├── main.tf
│       ├── transit-gateway.tf
│       └── vpc-peering.tf
│
├── environments/
│   ├── production/
│   │   ├── main.tf
│   │   ├── terraform.tfvars
│   │   └── backend.tf
│   │
│   └── staging/
│       ├── main.tf
│       ├── terraform.tfvars
│       └── backend.tf
│
└── scripts/
    ├── init.sh
    └── deploy.sh
```

### 7.4 Sample Terraform Configuration

```hcl
# modules/airflow-deployment/main.tf

variable "workspace_id" {
  type        = string
  description = "Unique workspace identifier"
}

variable "airflow_version" {
  type        = string
  default     = "2.8.1"
}

variable "executor" {
  type        = string
  default     = "KubernetesExecutor"
}

variable "resources" {
  type = object({
    scheduler_cpu    = string
    scheduler_memory = string
    worker_cpu       = string
    worker_memory    = string
    worker_count     = number
  })
  default = {
    scheduler_cpu    = "1"
    scheduler_memory = "2Gi"
    worker_cpu       = "2"
    worker_memory    = "4Gi"
    worker_count     = 2
  }
}

# EKS Namespace
resource "kubernetes_namespace" "airflow" {
  metadata {
    name = "airflow-${var.workspace_id}"
    labels = {
      "airflowcloud.io/workspace" = var.workspace_id
      "airflowcloud.io/managed"   = "true"
    }
  }
}

# RDS Instance
resource "aws_db_instance" "airflow_metadata" {
  identifier     = "airflow-${var.workspace_id}"
  engine         = "postgres"
  engine_version = "15.4"
  instance_class = "db.t3.medium"

  allocated_storage     = 100
  max_allocated_storage = 500
  storage_encrypted     = true
  kms_key_id           = aws_kms_key.airflow.arn

  db_name  = "airflow"
  username = "airflow"
  password = random_password.db_password.result

  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.airflow.name

  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "Mon:04:00-Mon:05:00"

  multi_az               = true
  deletion_protection    = true
  skip_final_snapshot    = false
  final_snapshot_identifier = "airflow-${var.workspace_id}-final"

  tags = {
    Name      = "airflow-${var.workspace_id}"
    Workspace = var.workspace_id
  }
}

# S3 Bucket for DAGs and Logs
resource "aws_s3_bucket" "airflow" {
  bucket = "airflowcloud-${var.workspace_id}-${data.aws_caller_identity.current.account_id}"

  tags = {
    Name      = "airflow-${var.workspace_id}"
    Workspace = var.workspace_id
  }
}

resource "aws_s3_bucket_versioning" "airflow" {
  bucket = aws_s3_bucket.airflow.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "airflow" {
  bucket = aws_s3_bucket.airflow.id

  rule {
    id     = "logs-retention"
    status = "Enabled"

    filter {
      prefix = "logs/"
    }

    expiration {
      days = 90
    }
  }
}

# Helm Release for Airflow
resource "helm_release" "airflow" {
  name       = "airflow"
  namespace  = kubernetes_namespace.airflow.metadata[0].name
  repository = "https://airflow.apache.org"
  chart      = "airflow"
  version    = var.airflow_version

  values = [
    templatefile("${path.module}/templates/airflow-values.yaml", {
      workspace_id     = var.workspace_id
      executor         = var.executor
      scheduler_cpu    = var.resources.scheduler_cpu
      scheduler_memory = var.resources.scheduler_memory
      worker_cpu       = var.resources.worker_cpu
      worker_memory    = var.resources.worker_memory
      worker_count     = var.resources.worker_count
      db_host          = aws_db_instance.airflow_metadata.endpoint
      s3_bucket        = aws_s3_bucket.airflow.id
    })
  ]

  depends_on = [
    aws_db_instance.airflow_metadata,
    aws_s3_bucket.airflow
  ]
}
```

---

## 8. Multi-Cloud Extensibility

### 8.1 Cloud Provider Abstraction

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    CLOUD PROVIDER ABSTRACTION LAYER                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    PROVIDER INTERFACE                                │    │
│  │                                                                      │    │
│  │  interface CloudProvider {                                           │    │
│  │    // Compute                                                        │    │
│  │    createKubernetesCluster(spec): Cluster                           │    │
│  │    scaleNodePool(clusterId, spec): void                             │    │
│  │                                                                      │    │
│  │    // Database                                                       │    │
│  │    createDatabase(spec): Database                                   │    │
│  │    createDatabaseReplica(dbId, region): Database                    │    │
│  │                                                                      │    │
│  │    // Storage                                                        │    │
│  │    createBucket(spec): Bucket                                       │    │
│  │    configureBucketPolicy(bucketId, policy): void                    │    │
│  │                                                                      │    │
│  │    // Networking                                                     │    │
│  │    createVPC(spec): VPC                                             │    │
│  │    createLoadBalancer(spec): LoadBalancer                           │    │
│  │                                                                      │    │
│  │    // Security                                                       │    │
│  │    createSecret(spec): Secret                                       │    │
│  │    createIAMRole(spec): IAMRole                                     │    │
│  │  }                                                                   │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│         ┌──────────────────────────┼──────────────────────────┐             │
│         ▼                          ▼                          ▼             │
│  ┌─────────────────┐       ┌─────────────────┐       ┌─────────────────┐   │
│  │  AWS Provider   │       │  GCP Provider   │       │ Azure Provider  │   │
│  │                 │       │                 │       │                 │   │
│  │ • EKS           │       │ • GKE           │       │ • AKS           │   │
│  │ • RDS           │       │ • Cloud SQL     │       │ • Azure SQL     │   │
│  │ • S3            │       │ • GCS           │       │ • Blob Storage  │   │
│  │ • VPC           │       │ • VPC           │       │ • VNet          │   │
│  │ • IAM           │       │ • IAM           │       │ • AAD           │   │
│  │ • Secrets Mgr   │       │ • Secret Mgr    │       │ • Key Vault     │   │
│  └─────────────────┘       └─────────────────┘       └─────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 Provider Implementation

```python
# providers/base.py
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional

@dataclass
class KubernetesClusterSpec:
    name: str
    version: str
    node_pools: list["NodePoolSpec"]
    networking: "NetworkingSpec"

@dataclass
class NodePoolSpec:
    name: str
    instance_type: str
    min_nodes: int
    max_nodes: int
    labels: dict[str, str]

@dataclass
class DatabaseSpec:
    name: str
    engine: str
    version: str
    instance_size: str
    storage_gb: int
    multi_az: bool
    encrypted: bool

class CloudProvider(ABC):
    """Abstract base class for cloud providers"""

    @abstractmethod
    async def create_kubernetes_cluster(
        self, spec: KubernetesClusterSpec
    ) -> "KubernetesCluster":
        pass

    @abstractmethod
    async def create_database(
        self, spec: DatabaseSpec
    ) -> "Database":
        pass

    @abstractmethod
    async def create_storage_bucket(
        self, name: str, region: str
    ) -> "StorageBucket":
        pass

# providers/aws.py
class AWSProvider(CloudProvider):
    """AWS implementation of CloudProvider"""

    def __init__(self, region: str, credentials: "AWSCredentials"):
        self.region = region
        self.eks_client = boto3.client('eks', region_name=region)
        self.rds_client = boto3.client('rds', region_name=region)
        self.s3_client = boto3.client('s3', region_name=region)

    async def create_kubernetes_cluster(
        self, spec: KubernetesClusterSpec
    ) -> "KubernetesCluster":
        # Create EKS cluster
        response = self.eks_client.create_cluster(
            name=spec.name,
            version=spec.version,
            roleArn=self._get_cluster_role_arn(),
            resourcesVpcConfig={
                'subnetIds': spec.networking.subnet_ids,
                'securityGroupIds': spec.networking.security_group_ids,
            }
        )

        # Wait for cluster to be active
        await self._wait_for_cluster(spec.name)

        # Create node pools
        for pool in spec.node_pools:
            await self._create_node_group(spec.name, pool)

        return KubernetesCluster(
            id=response['cluster']['arn'],
            name=spec.name,
            endpoint=response['cluster']['endpoint'],
            provider='aws'
        )

    async def create_database(self, spec: DatabaseSpec) -> "Database":
        response = self.rds_client.create_db_instance(
            DBInstanceIdentifier=spec.name,
            Engine=spec.engine,
            EngineVersion=spec.version,
            DBInstanceClass=self._map_instance_size(spec.instance_size),
            AllocatedStorage=spec.storage_gb,
            MultiAZ=spec.multi_az,
            StorageEncrypted=spec.encrypted,
            # ... additional configuration
        )

        return Database(
            id=response['DBInstance']['DBInstanceArn'],
            endpoint=response['DBInstance']['Endpoint']['Address'],
            port=response['DBInstance']['Endpoint']['Port'],
            provider='aws'
        )

# providers/gcp.py
class GCPProvider(CloudProvider):
    """GCP implementation of CloudProvider"""

    async def create_kubernetes_cluster(
        self, spec: KubernetesClusterSpec
    ) -> "KubernetesCluster":
        # Create GKE cluster
        cluster = {
            'name': spec.name,
            'initial_node_count': spec.node_pools[0].min_nodes,
            'node_config': {
                'machine_type': self._map_instance_type(
                    spec.node_pools[0].instance_type
                ),
            },
            # ... GKE specific config
        }

        operation = self.container_client.create_cluster(
            project_id=self.project_id,
            zone=self.zone,
            cluster=cluster
        )

        await self._wait_for_operation(operation)

        return KubernetesCluster(...)

# providers/azure.py
class AzureProvider(CloudProvider):
    """Azure implementation of CloudProvider"""

    async def create_kubernetes_cluster(
        self, spec: KubernetesClusterSpec
    ) -> "KubernetesCluster":
        # Create AKS cluster
        aks_cluster = ManagedCluster(
            location=self.location,
            dns_prefix=spec.name,
            kubernetes_version=spec.version,
            agent_pool_profiles=[
                ManagedClusterAgentPoolProfile(
                    name=pool.name,
                    count=pool.min_nodes,
                    vm_size=self._map_instance_type(pool.instance_type),
                    # ... AKS specific config
                )
                for pool in spec.node_pools
            ]
        )

        result = self.container_client.managed_clusters.begin_create_or_update(
            self.resource_group,
            spec.name,
            aks_cluster
        ).result()

        return KubernetesCluster(...)
```

### 8.3 Service Mapping Across Clouds

| Capability | AWS | GCP | Azure |
|------------|-----|-----|-------|
| **Kubernetes** | EKS | GKE | AKS |
| **PostgreSQL** | RDS | Cloud SQL | Azure Database |
| **Object Storage** | S3 | GCS | Blob Storage |
| **Secrets** | Secrets Manager | Secret Manager | Key Vault |
| **IAM** | IAM | IAM | Azure AD |
| **Load Balancer** | ALB/NLB | Cloud Load Balancing | Azure LB |
| **DNS** | Route 53 | Cloud DNS | Azure DNS |
| **Monitoring** | CloudWatch | Cloud Monitoring | Azure Monitor |
| **VPC** | VPC | VPC | VNet |
| **Message Queue** | SQS | Pub/Sub | Service Bus |

---

## 9. Security Architecture

### 9.1 Security Layers

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SECURITY ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    PERIMETER SECURITY                                │    │
│  │  • WAF (Web Application Firewall)                                   │    │
│  │  • DDoS Protection                                                  │    │
│  │  • Rate Limiting                                                    │    │
│  │  • IP Allowlisting (optional)                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                    IDENTITY & ACCESS                                 │    │
│  │  • SSO/SAML Integration                                             │    │
│  │  • Multi-Factor Authentication                                      │    │
│  │  • API Key Management                                               │    │
│  │  • Role-Based Access Control (RBAC)                                 │    │
│  │  • Service Account Management                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                    NETWORK SECURITY                                  │    │
│  │  • VPC Isolation per Customer                                       │    │
│  │  • Private Subnets for Data Plane                                   │    │
│  │  • Security Groups / Network Policies                               │    │
│  │  • mTLS for Service-to-Service                                      │    │
│  │  • VPN/PrivateLink for Hybrid                                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                    DATA SECURITY                                     │    │
│  │  • Encryption at Rest (AES-256)                                     │    │
│  │  • Encryption in Transit (TLS 1.3)                                  │    │
│  │  • Customer-Managed Keys (BYOK)                                     │    │
│  │  • Secret Management (Vault/Secrets Manager)                        │    │
│  │  • Data Masking in Logs                                             │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                    AUDIT & COMPLIANCE                                │    │
│  │  • Comprehensive Audit Logging                                      │    │
│  │  • SOC 2 Type II Compliance                                         │    │
│  │  • HIPAA Ready                                                      │    │
│  │  • GDPR Compliance                                                  │    │
│  │  • Penetration Testing                                              │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 RBAC Model

```python
# security/rbac.py
from enum import Enum
from dataclasses import dataclass

class Permission(Enum):
    # Workspace permissions
    WORKSPACE_READ = "workspace:read"
    WORKSPACE_WRITE = "workspace:write"
    WORKSPACE_DELETE = "workspace:delete"
    WORKSPACE_ADMIN = "workspace:admin"

    # Deployment permissions
    DEPLOYMENT_READ = "deployment:read"
    DEPLOYMENT_WRITE = "deployment:write"
    DEPLOYMENT_DELETE = "deployment:delete"
    DEPLOYMENT_SCALE = "deployment:scale"
    DEPLOYMENT_UPGRADE = "deployment:upgrade"

    # DAG permissions
    DAG_READ = "dag:read"
    DAG_WRITE = "dag:write"
    DAG_DELETE = "dag:delete"
    DAG_TRIGGER = "dag:trigger"

    # Secret permissions
    SECRET_READ = "secret:read"
    SECRET_WRITE = "secret:write"
    SECRET_DELETE = "secret:delete"

    # Admin permissions
    USER_MANAGE = "user:manage"
    BILLING_MANAGE = "billing:manage"
    AUDIT_READ = "audit:read"

@dataclass
class Role:
    name: str
    permissions: list[Permission]
    description: str

# Predefined roles
ROLES = {
    "workspace_admin": Role(
        name="Workspace Admin",
        permissions=[
            Permission.WORKSPACE_READ,
            Permission.WORKSPACE_WRITE,
            Permission.DEPLOYMENT_READ,
            Permission.DEPLOYMENT_WRITE,
            Permission.DEPLOYMENT_SCALE,
            Permission.DEPLOYMENT_UPGRADE,
            Permission.DAG_READ,
            Permission.DAG_WRITE,
            Permission.DAG_TRIGGER,
            Permission.SECRET_READ,
            Permission.SECRET_WRITE,
            Permission.USER_MANAGE,
        ],
        description="Full access to workspace resources"
    ),

    "developer": Role(
        name="Developer",
        permissions=[
            Permission.WORKSPACE_READ,
            Permission.DEPLOYMENT_READ,
            Permission.DAG_READ,
            Permission.DAG_WRITE,
            Permission.DAG_TRIGGER,
            Permission.SECRET_READ,
        ],
        description="Deploy and manage DAGs"
    ),

    "viewer": Role(
        name="Viewer",
        permissions=[
            Permission.WORKSPACE_READ,
            Permission.DEPLOYMENT_READ,
            Permission.DAG_READ,
        ],
        description="Read-only access"
    ),

    "operator": Role(
        name="Operator",
        permissions=[
            Permission.WORKSPACE_READ,
            Permission.DEPLOYMENT_READ,
            Permission.DEPLOYMENT_SCALE,
            Permission.DAG_READ,
            Permission.DAG_TRIGGER,
        ],
        description="Operate deployments and trigger DAGs"
    ),
}
```

### 9.3 Secret Management

```yaml
# Secret management architecture
apiVersion: airflowcloud.io/v1
kind: SecretBackend
metadata:
  name: customer-secrets
spec:
  # Backend type
  backend: aws-secrets-manager  # or: vault, gcp-secret-manager, azure-keyvault

  # AWS Secrets Manager configuration
  aws:
    region: us-east-1
    kmsKeyId: alias/airflowcloud-secrets

  # Secret rotation
  rotation:
    enabled: true
    intervalDays: 30

  # Access control
  access:
    # Which deployments can access
    deployments:
      - deployment-a
      - deployment-b
    # Which secret paths are accessible
    paths:
      - "airflow/connections/*"
      - "airflow/variables/*"
```

---

## 10. API Design

### 10.1 API Overview

```yaml
openapi: 3.0.3
info:
  title: AirflowCloud API
  version: 1.0.0
  description: |
    RESTful API for managing AirflowCloud resources.

servers:
  - url: https://api.airflowcloud.io/v1
    description: Production API

security:
  - bearerAuth: []
  - apiKey: []

paths:
  # ─────────────────────────────────────────────────────────────────────────
  # WORKSPACES
  # ─────────────────────────────────────────────────────────────────────────

  /workspaces:
    get:
      summary: List workspaces
      tags: [Workspaces]
      responses:
        '200':
          description: List of workspaces
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Workspace'

    post:
      summary: Create workspace
      tags: [Workspaces]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateWorkspaceRequest'
      responses:
        '201':
          description: Workspace created
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Workspace'

  /workspaces/{workspaceId}:
    get:
      summary: Get workspace
      tags: [Workspaces]
      parameters:
        - name: workspaceId
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: Workspace details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Workspace'

  # ─────────────────────────────────────────────────────────────────────────
  # DEPLOYMENTS
  # ─────────────────────────────────────────────────────────────────────────

  /workspaces/{workspaceId}/deployments:
    get:
      summary: List deployments
      tags: [Deployments]
      responses:
        '200':
          description: List of deployments

    post:
      summary: Create deployment
      tags: [Deployments]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateDeploymentRequest'
      responses:
        '202':
          description: Deployment creation initiated
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Deployment'

  /workspaces/{workspaceId}/deployments/{deploymentId}:
    get:
      summary: Get deployment
      tags: [Deployments]

    patch:
      summary: Update deployment
      tags: [Deployments]

    delete:
      summary: Delete deployment
      tags: [Deployments]

  /workspaces/{workspaceId}/deployments/{deploymentId}/scale:
    post:
      summary: Scale deployment
      tags: [Deployments]
      requestBody:
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/ScaleRequest'

  /workspaces/{workspaceId}/deployments/{deploymentId}/upgrade:
    post:
      summary: Upgrade Airflow version
      tags: [Deployments]

  # ─────────────────────────────────────────────────────────────────────────
  # DAGS
  # ─────────────────────────────────────────────────────────────────────────

  /workspaces/{workspaceId}/deployments/{deploymentId}/dags:
    get:
      summary: List DAGs
      tags: [DAGs]

    post:
      summary: Deploy DAGs
      tags: [DAGs]
      requestBody:
        content:
          multipart/form-data:
            schema:
              type: object
              properties:
                files:
                  type: array
                  items:
                    type: string
                    format: binary

  /workspaces/{workspaceId}/deployments/{deploymentId}/dags/{dagId}/runs:
    get:
      summary: List DAG runs
      tags: [DAGs]

    post:
      summary: Trigger DAG run
      tags: [DAGs]

components:
  schemas:
    Workspace:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        description:
          type: string
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    Deployment:
      type: object
      properties:
        id:
          type: string
          format: uuid
        name:
          type: string
        status:
          type: string
          enum: [provisioning, running, upgrading, scaling, stopped, failed]
        airflowVersion:
          type: string
        executor:
          type: string
          enum: [KubernetesExecutor, CeleryExecutor, LocalExecutor]
        resources:
          $ref: '#/components/schemas/DeploymentResources'
        endpoints:
          $ref: '#/components/schemas/DeploymentEndpoints'
        createdAt:
          type: string
          format: date-time

    DeploymentResources:
      type: object
      properties:
        scheduler:
          $ref: '#/components/schemas/ResourceSpec'
        webserver:
          $ref: '#/components/schemas/ResourceSpec'
        workers:
          type: object
          properties:
            count:
              type: integer
            resources:
              $ref: '#/components/schemas/ResourceSpec'

    ResourceSpec:
      type: object
      properties:
        cpu:
          type: string
        memory:
          type: string

    CreateDeploymentRequest:
      type: object
      required:
        - name
      properties:
        name:
          type: string
        airflowVersion:
          type: string
          default: "2.8.1"
        executor:
          type: string
          default: "KubernetesExecutor"
        resources:
          $ref: '#/components/schemas/DeploymentResources'

  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

    apiKey:
      type: apiKey
      in: header
      name: X-API-Key
```

---

## 11. Data Model

### 11.1 Entity Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DATA MODEL                                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐               │
│  │Organization │       │  Workspace  │       │ Deployment  │               │
│  ├─────────────┤       ├─────────────┤       ├─────────────┤               │
│  │ id          │──┐    │ id          │──┐    │ id          │               │
│  │ name        │  │    │ org_id      │◀─┘    │ workspace_id│◀──────────┐   │
│  │ plan        │  │    │ name        │  │    │ name        │           │   │
│  │ created_at  │  │    │ description │  │    │ type        │           │   │
│  └─────────────┘  │    │ created_at  │  │    │ status      │           │   │
│                   │    └─────────────┘  │    │ airflow_ver │           │   │
│                   │           │         │    │ executor    │           │   │
│                   │           │         │    │ resources   │           │   │
│                   │           ▼         │    │ endpoints   │           │   │
│                   │    ┌─────────────┐  │    │ created_at  │           │   │
│                   │    │    Team     │  │    └─────────────┘           │   │
│                   │    ├─────────────┤  │           │                  │   │
│                   │    │ id          │  │           │                  │   │
│                   │    │ workspace_id│◀─┘           │                  │   │
│                   │    │ name        │              │                  │   │
│                   │    │ permissions │              │                  │   │
│                   │    └─────────────┘              │                  │   │
│                   │           │                     │                  │   │
│                   │           ▼                     ▼                  │   │
│                   │    ┌─────────────┐       ┌─────────────┐          │   │
│  ┌─────────────┐  │    │    User     │       │    Agent    │          │   │
│  │   APIKey    │  │    ├─────────────┤       ├─────────────┤          │   │
│  ├─────────────┤  │    │ id          │       │ id          │          │   │
│  │ id          │  │    │ email       │       │ deployment_id│◀─────────┘   │
│  │ org_id      │◀─┘    │ name        │       │ status      │              │
│  │ name        │       │ role        │       │ version     │              │
│  │ key_hash    │       │ team_ids    │       │ last_seen   │              │
│  │ permissions │       │ created_at  │       │ metadata    │              │
│  │ expires_at  │       └─────────────┘       └─────────────┘              │
│  └─────────────┘                                                          │
│                                                                            │
│  ┌─────────────┐       ┌─────────────┐       ┌─────────────┐              │
│  │    DAG      │       │   DAGRun    │       │  TaskRun    │              │
│  ├─────────────┤       ├─────────────┤       ├─────────────┤              │
│  │ id          │──┐    │ id          │──┐    │ id          │              │
│  │ deployment_id│  │    │ dag_id      │◀─┘    │ dag_run_id  │◀─────────┐   │
│  │ dag_id      │  │    │ run_id      │  │    │ task_id     │          │   │
│  │ file_path   │  │    │ state       │  │    │ state       │          │   │
│  │ is_paused   │  │    │ start_date  │  │    │ start_date  │          │   │
│  │ schedule    │  │    │ end_date    │  │    │ end_date    │          │   │
│  │ last_parsed │  │    │ conf        │  │    │ try_number  │          │   │
│  └─────────────┘  │    └─────────────┘  │    └─────────────┘          │   │
│                   │                     │                              │   │
│                   └─────────────────────┴──────────────────────────────┘   │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### 11.2 Database Schema

```sql
-- Organizations
CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) UNIQUE NOT NULL,
    plan VARCHAR(50) NOT NULL DEFAULT 'starter',
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Workspaces
CREATE TABLE workspaces (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL,
    description TEXT,
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(organization_id, slug)
);

-- Deployments
CREATE TABLE deployments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    name VARCHAR(255) NOT NULL,
    deployment_type VARCHAR(50) NOT NULL, -- public_cloud, agent_based, private_cloud
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    airflow_version VARCHAR(20) NOT NULL,
    executor VARCHAR(50) NOT NULL DEFAULT 'KubernetesExecutor',
    resources JSONB NOT NULL,
    endpoints JSONB,
    cloud_provider VARCHAR(20), -- aws, gcp, azure
    cloud_region VARCHAR(50),
    infrastructure_config JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(workspace_id, name)
);

-- Agents (for hybrid deployments)
CREATE TABLE agents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    deployment_id UUID NOT NULL REFERENCES deployments(id),
    registration_token_hash VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    version VARCHAR(20),
    last_heartbeat TIMESTAMP WITH TIME ZONE,
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255),
    password_hash VARCHAR(255),
    auth_provider VARCHAR(50), -- local, google, github, saml
    auth_provider_id VARCHAR(255),
    mfa_enabled BOOLEAN DEFAULT FALSE,
    mfa_secret VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Organization memberships
CREATE TABLE organization_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL DEFAULT 'member',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(organization_id, user_id)
);

-- Workspace memberships
CREATE TABLE workspace_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID NOT NULL REFERENCES workspaces(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(50) NOT NULL DEFAULT 'viewer',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(workspace_id, user_id)
);

-- API Keys
CREATE TABLE api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    key_prefix VARCHAR(10) NOT NULL, -- First 10 chars for identification
    key_hash VARCHAR(255) NOT NULL,
    permissions JSONB DEFAULT '[]',
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Audit logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id UUID,
    metadata JSONB DEFAULT '{}',
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_workspaces_org ON workspaces(organization_id);
CREATE INDEX idx_deployments_workspace ON deployments(workspace_id);
CREATE INDEX idx_agents_deployment ON agents(deployment_id);
CREATE INDEX idx_audit_logs_org ON audit_logs(organization_id);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at);
```

---

## 12. Observability & Monitoring

### 12.1 Metrics Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       OBSERVABILITY STACK                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         METRICS                                      │    │
│  │                                                                      │    │
│  │  Sources:                    Pipeline:              Storage:         │    │
│  │  ┌─────────────┐            ┌─────────────┐        ┌─────────────┐  │    │
│  │  │ Airflow     │───────────▶│ Prometheus  │───────▶│ Thanos /    │  │    │
│  │  │ StatsD      │            │             │        │ Cortex      │  │    │
│  │  └─────────────┘            └─────────────┘        └─────────────┘  │    │
│  │  ┌─────────────┐            ┌─────────────┐        ┌─────────────┐  │    │
│  │  │ K8s Metrics │───────────▶│ Prometheus  │───────▶│ Long-term   │  │    │
│  │  │ Server      │            │ Federation  │        │ Storage     │  │    │
│  │  └─────────────┘            └─────────────┘        └─────────────┘  │    │
│  │  ┌─────────────┐                                                    │    │
│  │  │ Custom      │                                                    │    │
│  │  │ Exporters   │                                                    │    │
│  │  └─────────────┘                                                    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         LOGGING                                      │    │
│  │                                                                      │    │
│  │  Sources:                    Pipeline:              Storage:         │    │
│  │  ┌─────────────┐            ┌─────────────┐        ┌─────────────┐  │    │
│  │  │ Airflow     │───────────▶│ Fluent Bit  │───────▶│ Elasticsearch│  │    │
│  │  │ Task Logs   │            │             │        │ / Loki      │  │    │
│  │  └─────────────┘            └─────────────┘        └─────────────┘  │    │
│  │  ┌─────────────┐            ┌─────────────┐                         │    │
│  │  │ K8s Pod     │───────────▶│ Log         │                         │    │
│  │  │ Logs        │            │ Aggregator  │                         │    │
│  │  └─────────────┘            └─────────────┘                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         TRACING                                      │    │
│  │                                                                      │    │
│  │  ┌─────────────┐            ┌─────────────┐        ┌─────────────┐  │    │
│  │  │ OpenTelemetry│───────────▶│ Collector   │───────▶│ Jaeger /    │  │    │
│  │  │ SDK         │            │             │        │ Tempo       │  │    │
│  │  └─────────────┘            └─────────────┘        └─────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         ALERTING                                     │    │
│  │                                                                      │    │
│  │  ┌─────────────┐            ┌─────────────┐        ┌─────────────┐  │    │
│  │  │ Alert Rules │───────────▶│ Alertmanager│───────▶│ PagerDuty   │  │    │
│  │  │             │            │             │        │ Slack       │  │    │
│  │  │             │            │             │        │ Email       │  │    │
│  │  └─────────────┘            └─────────────┘        └─────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 12.2 Key Metrics

| Category | Metric | Description | Alert Threshold |
|----------|--------|-------------|-----------------|
| **Scheduler** | `scheduler_heartbeat` | Scheduler health | No heartbeat > 30s |
| **Scheduler** | `dag_processing_time` | DAG parse time | > 30s |
| **Tasks** | `task_success_rate` | Task success % | < 95% |
| **Tasks** | `task_duration_seconds` | Task execution time | > SLA |
| **Workers** | `worker_count` | Active workers | < min_workers |
| **Workers** | `worker_cpu_usage` | CPU utilization | > 80% |
| **Database** | `db_connections` | Active connections | > 80% of max |
| **Database** | `db_query_latency` | Query latency | > 100ms |
| **API** | `api_request_latency` | API response time | > 500ms |
| **API** | `api_error_rate` | 5xx error rate | > 1% |

---

## 13. Extensibility Framework

### 13.1 Plugin Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       PLUGIN ARCHITECTURE                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    PLUGIN REGISTRY                                   │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌────────────┐ │    │
│  │  │ Auth        │  │ Secrets     │  │ Notification│  │ Custom     │ │    │
│  │  │ Plugins     │  │ Backends    │  │ Channels    │  │ Operators  │ │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └────────────┘ │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                    │                                         │
│  ┌─────────────────────────────────┴───────────────────────────────────┐    │
│  │                    PLUGIN INTERFACES                                 │    │
│  │                                                                      │    │
│  │  interface AuthPlugin {                                              │    │
│  │    authenticate(credentials): User                                  │    │
│  │    authorize(user, resource, action): boolean                       │    │
│  │  }                                                                   │    │
│  │                                                                      │    │
│  │  interface SecretsBackend {                                          │    │
│  │    getSecret(key): string                                           │    │
│  │    setSecret(key, value): void                                      │    │
│  │    listSecrets(): string[]                                          │    │
│  │  }                                                                   │    │
│  │                                                                      │    │
│  │  interface NotificationChannel {                                     │    │
│  │    send(message, recipients): void                                  │    │
│  │  }                                                                   │    │
│  │                                                                      │    │
│  │  interface CloudProvider {                                           │    │
│  │    // See Section 8.2                                               │    │
│  │  }                                                                   │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                    BUILT-IN PLUGINS                                  │    │
│  │                                                                      │    │
│  │  Auth:           Secrets:           Notifications:                   │    │
│  │  • SAML          • AWS Secrets Mgr  • Slack                         │    │
│  │  • OIDC          • HashiCorp Vault  • PagerDuty                     │    │
│  │  • LDAP          • GCP Secret Mgr   • Email                         │    │
│  │  • GitHub        • Azure Key Vault  • Webhook                       │    │
│  │                                                                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 13.2 Custom Plugin Example

```python
# plugins/custom_secrets_backend.py
from airflowcloud.plugins import SecretsBackend, register_plugin

@register_plugin("secrets-backend", "custom-vault")
class CustomVaultBackend(SecretsBackend):
    """
    Custom secrets backend for internal vault system.
    """

    def __init__(self, config: dict):
        self.vault_url = config["vault_url"]
        self.auth_method = config.get("auth_method", "token")
        self._client = self._create_client()

    def get_secret(self, key: str) -> str:
        """Retrieve secret from vault"""
        response = self._client.read(f"secret/data/{key}")
        return response["data"]["data"]["value"]

    def set_secret(self, key: str, value: str) -> None:
        """Store secret in vault"""
        self._client.write(
            f"secret/data/{key}",
            data={"value": value}
        )

    def list_secrets(self) -> list[str]:
        """List all secret keys"""
        response = self._client.list("secret/metadata")
        return response["data"]["keys"]

    def _create_client(self):
        import hvac
        client = hvac.Client(url=self.vault_url)
        # Configure authentication
        return client
```

---

## 14. Migration & Upgrade Strategy

### 14.1 Airflow Version Upgrades

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    UPGRADE WORKFLOW                                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐   │
│  │ Validate│───▶│ Backup  │───▶│ Deploy  │───▶│ Verify  │───▶│ Cutover │   │
│  │         │    │         │    │ Canary  │    │         │    │         │   │
│  └─────────┘    └─────────┘    └─────────┘    └─────────┘    └─────────┘   │
│       │              │              │              │              │         │
│       ▼              ▼              ▼              ▼              ▼         │
│  • Check DAG     • Metadata    • Deploy new   • Run health  • Switch     │
│    compatibility   snapshot      version in     checks        traffic     │
│  • Verify deps   • Config        parallel     • Validate    • Monitor    │
│  • Test in         backup      • Route 10%     DAGs        • Rollback   │
│    staging                       traffic                     ready       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

### 14.2 Zero-Downtime Upgrade Process

```python
# upgrade/orchestrator.py
class UpgradeOrchestrator:
    """
    Manages zero-downtime Airflow upgrades using blue-green deployment.
    """

    async def upgrade(
        self,
        deployment_id: str,
        target_version: str,
        strategy: str = "blue_green"
    ) -> UpgradeResult:

        deployment = await self.get_deployment(deployment_id)

        # Phase 1: Validation
        await self._validate_upgrade_path(
            deployment.airflow_version,
            target_version
        )

        # Phase 2: Backup
        backup = await self._create_backup(deployment)

        # Phase 3: Deploy canary
        canary = await self._deploy_canary(deployment, target_version)

        # Phase 4: Gradual traffic shift
        for percentage in [10, 25, 50, 75, 100]:
            await self._shift_traffic(deployment, canary, percentage)

            # Monitor for errors
            if await self._detect_errors(canary):
                await self._rollback(deployment, canary, backup)
                raise UpgradeError("Errors detected, rolled back")

            await asyncio.sleep(60)  # Wait between shifts

        # Phase 5: Cleanup old deployment
        await self._cleanup_old_deployment(deployment)

        return UpgradeResult(
            success=True,
            new_version=target_version,
            duration=self._get_duration()
        )
```

---

## 15. Future Roadmap

### 15.1 Phase 1: Foundation (Current)

| Feature | Status | Target |
|---------|--------|--------|
| Public Cloud Deployment (AWS) | ✅ Complete | Q1 |
| Agent-Based Deployment | ✅ Complete | Q1 |
| CLI v1.0 | ✅ Complete | Q1 |
| Basic RBAC | ✅ Complete | Q1 |
| Monitoring Dashboard | ✅ Complete | Q1 |

### 15.2 Phase 2: Enterprise Features (Next)

| Feature | Status | Target |
|---------|--------|--------|
| SSO/SAML Integration | 🔄 In Progress | Q2 |
| Private Cloud Deployment | 📋 Planned | Q2 |
| Multi-Region Support | 📋 Planned | Q2 |
| Advanced RBAC | 📋 Planned | Q2 |
| Audit Logging | 📋 Planned | Q2 |

### 15.3 Phase 3: Multi-Cloud & Advanced (Future)

| Feature | Status | Target |
|---------|--------|--------|
| GCP Support | 📋 Planned | Q3 |
| Azure Support | 📋 Planned | Q3 |
| Remote Execution | 📋 Planned | Q3 |
| GitOps Integration | 📋 Planned | Q3 |
| Custom Operators Marketplace | 📋 Planned | Q4 |

### 15.4 Phase 4: Platform Maturity

| Feature | Status | Target |
|---------|--------|--------|
| Multi-Tenant Isolation | 📋 Planned | Q4 |
| Cost Optimization Engine | 📋 Planned | Q4 |
| AI-Powered DAG Optimization | 📋 Planned | Q4+ |
| Federated Deployments | 📋 Planned | Q4+ |

---

## 16. Appendix

### 16.1 Glossary

| Term | Definition |
|------|------------|
| **Workspace** | Logical grouping of deployments, teams, and resources |
| **Deployment** | Single Airflow environment with scheduler, workers, webserver |
| **Agent** | Lightweight component installed in customer infrastructure |
| **Control Plane** | Central management services (API, auth, orchestration) |
| **Data Plane** | Customer-specific resources (database, storage, compute) |
| **Executor** | Airflow component that runs tasks (Kubernetes, Celery, Local) |

### 16.2 References

- [Apache Airflow Documentation](https://airflow.apache.org/docs/)
- [Astronomer Documentation](https://docs.astronomer.io/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [AWS EKS Best Practices](https://aws.github.io/aws-eks-best-practices/)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)

### 16.3 Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | April 2026 | Platform Team | Initial release |

---

*This document is confidential and intended for internal use only.*
