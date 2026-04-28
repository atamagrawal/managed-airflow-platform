# FlowDeck Platform — Product Design Document

**Version:** 1.0  
**Status:** Draft  
**Project Codename:** FlowDeck  
**Audience:** Engineering, Architecture, Product

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Product Vision & Deployment Tiers](#2-product-vision--deployment-tiers)
3. [Core Architecture Principles](#3-core-architecture-principles)
4. [System Components](#4-system-components)
5. [Deployment Mode 1 — Public Cloud (Fully Managed)](#5-deployment-mode-1--public-cloud-fully-managed)
6. [Deployment Mode 2 — Agent-Based Managed Airflow](#6-deployment-mode-2--agent-based-managed-airflow)
7. [Deployment Mode 3 — FlowDeck CLI](#7-deployment-mode-3--flowdeck-cli)
8. [Control Plane Deep Dive](#8-control-plane-deep-dive)
9. [Data Plane Deep Dive](#9-data-plane-deep-dive)
10. [Agent Architecture Deep Dive](#10-agent-architecture-deep-dive)
11. [API Design](#11-api-design)
12. [Authentication & Authorization](#12-authentication--authorization)
13. [Observability Stack](#13-observability-stack)
14. [Multi-Tenancy Model](#14-multi-tenancy-model)
15. [Extension Path — Remote Execution Mode](#15-extension-path--remote-execution-mode)
16. [Extension Path — Private Cloud Mode](#16-extension-path--private-cloud-mode)
17. [Extension Path — Hybrid Multi-Region](#17-extension-path--hybrid-multi-region)
18. [Data Model](#18-data-model)
19. [Technology Stack Choices](#19-technology-stack-choices)
20. [Security Architecture](#20-security-architecture)
21. [Rollout & Migration Strategy](#21-rollout--migration-strategy)
22. [Open Questions & Decisions](#22-open-questions--decisions)

---

## 1. Executive Summary

This document describes the architecture of **FlowDeck** — a managed Apache Airflow orchestration platform. FlowDeck is designed from the ground up as a multi-mode, extensible system that serves customers across the full spectrum of deployment preferences:

- **Mode 1 — Public Cloud:** Fully managed Airflow deployments hosted in FlowDeck's cloud. Zero customer infrastructure involvement.
- **Mode 2 — Agent-Based:** Customer installs a lightweight agent in their own Kubernetes cluster. FlowDeck manages and provisions Airflow inside the customer's environment.
- **Mode 3 — CLI:** Developer tooling (`flowdeck`) for local development, CI/CD integration, DAG authoring, connection management, and deployment operations.

The architecture is intentionally layered so that future deployment modes — **Remote Execution** (task-only isolation) and full **Private Cloud** (everything on-premises) — are delivered as extensions without redesigning the core.

---

## 2. Product Vision & Deployment Tiers

### 2.1 Deployment Tier Matrix

| Feature | Public Cloud | Agent-Based | Remote Execution *(future)* | Private Cloud *(future)* |
|---|---|---|---|---|
| Airflow scheduling | FlowDeck cloud | Customer env | FlowDeck cloud | Customer env |
| Task execution | FlowDeck cloud | Customer env | Customer env | Customer env |
| DAG code location | FlowDeck cloud | Customer env | Customer env | Customer env |
| Metadata DB | FlowDeck cloud | Customer env | FlowDeck cloud | Customer env |
| Secrets | FlowDeck cloud | Customer env | Customer env | Customer env |
| Data access | Via connections | Direct | Direct | Direct |
| Managed by FlowDeck | Fully | Control plane | Orchestration only | Platform layer only |
| Air-gap support | No | Partial | No | Yes |

### 2.2 North Star Architecture

All four modes share a single **Control Plane** (the FlowDeck API server, UI, and identity layer). What changes across modes is where the **Data Plane** runs and how much of it is customer-managed. This plane separation is the single most important architectural principle.

```
┌─────────────────────────────────────────────────────┐
│              FLOWDECK CONTROL PLANE                 │
│  (API Server · UI · IAM · Billing · Observability)  │
└─────────────┬───────────────────────────────────────┘
              │ gRPC / HTTPS (always outbound from agent)
   ┌──────────▼──────────────────────────────────┐
   │           DATA PLANE (per deployment)       │
   │  Scheduler · Workers · Metadata DB          │
   │  [FlowDeck cloud | Customer cluster | Both] │
   └─────────────────────────────────────────────┘
```

---

## 3. Core Architecture Principles

### 3.1 Plane Separation
Control Plane and Data Plane are always logically separate, even in Public Cloud mode where they run in the same infrastructure. Enforced at the API level from day one so moving the Data Plane to a customer environment never requires rearchitecting the Control Plane.

### 3.2 Desired State Reconciliation
The Control Plane maintains a desired state for every Airflow deployment (version, resource profile, environment variables, plugins). A reconciler component (Commander) continuously compares desired vs. actual state and applies changes via Helm. This pattern is identical whether Commander is co-located (Public Cloud) or agent-embedded (Agent-Based).

### 3.3 Outbound-Only Agent Communication
Any component running in a customer environment must communicate exclusively via outbound HTTPS/gRPC connections initiated from the customer side. No inbound connections, no VPN tunnels, no firewall exceptions required.

### 3.4 API-First Everything
Every platform capability is exposed via a versioned REST/gRPC API before being surfaced in the UI or CLI. The CLI and UI are consumers of the same API that customers use programmatically.

### 3.5 Airflow 3 as the Foundation
FlowDeck targets Apache Airflow 3.x, which introduces the Task Execution API — a critical enabler. Workers communicate with the API Server (not the database directly), making remote/agent-based execution architecturally sound without database port exposure.

---

## 4. System Components

### 4.1 Component Inventory

| Component | Tier | Purpose | Technology |
|---|---|---|---|
| FlowDeck API Server | Control Plane | Central management API | Go / gRPC + REST |
| FlowDeck UI | Control Plane | Web dashboard | React / Next.js |
| FlowDeck CLI (`flowdeck`) | Client | Developer & operator tooling | Go (single binary) |
| Commander | Data Plane | Desired-state reconciler | Go |
| Config Syncer | Data Plane | Secret & config mirroring | Go |
| Agent | Customer env | Embeds Commander + Syncer | Go (single binary Helm chart) |
| Airflow Runtime | Data Plane | Apache Airflow | Python / Helm chart |
| Metadata DB | Data Plane | Airflow state | PostgreSQL 15+ |
| NATS JetStream | Control Plane | Event bus | NATS |
| Prometheus | Both planes | Metrics | Prometheus |
| Grafana | Control Plane | Dashboards | Grafana |
| Loki + Vector | Both planes | Log aggregation | Loki + Vector |
| Registry | Data Plane | Container image store | OCI-compatible |
| Sentinel | Agent | Agent health monitoring | Go sidecar |

---

## 5. Deployment Mode 1 — Public Cloud (Fully Managed)

### 5.1 Overview

FlowDeck manages everything. The customer interacts only with the UI, CLI, and Airflow itself. All infrastructure (Kubernetes cluster, Postgres, image registry, networking) is provisioned and operated by FlowDeck in its own cloud account.

### 5.2 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        FLOWDECK CLOUD                           │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    Control Plane                         │   │
│  │  FlowDeck API Server · UI · IAM · NATS · Prometheus      │   │
│  │  Platform Postgres · Grafana · Loki                      │   │
│  └─────────────────────┬────────────────────────────────────┘   │
│                        │ Internal k8s networking                │
│  ┌─────────────────────▼────────────────────────────────────┐   │
│  │               Data Plane (per Deployment)                │   │
│  │  ┌────────────┐ ┌────────────┐ ┌───────────────────────┐ │   │
│  │  │ Scheduler  │ │  Workers   │ │  API Server / UI      │ │   │
│  │  └────────────┘ └────────────┘ └───────────────────────┘ │   │
│  │  Commander · Config Syncer · Vector · Airflow Postgres   │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 5.3 Deployment Lifecycle

1. Customer creates a Deployment via UI or `flowdeck deployment create`.
2. API Server stores desired state in the platform DB and publishes an event to NATS.
3. Commander (co-located) picks up the event and runs `helm install` in a new namespace.
4. Config Syncer populates the namespace with credentials, env vars, and connections.
5. Airflow pods start. Commander reports readiness back to the API Server.
6. Customer's DAG bundle is pulled into the Scheduler pod.
7. Customer sees "Running" status in the UI within ~2 minutes.

### 5.4 Resource Isolation

Each Deployment runs in its own Kubernetes namespace with dedicated ResourceQuota/LimitRange, network policies blocking cross-deployment traffic, a separate Postgres schema (or separate instance for enterprise tier), and scoped RBAC service accounts.

### 5.5 Scaling Model

Deployments have a Resource Profile: `small / medium / large / custom`. Each maps to CPU/memory limits. Worker autoscaling is provided via KEDA watching task queue depth.

---

## 6. Deployment Mode 2 — Agent-Based Managed Airflow

### 6.1 Overview

The customer installs the FlowDeck Agent — a single Helm chart — into their own Kubernetes cluster. The agent connects outbound to the FlowDeck Control Plane. FlowDeck then manages Airflow deployments inside the customer's cluster, as it would in Public Cloud mode, but the infrastructure is the customer's.

Ideal for customers who must keep data and code inside their own network, have existing Kubernetes clusters, operate in regulated industries, or want the managed experience without surrendering infrastructure control.

### 6.2 Architecture

```
┌─────────────────────────────────────────┐    HTTPS/gRPC outbound only
│         FLOWDECK CLOUD                  │◄────────────────────────────┐
│                                         │                             │
│  FlowDeck API Server · NATS             │                             │
│  Platform Postgres · UI · Observability │                             │
└─────────────────────────────────────────┘                             │
                                                                        │
┌───────────────────────────────────────────────────────────────────────┤
│                CUSTOMER'S KUBERNETES CLUSTER                          │
│                                                                       │
│  ┌───────────────────────────────────────────────────────────────┐    │
│  │                  FLOWDECK AGENT (Helm chart)                  │    │
│  │  ┌─────────────┐  ┌──────────────┐  ┌──────────────────────┐ │    │
│  │  │  Commander  │  │Config Syncer │  │  Heartbeat/Sentinel  │ │    │
│  │  └──────┬──────┘  └──────┬───────┘  └──────────┬───────────┘ │    │
│  └─────────┼───────────────┼─────────────────────┼─────────────┘    │
│            │ Helm           │ k8s secrets          │ status           │
│  ┌─────────▼───────────────▼─────────────────────▼─────────────┐    │
│  │          Airflow Deployment Namespaces                       │    │
│  │  ┌──────────────────────────────────────────────────────┐    │    │
│  │  │ namespace: flowdeck-deployment-<id>                  │    │    │
│  │  │ Scheduler · Workers · API Server · Postgres          │    │    │
│  │  │ DAG code (Git/S3) · Secrets (customer-managed)       │    │    │
│  │  └──────────────────────────────────────────────────────┘    │    │
│  └───────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
```

### 6.3 Agent Components

**Commander (Reconciler):** Polls the FlowDeck API Server every 10s for desired deployment state. Applies, upgrades, or rolls back Helm releases for Airflow deployments in the customer's cluster. Reports actual state back. Handles graceful rollbacks on failed upgrades.

**Config Syncer:** Pulls deployment configuration from the FlowDeck API Server every 30s. Populates Kubernetes secrets in each Airflow namespace (image pull credentials, Airflow connections, env vars). Supports pluggable secret backends so customers can point it at their own Vault or AWS Secrets Manager.

**Heartbeat Reporter / Sentinel:** Sends a health heartbeat to the API Server every 15s including agent version, Kubernetes version, available CPU/memory, and Airflow deployment health. API Server marks the cluster "Unhealthy" if no heartbeat received for 60s. Sentinel (sidecar) provides deeper monitoring from agent version 1.2.0+.

### 6.4 Agent Installation

```bash
# Customer copies this one-liner from the FlowDeck UI
helm repo add flowdeck https://charts.flowdeck.io
helm repo update

helm install flowdeck-agent flowdeck/agent \
  --namespace flowdeck-system \
  --create-namespace \
  --set agent.token=<cluster-token-from-ui> \
  --set agent.controlPlaneUrl=https://api.flowdeck.io
```

### 6.5 Agent RBAC Scope

The Helm chart creates a ServiceAccount with a ClusterRole limited to:
- `get/list/watch/create/update/delete` on Namespaces labelled `flowdeck.io/managed: "true"`
- Full access within those namespaces: Pods, Deployments, Services, Secrets, ConfigMaps, HelmReleases
- `get/list` on Nodes (for resource reporting only)
- No access to existing customer namespaces, secrets, or workloads

### 6.6 Agent State Machine

```
REGISTERING → (token valid) → HEALTHY → (drift detected) → RECONCILING → HEALTHY
                                   ↓ heartbeat fails
                               DEGRADED → (reconnect) → HEALTHY
                                   ↓ timeout >5min
                               UNHEALTHY → (alert fires)
```

---

## 7. Deployment Mode 3 — FlowDeck CLI

The `flowdeck` CLI is a single compiled Go binary that is the primary interface for local development, CI/CD pipelines, and power users. It provides **full parity** with the Astronomer `astro` CLI and adds FlowDeck-specific extensions.

### 7.1 Distribution

| Channel | Command |
|---|---|
| Homebrew (macOS/Linux) | `brew install flowdeck/tap/flowdeck` |
| Scoop (Windows) | `scoop install flowdeck` |
| winget (Windows) | `winget install FlowDeck.CLI` |
| Direct binary | GitHub Releases (`.exe`, `.tar.gz`, `.deb`, `.rpm`) |
| Docker | `docker run flowdeck/cli:latest` |
| GitHub Action | `flowdeck/setup-cli@v1` |

### 7.2 Global Flags (all commands)

```
-h, --help                    Show help for any command
    --verbosity <string>      Log level: debug | info | warn | error | fatal | panic
-g, --global                  Apply config change globally (not just current project)
    --output <string>         Output format: table | json | yaml (default: table)
    --workspace-id <string>   Target workspace (overrides active workspace)
    --token <string>          API token (for non-interactive / CI use)
```

---

### 7.3 `flowdeck login` / `flowdeck logout`

Authenticate to a FlowDeck installation.

```bash
flowdeck login                          # Browser-based OAuth2 / SSO flow
flowdeck login --token <api-token>      # CI/CD non-interactive (token auth)
flowdeck login <domain>                 # Log in to a specific FlowDeck installation
flowdeck logout                         # Clear local session for active context
flowdeck logout <domain>                # Log out of a specific installation
```

---

### 7.4 `flowdeck auth`

Manage identity separate from the login shortcut.

```bash
flowdeck auth login                     # Same as flowdeck login
flowdeck auth login --token <token>     # Token-based auth
flowdeck auth logout                    # Same as flowdeck logout
```

---

### 7.5 `flowdeck config`

Read and write CLI configuration values stored in `~/.flowdeck/config.yaml`.

```bash
flowdeck config get <setting>           # Print value of a specific setting
flowdeck config set <setting> <value>   # Update or override a config value
flowdeck config --global get <setting>  # Read from global config file
flowdeck config --global set <setting> <value>
```

**Configurable settings:**

| Setting | Description | Default |
|---|---|---|
| `project.name` | Name of the current project | directory name |
| `context` | Active FlowDeck domain / installation | `flowdeck.io` |
| `show_warnings` | Show CLI warnings | `true` |
| `skip_parse` | Skip DAG parse check on deploy | `false` |
| `auto_select_workspace` | Skip workspace selection prompt | `false` |
| `container_runtime` | Container engine: `docker` or `podman` | auto-detected |
| `webserver_port` | Local Airflow UI port | `8080` |
| `postgres_port` | Local Postgres port | `5432` |
| `duplicate_volumes` | Duplicate Docker volumes on restart | `false` |

---

### 7.6 `flowdeck context`

Manage multiple FlowDeck installation contexts (useful for those connecting to both FlowDeck Cloud and a self-hosted Private Cloud instance).

```bash
flowdeck context list                   # List all installations you've authenticated to
flowdeck context switch <domain>        # Switch active installation context
flowdeck context delete <domain>        # Remove a stored installation context
```

---

### 7.7 `flowdeck version`

```bash
flowdeck version                        # Print FlowDeck CLI version and git commit
```

---

### 7.8 `flowdeck completion`

Generate shell autocompletion scripts.

```bash
flowdeck completion bash                # Generate Bash autocompletion script
flowdeck completion zsh                 # Generate Zsh autocompletion script
flowdeck completion fish                # Generate Fish autocompletion script
flowdeck completion powershell          # Generate PowerShell autocompletion script
```

---

### 7.9 `flowdeck dev` — Local Development

Run and manage Airflow locally using Docker or Podman. The `dev` commands are the entry point for all local development workflows.

#### Project Initialization

```bash
flowdeck dev init                              # Scaffold a new FlowDeck project
flowdeck dev init --airflow-version 3.0.2      # Specify Airflow version
flowdeck dev init --from-template <name>       # Use a starter template
#   Templates: etl | dbt-on-flowdeck | generative-ai | ml-pipeline | learning-airflow
flowdeck dev init --name <project-name>        # Set project name
```

**Generated project structure:**

```
my-project/
├── dags/                    # DAG Python files
│   └── example_dag.py
├── plugins/                 # Custom Airflow plugins
├── include/                 # Supporting files (SQL, configs, schemas)
├── tests/
│   └── dags/
│       └── test_dag_example.py
├── Dockerfile               # Extends flowdeck/airflow:<version>
├── requirements.txt         # Python dependencies
├── packages.txt             # OS-level packages (apt)
├── airflow_settings.yaml    # Local connections, variables, pools
└── .flowdeck/
    └── config.yaml          # Project config (Airflow version, deployments)
```

#### Running the Local Environment

```bash
flowdeck dev start                             # Build Docker image & start all Airflow components
flowdeck dev start --no-browser                # Start without opening Airflow UI in browser
flowdeck dev start --wait                      # Block until all components are healthy
flowdeck dev start --env-file <path>           # Load additional env vars from a file

flowdeck dev stop                              # Pause all local containers (preserves volumes)
flowdeck dev kill                              # Force-stop and remove all local containers + volumes

flowdeck dev restart                           # Stop + rebuild image + start (applies all changes)
flowdeck dev restart --no-cache                # Force full Docker image rebuild

flowdeck dev ps                                # Show status of all local Airflow containers
```

#### Testing & Validation

```bash
flowdeck dev parse                             # Parse all DAGs — surface import/syntax errors fast
flowdeck dev pytest                            # Run all tests in tests/ directory using pytest
flowdeck dev pytest <test-path>                # Run a specific test file or directory
flowdeck dev pytest --args "<pytest-flags>"    # Pass extra flags to pytest

flowdeck dev upgrade-test                      # Lint project for compatibility with a newer Airflow version
flowdeck dev upgrade-test --version <version>  # Target specific Airflow version
```

#### Logs

```bash
flowdeck dev logs                              # Stream logs from all local components
flowdeck dev logs --scheduler                  # Stream scheduler logs only
flowdeck dev logs --webserver                  # Stream webserver / API server logs
flowdeck dev logs --triggerer                  # Stream triggerer logs
flowdeck dev logs --follow                     # Tail logs continuously
```

#### Executing Commands Inside Local Containers

```bash
flowdeck dev run <airflow-command>             # Run any Airflow CLI command in local containers
# Examples:
flowdeck dev run dags list
flowdeck dev run connections list
flowdeck dev run tasks run <dag-id> <task-id> <execution-date>

flowdeck dev bash                              # Open an interactive bash shell in the scheduler container
flowdeck dev airflow <command>                 # Alias for flowdeck dev run
```

#### Object Import / Export (Local)

```bash
flowdeck dev object import                     # Import connections, variables, pools from airflow_settings.yaml
flowdeck dev object export                     # Export current connections, variables, pools to airflow_settings.yaml
```

---

### 7.10 `flowdeck deploy`

Build and deploy a DAG bundle image to a FlowDeck Deployment. Automatically runs `flowdeck dev parse` before deploying to prevent broken DAGs from reaching production.

```bash
flowdeck deploy                                # Deploy to deployment in .flowdeck/config.yaml
flowdeck deploy <deployment-id>                # Deploy to a specific Deployment
flowdeck deploy --dags-only                    # Deploy only DAG files (no image rebuild)
flowdeck deploy --image-name <image>           # Deploy a specific pre-built image
flowdeck deploy --wait                         # Block until deployment is healthy
flowdeck deploy --wait-time <seconds>          # Timeout for --wait (default: 300s)
flowdeck deploy --skip-parse                   # Skip DAG parse validation before deploy
flowdeck deploy --force                        # Deploy even if no changes detected
flowdeck deploy --description "<text>"         # Tag this deploy with a description
flowdeck deploy --save                         # Save deployment config after deploy
flowdeck deploy --mount-dags                   # Deploy using NFS-mounted DAGs (no image rebuild)
flowdeck deploy --type <type>                  # Deploy type: image | dags | image-and-dags (default: image)
```

---

### 7.11 `flowdeck run`

Trigger a DAG run from the CLI without opening the Airflow UI. Useful for one-off or CI-triggered runs.

```bash
flowdeck run <dag-id>                          # Trigger a DAG run in the active deployment
flowdeck run <dag-id> --deployment-id <id>     # Target a specific deployment
flowdeck run <dag-id> --conf '{"key":"val"}'   # Pass a run configuration JSON
```

---

### 7.12 `flowdeck deployment`

Manage Airflow Deployments on FlowDeck. Subcommands map directly to deployment lifecycle operations.

#### Core Lifecycle

```bash
flowdeck deployment list                                    # List all Deployments in workspace
flowdeck deployment list --workspace-id <id>               # List in a specific workspace
flowdeck deployment list --output json                     # JSON output for scripting

flowdeck deployment create                                 # Interactive creation wizard
flowdeck deployment create \
  --name <name> \
  --workspace-id <id> \
  --cluster-id <id> \
  --airflow-version <version> \
  --executor <CeleryExecutor|KubernetesExecutor|LocalExecutor> \
  --cloud-provider <aws|gcp|azure> \
  --region <region> \
  --dag-deploy-enabled \
  --high-availability \
  --development-mode \
  --description "<text>" \
  --wait                                                   # Block until healthy

flowdeck deployment delete <deployment-id>                  # Delete a Deployment
flowdeck deployment delete <deployment-id> --force          # Skip confirmation prompt

flowdeck deployment update <deployment-id>                  # Interactive update wizard
flowdeck deployment update <deployment-id> \
  --name <name> \
  --description "<text>" \
  --airflow-version <version> \
  --executor <executor> \
  --high-availability \
  --development-mode \
  --workload-identity <identity>                           # AWS/GCP workload identity

flowdeck deployment inspect <deployment-id>                 # Full deployment detail
flowdeck deployment inspect <deployment-id> --key <field>   # Inspect a specific field
flowdeck deployment inspect <deployment-id> \
  --show-workload-identity                                 # Include workload identity in output
flowdeck deployment inspect <deployment-id> --output yaml   # YAML format (great for GitOps)
```

#### Logs

```bash
flowdeck deployment logs <deployment-id>                    # Stream deployment logs
flowdeck deployment logs <deployment-id> --scheduler        # Scheduler logs only
flowdeck deployment logs <deployment-id> --webserver        # Webserver/API server logs
flowdeck deployment logs <deployment-id> --triggerer        # Triggerer logs
flowdeck deployment logs <deployment-id> --follow           # Tail continuously
```

#### Hibernate & Wake-up (Development Deployments)

```bash
flowdeck deployment hibernate <deployment-id>               # Hibernate immediately
flowdeck deployment hibernate <deployment-id> \
  --override-until <datetime>                              # Hibernate until a specific date/time

flowdeck deployment wake-up <deployment-id>                 # Wake a hibernated deployment
flowdeck deployment wake-up <deployment-id> \
  --override-until <datetime>                              # Wake until specific time, then re-hibernate
```

#### Airflow Version Upgrade (Private Cloud / Agent-Based)

```bash
flowdeck deployment airflow upgrade                         # Initialize Airflow upgrade (interactive)
flowdeck deployment airflow upgrade \
  --deployment-id <id> \
  --desired-airflow-version <version>                      # Non-interactive upgrade init
```

#### Runtime Upgrade & Migration (Private Cloud / Agent-Based)

```bash
flowdeck deployment runtime upgrade \
  --deployment-id <id> \
  --desired-runtime-version <version>                      # Initialize Runtime upgrade

flowdeck deployment runtime migrate \
  --deployment-id <id>                                     # Migrate from Certified to Astro Runtime
```

#### Service Accounts (CI/CD)

```bash
flowdeck deployment service-account create \
  --deployment-id <id> \
  --label "<label>" \
  --role <DEPLOYMENT_VIEWER|DEPLOYMENT_EDITOR|DEPLOYMENT_ADMIN>

flowdeck deployment service-account list --deployment-id <id>
flowdeck deployment service-account delete --deployment-id <id> --service-account-id <id>
```

#### Tokens

```bash
flowdeck deployment token list --deployment-id <id>
flowdeck deployment token create \
  --deployment-id <id> \
  --name "<name>" \
  --role <role> \
  --expiration <days>
flowdeck deployment token update --deployment-id <id> --token-id <id> --name "<name>"
flowdeck deployment token delete --deployment-id <id> --token-id <id>
flowdeck deployment token rotate --deployment-id <id> --token-id <id>
```

#### Users

```bash
flowdeck deployment user list --deployment-id <id>
flowdeck deployment user list --deployment-id <id> --email <email>

flowdeck deployment user add \
  --deployment-id <id> \
  --email <email> \
  --role <DEPLOYMENT_VIEWER|DEPLOYMENT_EDITOR|DEPLOYMENT_ADMIN>

flowdeck deployment user remove --deployment-id <id> --email <email>

flowdeck deployment user update \
  --deployment-id <id> \
  --email <email> \
  --role <role>
```

#### Teams

```bash
flowdeck deployment team list --deployment-id <id>

flowdeck deployment team add \
  --deployment-id <id> \
  --team-id <id> \
  --role <role>

flowdeck deployment team remove --deployment-id <id> --team-id <id>

flowdeck deployment team update \
  --deployment-id <id> \
  --team-id <id> \
  --role <role>
```

---

### 7.13 `flowdeck deployment airflow-variable`

Manage Airflow Variables directly from the CLI, stored in the Deployment's metadata database.

```bash
flowdeck deployment airflow-variable list \
  --deployment-id <id>

flowdeck deployment airflow-variable create \
  --deployment-id <id> \
  --variable-key <key> \
  --variable-value <value> \
  --description "<text>"

flowdeck deployment airflow-variable update \
  --deployment-id <id> \
  --variable-key <key> \
  --variable-value <value>

flowdeck deployment airflow-variable copy \
  --source-deployment-id <id> \
  --target-deployment-id <id>   # Copy all variables from one deployment to another
```

---

### 7.14 `flowdeck deployment connection`

Manage Airflow Connections on a Deployment.

```bash
flowdeck deployment connection list \
  --deployment-id <id>
flowdeck deployment connection list \
  --deployment-id <id> \
  --output json                           # JSON format for export/import

flowdeck deployment connection create \
  --deployment-id <id> \
  --conn-id <connection-id> \
  --conn-type <type> \
  --host <host> \
  --port <port> \
  --login <user> \
  --password <password> \
  --schema <schema> \
  --extra '<json>'

flowdeck deployment connection update \
  --deployment-id <id> \
  --conn-id <connection-id> \
  [same flags as create]

flowdeck deployment connection copy \
  --source-deployment-id <id> \
  --target-deployment-id <id>             # Copy all connections between deployments
```

---

### 7.15 `flowdeck deployment pool`

Manage Airflow Pools on a Deployment.

```bash
flowdeck deployment pool list \
  --deployment-id <id>

flowdeck deployment pool create \
  --deployment-id <id> \
  --name <pool-name> \
  --slots <integer> \
  --description "<text>"

flowdeck deployment pool update \
  --deployment-id <id> \
  --name <pool-name> \
  --slots <integer> \
  --description "<text>"

flowdeck deployment pool copy \
  --source-deployment-id <id> \
  --target-deployment-id <id>
```

---

### 7.16 `flowdeck deployment variable`

Manage Deployment-level **environment variables** (distinct from Airflow Variables).

```bash
flowdeck deployment variable list \
  --deployment-id <id>
flowdeck deployment variable list \
  --deployment-id <id> \
  --output table

flowdeck deployment variable create \
  --deployment-id <id> \
  --key <KEY> \
  --value <value> \
  --secret                               # Mark value as secret (masked in UI)

flowdeck deployment variable update \
  --deployment-id <id> \
  --key <KEY> \
  --value <value>
```

---

### 7.17 `flowdeck deployment worker-queue`

Manage worker queues for Celery or Kubernetes executor deployments.

```bash
flowdeck deployment worker-queue list \
  --deployment-id <id>

flowdeck deployment worker-queue create \
  --deployment-id <id> \
  --name <queue-name> \
  --worker-concurrency <int> \
  --min-worker-count <int> \
  --max-worker-count <int> \
  --worker-cpu <millicores> \
  --worker-memory <GB> \
  --node-pool-id <id>                    # Pin queue to specific node pool

flowdeck deployment worker-queue update \
  --deployment-id <id> \
  --name <queue-name> \
  [same resource flags]

flowdeck deployment worker-queue delete \
  --deployment-id <id> \
  --name <queue-name>
```

---

### 7.18 `flowdeck workspace`

Manage Workspaces within an Organization.

```bash
flowdeck workspace list                                     # List accessible workspaces
flowdeck workspace list --output json

flowdeck workspace create \
  --name "<name>" \
  --description "<text>" \
  --enforce-cicd                                           # Enforce CI/CD-only deploys

flowdeck workspace update \
  --workspace-id <id> \
  --name "<name>" \
  --description "<text>"

flowdeck workspace delete \
  --workspace-id <id>

flowdeck workspace switch <workspace-id-or-name>            # Switch active workspace
flowdeck workspace switch                                   # Interactive prompt
```

#### Workspace Users

```bash
flowdeck workspace user list
flowdeck workspace user list --email <email>

flowdeck workspace user add \
  --email <email> \
  --role <WORKSPACE_VIEWER|WORKSPACE_EDITOR|WORKSPACE_OPERATOR|WORKSPACE_OWNER>

flowdeck workspace user remove --email <email>

flowdeck workspace user update --email <email> --role <role>
```

#### Workspace Teams

```bash
flowdeck workspace team list
flowdeck workspace team add --team-id <id> --role <role>
flowdeck workspace team remove --team-id <id>
flowdeck workspace team update --team-id <id> --role <role>
```

#### Workspace Tokens

```bash
flowdeck workspace token list
flowdeck workspace token create --name "<name>" --role <role> --expiration <days>
flowdeck workspace token update --token-id <id> --name "<name>"
flowdeck workspace token delete --token-id <id>
flowdeck workspace token rotate --token-id <id>
```

---

### 7.19 `flowdeck organization`

Manage the top-level Organization (Org Owners only).

```bash
flowdeck organization list                                  # List orgs you belong to

flowdeck organization switch <org-id-or-name>              # Switch active org
flowdeck organization switch --workspace-id <id>           # Switch org and pre-select workspace
```

#### Organization Users

```bash
flowdeck organization user list
flowdeck organization user list --role <role>

flowdeck organization user invite \
  --email <email> \
  --role <ORGANIZATION_MEMBER|ORGANIZATION_BILLING_ADMIN|ORGANIZATION_OWNER>

flowdeck organization user remove --email <email>

flowdeck organization user update --email <email> --role <role>
```

#### Organization Teams

```bash
flowdeck organization team list
flowdeck organization team create --name "<name>" --description "<text>"
flowdeck organization team get --team-id <id>
flowdeck organization team update --team-id <id> --name "<name>"
flowdeck organization team delete --team-id <id>

flowdeck organization team user add --team-id <id> --email <email>
flowdeck organization team user remove --team-id <id> --email <email>
flowdeck organization team user list --team-id <id>
```

#### Organization Tokens

```bash
flowdeck organization token list
flowdeck organization token create --name "<name>" --role <role> --expiration <days>
flowdeck organization token update --token-id <id> --name "<name>"
flowdeck organization token delete --token-id <id>
flowdeck organization token rotate --token-id <id>
```

---

### 7.20 `flowdeck team`

Shorthand workspace-level team management (works within the currently active workspace context).

```bash
flowdeck team list
flowdeck team get --team-id <id>
```

---

### 7.21 `flowdeck user`

```bash
flowdeck user create \
  --email <email> \
  --role <role>                          # Invite a new user to the active workspace/org
```

---

### 7.22 `flowdeck cluster`

Manage agent-based clusters (FlowDeck-specific, for Mode 2 deployments).

```bash
flowdeck cluster list                                       # List all registered clusters
flowdeck cluster create                                     # Generate install token + print helm command
flowdeck cluster create --name "<name>"                    # Non-interactive cluster registration
flowdeck cluster inspect <cluster-id>                      # Full cluster detail
flowdeck cluster delete <cluster-id>                       # Deregister a cluster
flowdeck cluster token rotate <cluster-id>                 # Rotate agent authentication token
flowdeck cluster health <cluster-id>                       # Show live health status
```

---

### 7.23 `flowdeck remote`

Manage Remote Execution Agents *(available when Remote Execution mode is enabled on a Deployment)*.

```bash
flowdeck remote list --deployment-id <id>                  # List all registered remote agents
flowdeck remote inspect <agent-id>                         # Inspect a specific agent
flowdeck remote delete <agent-id>                          # Deregister an agent
flowdeck remote cordon <agent-id>                          # Drain agent — stop new task assignment
flowdeck remote uncordon <agent-id>                        # Re-enable agent for task assignment
flowdeck remote token create --deployment-id <id>          # Create an agent authentication token
flowdeck remote token list --deployment-id <id>            # List all agent tokens
flowdeck remote token delete --token-id <id>               # Delete an agent token
```

---

### 7.24 `flowdeck dbt`

Integrate dbt project deployments with FlowDeck *(requires Cosmos or dbt-on-FlowDeck add-on)*.

```bash
flowdeck dbt deploy \
  --deployment-id <id> \
  --dbt-project-path <path> \
  --wait                                 # Block until dbt deploy is complete
flowdeck dbt deploy \
  --deployment-id <id> \
  --wait-time <seconds>
```

---

### 7.25 `flowdeck api`

Make raw HTTP requests to the FlowDeck API — useful for automation and testing endpoints not yet surfaced as CLI commands.

```bash
flowdeck api <method> <endpoint>         # e.g. flowdeck api GET /v1/deployments
flowdeck api POST /v1/deployments --body '{"name":"prod"}'
```

---

### 7.26 `flowdeck ide`

Integrate with local IDE tooling (language server, DAG lint, type stubs).

```bash
flowdeck ide init                        # Install IDE integration (language server config)
flowdeck ide start                       # Start the FlowDeck language server
```

---

### 7.27 `flowdeck telemetry`

Control whether anonymous usage telemetry is sent to FlowDeck.

```bash
flowdeck telemetry enable                # Enable anonymous telemetry (default)
flowdeck telemetry disable               # Opt out of telemetry
```

---

### 7.28 CLI Configuration File (Project-Level)

```yaml
# .flowdeck/config.yaml
version: 1
project:
  name: my-data-platform

airflow:
  version: "3.0.2"

deployments:
  prod:
    id: dep-xxxxxxxx
    cluster: cluster-xxxxxxxx         # omit for Public Cloud deployments
  staging:
    id: dep-yyyyyyyy
```

---

### 7.29 CI/CD Integration

```yaml
# GitHub Actions example
- name: Deploy to FlowDeck
  uses: flowdeck/deploy-action@v1
  with:
    token: ${{ secrets.FLOWDECK_API_TOKEN }}
    deployment-id: ${{ vars.DEPLOYMENT_ID }}
    wait: true
    skip-parse: false

# GitLab CI example
deploy-airflow:
  image: flowdeck/cli:latest
  script:
    - flowdeck deploy $DEPLOYMENT_ID --wait --token $FLOWDECK_TOKEN
```

---

## 8. Control Plane Deep Dive

### 8.1 FlowDeck API Server

Stateless Go service fronted by a load balancer and backed by platform Postgres. Exposes:
- **REST API (v1)** — for UI, CLI, customer integrations
- **gRPC API** — for agent-to-control-plane communication (lower latency, streaming)
- **Webhook endpoints** — for Git provider webhooks (push → auto-deploy DAGs)

**Key Responsibilities:** Authentication (JWT validation, SSO), RBAC enforcement, Cluster/Deployment CRUD, desired state storage and versioning, event publication to NATS, aggregated metrics and health status, and audit logging.

### 8.2 NATS JetStream — Event Bus

Durable, ordered event streaming between the API Server and all Commanders. Key streams:

| Stream | Publisher | Consumer | Purpose |
|---|---|---|---|
| `deployments.desired` | API Server | Commander | Create/update/delete events |
| `deployments.status` | Commander | API Server | Actual state reports |
| `agents.heartbeat` | Agent | API Server | Health signals |
| `alerts.platform` | Prometheus | API Server | Alert forwarding |

Deployed as a 3-node cluster for HA. JetStream persistence ensures events survive temporary disconnects.

### 8.3 Platform Postgres

PostgreSQL 15 (or managed: RDS, Cloud SQL, Azure DB). Schema migrations via `golang-migrate`. API Server runs migrations on startup with a distributed lock. Key tables: `organizations`, `workspaces`, `clusters`, `deployments`, `deployment_versions`, `users`, `tokens`, `audit_logs`.

---

## 9. Data Plane Deep Dive

### 9.1 Airflow Runtime Helm Chart

Every Airflow deployment is installed from the `flowdeck-airflow` Helm chart — a wrapper around the official Apache Airflow Helm chart with FlowDeck-specific additions:
- Pre-configured observability sidecar (Vector for logs, Prometheus annotations)
- FlowDeck-managed secrets injection via init containers
- DAG bundle management (init container pulls DAG image or syncs from Git)
- Readiness probe integration with Commander health reporter

### 9.2 Commander (Reconciler)

Go service implementing a control loop:

```
loop every 10s:
  desired = fetch_desired_state_from_api_server()
  actual  = inspect_helm_releases_in_cluster()
  diff    = compare(desired, actual)

  for each change in diff:
    CREATE → helm_install(chart, values)
    UPDATE → helm_upgrade(chart, values)
    DELETE → helm_uninstall(release)

  report_actual_state_to_api_server(actual)
```

In Public Cloud mode, Commander runs in the Control Plane cluster. In Agent-Based mode, it runs inside the customer's cluster as part of the Agent. The code is identical — only the deployment target differs.

### 9.3 Config Syncer

Mirrors from the FlowDeck API Server into each Airflow namespace:
- `flowdeck-registry-credentials` — image pull secret
- `flowdeck-airflow-connections` — Airflow connection URIs
- `flowdeck-env` — deployment environment variables
- `flowdeck-api-token` — token for Airflow to authenticate with FlowDeck API

---

## 10. Agent Architecture Deep Dive

### 10.1 Token Rotation

Agent tokens are JWTs (24h). The Agent automatically rotates 1h before expiry:
1. Agent sends current token + cluster ID to `POST /v1/clusters/{id}/token/rotate`
2. API Server issues a new token
3. Agent persists new token to a Kubernetes secret in `flowdeck-system` namespace
4. Old token is immediately invalidated

### 10.2 Upgrade Strategy

When the API Server signals a newer agent version is available:
1. Commander pulls the new chart version from the FlowDeck registry
2. Applies a rolling update to the agent Deployment
3. New pod comes up, registers, begins heartbeating
4. Old pod terminates

Customers can pin their agent version or configure auto-upgrade policies (latest minor, never major, etc.).

### 10.3 Agent RBAC

The Helm chart creates a dedicated ServiceAccount with a ClusterRole limited to namespaces labelled `flowdeck.io/managed: "true"`. No access to existing customer namespaces, secrets, or workloads. Agents cannot modify ClusterRoles or cluster-wide resources beyond what the chart explicitly grants.

---

## 11. API Design

### 11.1 REST API (v1)

Base URL: `https://api.flowdeck.io/v1`

```
/organizations
  GET    /                         List orgs
  POST   /                         Create org

/workspaces
  GET    /                         List workspaces
  POST   /                         Create workspace
  GET    /{workspaceId}
  PUT    /{workspaceId}
  DELETE /{workspaceId}

/clusters
  GET    /                         List clusters (agent-based)
  POST   /                         Register cluster
  GET    /{clusterId}
  DELETE /{clusterId}
  GET    /{clusterId}/health
  POST   /{clusterId}/token/rotate

/deployments
  GET    /                         List deployments
  POST   /                         Create deployment
  GET    /{deploymentId}
  PUT    /{deploymentId}
  DELETE /{deploymentId}
  GET    /{deploymentId}/status    Live pod states
  GET    /{deploymentId}/logs      Streamed logs
  GET    /{deploymentId}/metrics   Aggregated metrics
  POST   /{deploymentId}/hibernate
  POST   /{deploymentId}/wake-up

/deployments/{id}/airflow-variables
  GET    /                         List variables
  POST   /                         Create variable
  PUT    /{key}                    Update variable
  POST   /copy                     Copy from another deployment

/deployments/{id}/connections
  GET    /
  POST   /
  PUT    /{connId}
  POST   /copy

/deployments/{id}/pools
  GET    /
  POST   /
  PUT    /{poolName}
  POST   /copy

/deployments/{id}/environment-variables
  GET    /
  POST   /
  PUT    /{key}

/deployments/{id}/worker-queues
  GET    /
  POST   /
  PUT    /{queueName}
  DELETE /{queueName}

/deployments/{id}/users
  GET    /
  POST   /
  PUT    /{userId}
  DELETE /{userId}

/deployments/{id}/teams
  GET    /
  POST   /
  PUT    /{teamId}
  DELETE /{teamId}

/deployments/{id}/tokens
  GET    /
  POST   /
  PUT    /{tokenId}
  DELETE /{tokenId}
  POST   /{tokenId}/rotate

/deploy
  POST   /{deploymentId}           Trigger a DAG bundle deploy
  GET    /{deploymentId}/history   Deploy history
```

### 11.2 gRPC API (Agent Protocol)

```protobuf
service AgentService {
  rpc Heartbeat(HeartbeatRequest) returns (HeartbeatResponse);
  rpc GetDesiredState(GetDesiredStateRequest) returns (DesiredState);
  rpc ReportActualState(ActualState) returns (ReportResponse);
  rpc RotateToken(RotateTokenRequest) returns (RotateTokenResponse);
  rpc StreamEvents(StreamEventsRequest) returns (stream Event);
}
```

### 11.3 Versioning

APIs versioned by URL path (`/v1`, `/v2`). No breaking changes within a version. API Server supports N and N-1 simultaneously. CLI and Agent declare their supported API version range.

---

## 12. Authentication & Authorization

### 12.1 User Authentication

- **Username/Password** — SCRAM-SHA-256 hashed (basic tier)
- **SSO / OIDC** — Okta, Microsoft Entra ID, Google Workspace, any OIDC-compliant IdP
- **SAML 2.0** — Enterprise tier
- **API Tokens** — Scoped, manually rotated or short-lived auto-rotated for CI/CD

### 12.2 RBAC Model

```
Organization
  └── Workspace
        └── Deployment

Organization roles:  ORGANIZATION_MEMBER | ORGANIZATION_BILLING_ADMIN | ORGANIZATION_OWNER
Workspace roles:     WORKSPACE_VIEWER | WORKSPACE_EDITOR | WORKSPACE_OPERATOR | WORKSPACE_OWNER
Deployment roles:    DEPLOYMENT_VIEWER | DEPLOYMENT_EDITOR | DEPLOYMENT_ADMIN
CI/CD special:       DEPLOYER (trigger deploys and DAG runs only)
```

Roles are additive and inherited downward.

### 12.3 Agent Authentication

Cluster-scoped tokens grant only:
- Reading desired state for deployments assigned to this cluster
- Writing actual state and heartbeats for this cluster
- No access to billing, user management, or other clusters

---

## 13. Observability Stack

### 13.1 Metrics

Each Airflow deployment exposes Prometheus metrics. In Public Cloud mode a shared Prometheus federation server aggregates all deployment Prometheus instances. In Agent-Based mode, a per-cluster Prometheus remote-writes a curated set of metrics to the FlowDeck Control Plane.

**Key metrics in FlowDeck UI per Deployment:**
- Task success/failure rate (24h, 7d)
- Scheduler heartbeat lag
- Task queue depth per queue
- Worker CPU/memory utilisation
- DAG parsing time

### 13.2 Logs

Vector is deployed as a DaemonSet in each data plane cluster. It tails Airflow Scheduler logs, Worker task logs, and Agent logs — shipping to Loki or a customer-specified backend (S3, Datadog, Splunk).

### 13.3 Alerting

Alertmanager on the Control Plane handles all alerts:

| Alert | Trigger | Severity |
|---|---|---|
| Agent heartbeat lost | No heartbeat > 60s | Critical |
| Scheduler not heartbeating | Scheduler dead | Critical |
| Task failure rate > 20% | Last 1h | Warning |
| Worker CPU > 90% | 5 min sustained | Warning |
| Deployment failed to reconcile | 3 consecutive errors | Critical |

Notifications routed to Email, Slack, PagerDuty, or any webhook.

---

## 14. Multi-Tenancy Model

Each Organization is a top-level tenant. Isolation guarantees:
- **Network:** NetworkPolicies prevent cross-deployment traffic
- **Compute:** ResourceQuotas enforce CPU/memory ceilings per deployment
- **Storage:** Each deployment has its own Postgres schema or instance (tiered)
- **Identity:** RBAC ensures users can only see resources in their Organization
- **Secrets:** Secrets are namespaced and never shared across Organizations

In Public Cloud mode, KEDA-managed autoscaling prevents noisy-neighbor starvation. Node taints and toleration profiles allow premium-tier customers to request dedicated node pools.

---

## 15. Extension Path — Remote Execution Mode

> **Target:** 6–12 months post launch.

### 15.1 What Changes

The Airflow **Scheduler and Metadata DB stay on the FlowDeck Control Plane**, but **task execution happens in the customer's environment** via a lightweight Worker Agent. No Airflow Scheduler or Metadata DB is installed in the customer's cluster. Only viable with **Airflow 3** (Task Execution API — workers use HTTP not direct DB connections).

### 15.2 New Components Required

**Worker Agent (customer's cluster):** Stripped Helm chart containing only Airflow Worker, Triggerer, and DAG Processor Agent. No Commander or Helm management. Heartbeats to the FlowDeck API Server advertising available task slots. Executes tasks locally using customer's secrets, IAM, and network. Reports task status (not data) back outbound.

**DAG Processor Agent (customer's cluster):** Reads DAG Python files from customer's source. Serializes DAG graph structure only, uploads serialized form to the API Server. Raw DAG code never leaves the customer's environment.

### 15.3 Architecture Additions

```
FlowDeck API Server: new endpoints
  POST /v1/remote-agents/register
  GET  /v1/remote-agents/{id}/tasks           ← worker polls this
  POST /v1/remote-agents/{id}/task-results    ← worker pushes status

Task Execution API proxy:
  Worker Agent → HTTPS outbound → FlowDeck API Server → Airflow Metadata DB
```

### 15.4 Network Options

| Option | Description |
|---|---|
| Default | Outbound HTTPS to `api.flowdeck.io:443` (public internet, token-authenticated) |
| IP Allowlisting | Restrict API server to accept only specific customer source IPs |
| AWS PrivateLink | FlowDeck exposes VPC Endpoint Service; customer creates VPC Endpoint; traffic stays on AWS backbone |
| GCP Private Service Connect | Equivalent for GCP customers |
| Azure Private Link | Equivalent for Azure customers |

### 15.5 Security Boundary

What FlowDeck sees: Task state, duration, DAG graph structure, log links.  
What FlowDeck never sees: Raw task data, customer secrets, DAG Python code, XCom payloads (routed to customer's S3/backend).

### 15.6 Implementation Phases

| Phase | Scope |
|---|---|
| Phase R1 | Airflow 3 Task Execution API proxy in FlowDeck API Server |
| Phase R2 | Worker Agent Helm chart (stripped, no Commander) |
| Phase R3 | DAG Processor Agent + DAG serialization pipeline |
| Phase R4 | PrivateLink (AWS), Private Service Connect (GCP), Private Link (Azure) |
| Phase R5 | Multi-region worker agents — one deployment, workers in N regions |

---

## 16. Extension Path — Private Cloud Mode

> **Target:** 12–18 months post launch.

### 16.1 What Changes

Everything runs in the customer's environment. FlowDeck provides the software; the customer runs the infrastructure. No outbound connection to FlowDeck SaaS required after initial installation (air-gap capable).

### 16.2 New Components Required

**FlowDeck Private Cloud Installer (`flowdeck-installer`):** A bootstrapper CLI that provisions the full Control Plane (API Server, UI, NATS, platform Postgres, Prometheus, Grafana, Loki) into the customer's Kubernetes cluster via Helm. Supports disconnected/air-gap mode with all images pre-pulled to a private registry.

**License Service:** Offline license validation (signed JWT with embedded expiry + feature flags). No phone-home required. Annual renewal via new license file, not a network call.

### 16.3 Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                  CUSTOMER'S ENVIRONMENT                         │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              FlowDeck Control Plane                      │   │
│  │  (same code as SaaS — different license mode)            │   │
│  │  API Server · UI · NATS · Platform Postgres              │   │
│  │  Prometheus · Grafana · Loki · Alertmanager              │   │
│  └─────────────────────────┬────────────────────────────────┘   │
│                            │ internal k8s networking            │
│  ┌─────────────────────────▼────────────────────────────────┐   │
│  │                    Data Plane                            │   │
│  │  Commander · Airflow Deployments · Airflow Postgres      │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 16.4 Feature Parity

| Feature | SaaS | Private Cloud |
|---|---|---|
| UI & API | Full parity | Full parity |
| SSO | Orion-hosted IdP integration | Customer's IdP (same OIDC code) |
| Metrics | FlowDeck-hosted Grafana | Customer-hosted Grafana (same dashboards) |
| Upgrades | Automatic | Manual via `flowdeck-installer upgrade` |
| Billing | Usage-based | License-based (annual) |
| Support | SLA via FlowDeck cloud | SLA via support portal, no telemetry |
| Air-gap | No | Yes |

### 16.5 Air-Gap Support

The installer supports `--air-gap` flag that generates a manifest of all required container images. Customer pulls and mirrors images to their private registry. Installer reads `imageRegistry: registry.customer.internal` from config and patches all Helm values accordingly.

---

## 17. Extension Path — Hybrid Multi-Region

> **Target:** 18+ months.

A single FlowDeck Organization with deployments spread across multiple regions and clouds — all visible in one UI, with cross-region failover and geo-pinned execution.

**Key additions needed:**
- Control Plane federation (multi-region active-active or active-passive)
- Cross-region NATS cluster
- Deployment geo-affinity policies (tasks must run in EU, data must not cross borders)
- Global DAG catalog with per-region deployment targeting

---

## 18. Data Model

### 18.1 Core Entities (Platform Postgres)

```sql
CREATE TABLE organizations (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  slug        TEXT UNIQUE NOT NULL,
  tier        TEXT NOT NULL DEFAULT 'starter',  -- starter | pro | enterprise
  created_at  TIMESTAMPTZ DEFAULT now(),
  updated_at  TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE workspaces (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name            TEXT NOT NULL,
  description     TEXT,
  ci_cd_enforced  BOOLEAN DEFAULT FALSE,
  created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE clusters (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id),
  name            TEXT NOT NULL,
  mode            TEXT NOT NULL,             -- 'flowdeck_cloud' | 'agent_based'
  status          TEXT NOT NULL DEFAULT 'pending',
  last_heartbeat  TIMESTAMPTZ,
  k8s_version     TEXT,
  agent_version   TEXT,
  cloud_provider  TEXT,                      -- 'aws' | 'gcp' | 'azure' | 'on_prem'
  region          TEXT,
  created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE deployments (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  workspace_id     UUID NOT NULL REFERENCES workspaces(id),
  cluster_id       UUID REFERENCES clusters(id),  -- null = flowdeck cloud
  name             TEXT NOT NULL,
  description      TEXT,
  airflow_version  TEXT NOT NULL,
  executor         TEXT NOT NULL DEFAULT 'CeleryExecutor',
  resource_profile TEXT NOT NULL DEFAULT 'medium',
  dag_bundle_url   TEXT,
  status           TEXT NOT NULL DEFAULT 'creating',
  high_availability BOOLEAN DEFAULT FALSE,
  development_mode  BOOLEAN DEFAULT FALSE,
  desired_state    JSONB NOT NULL DEFAULT '{}',
  actual_state     JSONB,
  created_at       TIMESTAMPTZ DEFAULT now(),
  updated_at       TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE deployment_versions (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  deployment_id UUID NOT NULL REFERENCES deployments(id),
  version       INT NOT NULL,
  desired_state JSONB NOT NULL,
  description   TEXT,
  created_by    UUID,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE deployment_tokens (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  deployment_id UUID NOT NULL REFERENCES deployments(id),
  name          TEXT NOT NULL,
  token_hash    TEXT NOT NULL,
  role          TEXT NOT NULL,
  expires_at    TIMESTAMPTZ,
  created_at    TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE audit_logs (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL,
  actor_id        UUID,
  actor_type      TEXT,                     -- 'user' | 'agent' | 'system'
  action          TEXT NOT NULL,
  resource_type   TEXT,
  resource_id     UUID,
  metadata        JSONB,
  created_at      TIMESTAMPTZ DEFAULT now()
);
```

---

## 19. Technology Stack Choices

| Layer | Technology | Rationale |
|---|---|---|
| API Server | Go + Chi router | Fast, small binary, excellent k8s client |
| gRPC (agent protocol) | protobuf + grpc-go | Efficient binary protocol, streaming, strong typing |
| Event Bus | NATS JetStream | Lightweight, durable streams, embedded mode for dev |
| Platform DB | PostgreSQL 15 | Proven, JSONB for flexible desired state |
| UI | Next.js + React + Tailwind | SSR for fast load, TypeScript end-to-end |
| CLI | Go (cobra + viper) | Single binary, no runtime dependency |
| Agent | Go | Same language as API Server, small footprint |
| Airflow Packaging | Helm + Docker | Industry standard |
| Metrics | Prometheus + Grafana | Industry standard |
| Logs | Loki + Vector | Lightweight, Grafana native |
| Container runtime (local dev) | Docker or Podman | Auto-detected; Podman default on macOS |

---

## 20. Security Architecture

### 20.1 Defense in Depth

```
Layer 1: Network
  - TLS 1.3 everywhere (no 1.2 fallback in production)
  - mTLS between internal control plane services
  - Agent connections: outbound HTTPS only; optional certificate pinning

Layer 2: Identity
  - Short-lived JWTs (1h access, 7d refresh)
  - Agent tokens: 24h, auto-rotated
  - API tokens: manually rotated, scoped to minimum permissions

Layer 3: Authorization
  - RBAC enforced on every API handler via middleware
  - Organization-scoped resource isolation in all DB queries

Layer 4: Data
  - Secrets encrypted at rest (AES-256) in platform Postgres
  - Secrets never returned in API responses (write-only)
  - Full audit log for all mutation operations

Layer 5: Runtime
  - Kubernetes pod security standards (restricted profile)
  - Non-root containers, read-only root filesystem where possible
  - NetworkPolicies blocking cross-namespace traffic
```

### 20.2 Compliance Readiness

| Standard | Mode | Notes |
|---|---|---|
| SOC 2 Type II | Public Cloud | Audit log, encryption, access control — ready to audit |
| HIPAA | Agent-Based | Data stays in customer env; BAA available |
| GDPR | All | Data residency via cluster selection; no PII in Control Plane |
| PCI-DSS | Agent-Based + PrivateLink | For CDE workloads |
| FedRAMP Moderate | Private Cloud *(future)* | Air-gap support targets FedRAMP |

---

## 21. Rollout & Migration Strategy

### 21.1 Phase 1 — Foundation (Months 1–3)

- Control Plane: FlowDeck API Server (REST), Platform Postgres, NATS, basic UI
- Public Cloud mode: Commander, Config Syncer, Airflow Helm chart, first working deployment
- CLI: `login`, `logout`, `version`, `config`, `context`, `deployment create/list/delete/inspect/logs`, `deploy`, `dev start/stop/restart/init/parse`
- Observability: Prometheus + Grafana + Loki basics

### 21.2 Phase 2 — Agent-Based (Months 4–6)

- Agent binary: Commander + Config Syncer + Heartbeat Reporter + Sentinel
- Agent Helm chart, token generation, installation flow in UI
- `flowdeck cluster` CLI commands
- Agent token rotation and self-upgrade mechanism
- gRPC agent protocol for push-mode events

### 21.3 Phase 3 — Full CLI Parity (Months 7–9)

- `flowdeck deployment airflow-variable/connection/pool/variable/worker-queue` commands
- `flowdeck workspace/organization/team/user` commands
- `flowdeck deployment user/team/token` commands
- `flowdeck dbt deploy` integration
- `flowdeck dev pytest/upgrade-test/bash/object import/export`
- `flowdeck deployment hibernate/wake-up`
- GitHub Actions integration and GitLab CI examples
- `flowdeck api` raw API command

### 21.4 Phase 4 — Remote Execution (Months 10–16)

- Airflow 3 Task Execution API proxy in FlowDeck API Server
- Worker Agent Helm chart (stripped, no Commander)
- DAG Processor Agent + DAG serialization pipeline
- `flowdeck remote` CLI commands
- AWS PrivateLink, GCP Private Service Connect, Azure Private Link

### 21.5 Phase 5 — Private Cloud (Months 17–24)

- `flowdeck-installer` bootstrap CLI
- Offline license service
- Air-gap image bundle generator
- On-premises documentation and support runbooks
- `flowdeck deployment runtime upgrade/migrate` commands (Private Cloud)

---

## 22. Open Questions & Decisions

| # | Question | Options | Recommendation |
|---|---|---|---|
| 1 | Airflow 2 support? | Both / Airflow 3 only | Support 2.x read-only; push customers to 3 for agent exec |
| 2 | Agent protocol: gRPC vs REST polling? | gRPC streaming / REST polling | Start with REST polling (simpler); migrate to gRPC in Phase 2 |
| 3 | Postgres: per-deployment or shared? | Shared schema / separate DB | Shared Postgres, per-deployment schema; separate DB for enterprise |
| 4 | Log storage: self-hosted vs managed? | Self-hosted Loki / Grafana Cloud / Elastic | Self-hosted Loki; Grafana Cloud as premium upsell |
| 5 | Registry: self-hosted vs cloud? | ECR/GCR/ACR / Harbor | Cloud-managed (ECR) to start; Harbor for Private Cloud |
| 6 | CLI distribution: binary vs npm/pip? | Go binary / npm / pip | Go binary — no runtime dependency, CI/CD friendly |
| 7 | NATS vs Kafka? | NATS JetStream / Kafka | NATS — operationally simpler, adequate throughput |
| 8 | Multi-cloud control plane in Phase 1? | Single cloud / multi-cloud | Single cloud (AWS) first; GCP/Azure in Phase 3 |
| 9 | Local dev: Docker vs Podman default? | Docker / Podman / auto-detect | Auto-detect; default Podman on macOS (no daemon, rootless) |
| 10 | Telemetry opt-in or opt-out? | Opt-in / Opt-out | Opt-out with clear disclosure; disable via `flowdeck telemetry disable` |

---

*Document maintained by the FlowDeck Platform Architecture Team. Last updated: 2026-04-27.*
