# Managed Airflow Platform - Deployment Options

The Managed Airflow Platform supports **four** deployment targets: **Local** (Docker Compose on your machine), **AWS EC2**, **AWS ECS (Fargate)**, and **Kubernetes** (Helm). This guide compares the cloud options; for local development, start with [docs/LOCAL_TESTING.md](docs/LOCAL_TESTING.md).

## Quick Comparison

| Aspect | Local | EC2 + Docker | AWS ECS | Kubernetes |
|--------|-------|-------------|---------|------------|
| **Complexity** | ⭐ Simplest | ⭐ Simple | ⭐⭐ Moderate | ⭐⭐⭐ Complex |
| **Setup Time** | minutes | 10-15 min | 20-30 min | 45-60 min |
| **Cost (per tenant)** | Free | ~$35/month | ~$70-140/month | ~$200/month |
| **High Availability** | ❌ No | ❌ No | ✅ Yes | ✅ Yes |
| **Auto-Scaling** | ❌ No | ❌ Manual | ✅ Yes | ✅ Yes |
| **Best For** | Dev / demos | Dev/Test | Test/Staging | Production |
| **Learning Curve** | Easiest | Easy | Medium | Steep |
| **Maintenance** | You manage Docker | Low | Low | High |
| **Cloud Lock-in** | None | AWS only | AWS only | Multi-cloud |

## Detailed Comparison

### 1. EC2 with Docker

**Architecture**: Single EC2 instance per tenant running Docker Compose

**When to Use:**
- ✅ Development and testing environments
- ✅ POC and evaluation
- ✅ Budget-constrained projects ($35/month)
- ✅ Small teams learning Airflow
- ✅ Non-critical workloads
- ✅ Rapid prototyping

**When NOT to Use:**
- ❌ Production workloads requiring high availability
- ❌ Mission-critical applications
- ❌ Need for automatic failover
- ❌ Large-scale deployments (>10 tenants)

**Pros:**
- 💰 Lowest cost option
- 🚀 Fastest setup time
- 📚 Easy to understand (Docker Compose)
- 🔧 Simple troubleshooting
- 🎯 No orchestration overhead
- 🛠️ Easy to customize

**Cons:**
- 💔 No high availability
- 📉 Manual scaling only
- 🔄 No auto-recovery
- 📦 Limited by single instance resources
- 🗄️ Local storage (data loss risk)

**Monthly Cost Breakdown:**
- EC2 t3.medium: $30
- EBS storage: $2
- Data transfer: $3
- **Total: ~$35/month per tenant**

**Example Use Cases:**
1. **Startup Dev Team**: 3 developers testing DAGs
2. **POC Project**: Evaluating Airflow for 2 weeks
3. **Learning Environment**: Training team on Airflow
4. **Side Project**: Personal data pipeline

**Setup Profile:**
```yaml
spring:
  profiles:
    active: ec2
```

**Documentation**: See [EC2_IMPLEMENTATION.md](./EC2_IMPLEMENTATION.md)

---

### 2. AWS ECS (Elastic Container Service)

**Architecture**: Fargate-based containers with managed PostgreSQL (RDS) and Redis (ElastiCache)

**When to Use:**
- ✅ Test and staging environments
- ✅ Production workloads (small to medium scale)
- ✅ AWS-native deployments
- ✅ Need for auto-scaling without Kubernetes complexity
- ✅ Managed infrastructure preference
- ✅ Medium-scale deployments (10-50 tenants)

**When NOT to Use:**
- ❌ Multi-cloud requirements
- ❌ Already have Kubernetes expertise
- ❌ Need for advanced orchestration features
- ❌ Tightly budget-constrained (<$50/month)

**Pros:**
- 🚀 Serverless containers (Fargate)
- 📈 Automatic scaling
- 🔄 Self-healing infrastructure
- 🛡️ AWS-managed services (RDS, ElastiCache)
- 📊 Built-in monitoring (CloudWatch)
- 💼 Production-ready
- 🔧 Moderate complexity

**Cons:**
- 💰 Higher cost than EC2
- 🔒 AWS vendor lock-in
- 🎛️ Less flexible than Kubernetes
- 📚 Learning curve for ECS concepts
- 🔗 Tied to AWS regions

**Monthly Cost Breakdown:**
- Fargate tasks: $35
- RDS db.t3.micro: $15
- ElastiCache: $12
- Data transfer: $5
- **Total: ~$70/month per tenant**

**Example Use Cases:**
1. **Staging Environment**: Pre-production testing with auto-scaling
2. **Small Production**: 5-10 tenants with moderate load
3. **AWS-Native Shop**: Team familiar with AWS services
4. **Rapid Deployment**: Need production-ready quickly

**Setup Profile:**
```yaml
spring:
  profiles:
    active: ecs
```

**Documentation**: See [ECS_IMPLEMENTATION.md](./ECS_IMPLEMENTATION.md)

---

### 3. Kubernetes

**Architecture**: Helm-based deployments on Kubernetes cluster with KEDA auto-scaling

**When to Use:**
- ✅ Large-scale production deployments
- ✅ Multi-cloud or hybrid cloud
- ✅ Complex orchestration requirements
- ✅ Enterprise-scale (50+ tenants)
- ✅ Existing Kubernetes infrastructure
- ✅ Advanced networking needs
- ✅ GitOps workflows

**When NOT to Use:**
- ❌ Small teams without Kubernetes experience
- ❌ Budget-constrained projects
- ❌ Simple use cases
- ❌ Rapid POC/testing
- ❌ Limited operational resources

**Pros:**
- 🌍 Multi-cloud and portable
- 📈 Advanced auto-scaling (KEDA)
- 🔧 Highly customizable
- 🏢 Enterprise-grade features
- 🔄 Advanced deployment strategies (canary, blue-green)
- 🎯 Fine-grained resource control
- 📊 Rich ecosystem (Prometheus, Grafana, etc.)
- 🛡️ Battle-tested at scale

**Cons:**
- 💰 Highest cost
- 📚 Steep learning curve
- 🔧 High operational overhead
- ⏰ Longer setup time
- 🎛️ Complex troubleshooting
- 👥 Requires dedicated operations team

**Monthly Cost Breakdown:**
- EKS cluster: $72
- Worker nodes (3x t3.medium): $90
- EBS storage: $6
- Load balancer: $18
- Data transfer: $10
- **Total: ~$200/month + per tenant resources**

**Example Use Cases:**
1. **Enterprise Platform**: 100+ tenants across multiple regions
2. **Multi-Cloud**: Deployment across AWS, GCP, and on-premises
3. **Advanced Requirements**: Custom operators, complex networking
4. **Existing K8s**: Team already running Kubernetes

**Setup Profile:**
```yaml
spring:
  profiles:
    active: default  # Kubernetes is default
```

**Documentation**: See main README and Kubernetes documentation

---

## Decision Matrix

### By Use Case

| Use Case | Recommended | Alternative |
|----------|------------|-------------|
| Development/Testing | EC2 + Docker | ECS |
| POC/Evaluation | EC2 + Docker | ECS |
| Staging Environment | ECS | Kubernetes |
| Small Production (<10 tenants) | ECS | Kubernetes |
| Medium Production (10-50 tenants) | ECS | Kubernetes |
| Large Production (50+ tenants) | Kubernetes | ECS |
| Multi-cloud | Kubernetes | - |
| Budget <$50/month | EC2 + Docker | - |
| Budget $50-150/month | ECS | - |
| Budget >$150/month | Kubernetes | - |

### By Team Profile

| Team Profile | Recommended |
|-------------|-------------|
| Small team, limited ops | EC2 + Docker |
| AWS-native, moderate ops | ECS |
| Kubernetes expertise | Kubernetes |
| DevOps team available | Kubernetes or ECS |
| No dedicated ops | EC2 + Docker |

### By Requirements

| Requirement | EC2 | ECS | K8s |
|------------|-----|-----|-----|
| High Availability | ❌ | ✅ | ✅ |
| Auto-Scaling | ❌ | ✅ | ✅ |
| Multi-cloud | ❌ | ❌ | ✅ |
| Cost <$50/month | ✅ | ❌ | ❌ |
| Simple Management | ✅ | ✅ | ❌ |
| Enterprise Features | ❌ | ⚠️ | ✅ |
| Fast Setup | ✅ | ⚠️ | ❌ |

## Feature Matrix

### Deployment Features

| Feature | EC2 | ECS | K8s |
|---------|-----|-----|-----|
| Executor Support | All | All | All |
| Worker Scaling | Manual | Auto | Auto (KEDA) |
| Rolling Updates | Manual | Auto | Auto |
| Health Checks | Basic | Advanced | Advanced |
| Secret Management | Files | Secrets Manager | K8s Secrets |
| Log Aggregation | Local | CloudWatch | Various |
| Metrics | Basic | CloudWatch | Prometheus |
| Backup/Restore | Manual | Manual | Various |

### Task Queue Routing (Flow Deck)

- Queue names in Flow Deck are treated as standard Airflow task queue routing labels.
- Current implementation:
  - Local Docker Compose: queue names map to queue-specific Celery worker services.
  - Trigger flow: optional queue selection is passed in DAG run `conf` under `managed_platform.target_worker_queue`.
- Provider mapping model:
  - ECS/Kubernetes providers can reuse the same queue names and map them to provider-native worker capacity/routing.
  - This keeps queue intent stable across providers even when runtime mechanics differ.

### Operational Features

| Feature | EC2 | ECS | K8s |
|---------|-----|-----|-----|
| Deployment Time | 5-10 min | 10-15 min | 20-30 min |
| Troubleshooting | Easy | Medium | Hard |
| Maintenance | Low | Low | High |
| Monitoring | Basic | Good | Excellent |
| Alerting | Manual | CloudWatch | Rich ecosystem |
| Upgrades | Manual | Managed | Manual |

## Migration Path

### Starting with EC2 → Moving to ECS

**When to migrate:**
- Outgrown single instance limitations
- Need for high availability
- Budget allows for ~2x cost increase
- Team comfortable with AWS

**Migration steps:**
1. Set up ECS infrastructure (Terraform/CloudFormation)
2. Deploy to ECS with `spring.profiles.active=ecs`
3. Test thoroughly in ECS environment
4. Migrate tenants one by one
5. Decommission EC2 instances

### Starting with ECS → Moving to Kubernetes

**When to migrate:**
- Need multi-cloud deployment
- Require advanced orchestration
- Scaling beyond ECS capabilities
- Enterprise requirements (compliance, governance)

**Migration steps:**
1. Set up Kubernetes cluster
2. Install Helm and configure
3. Deploy to K8s with default profile
4. Parallel run (ECS + K8s) for validation
5. Gradual migration of tenants
6. Decommission ECS resources

## Switching Between Providers

The platform makes it easy to switch deployment providers:

```bash
# Run with EC2
mvn spring-boot:run -Dspring-boot.run.profiles=ec2

# Run with ECS
mvn spring-boot:run -Dspring-boot.run.profiles=ecs

# Run with Kubernetes (default)
mvn spring-boot:run
```

## Cost Optimization Tips

### EC2
- Use t3.small for very light workloads
- Implement automated stop/start schedules
- Use Spot Instances for non-prod (60-70% savings)
- Share instances for multiple small deployments

### ECS
- Use Fargate Spot for workers (70% savings)
- Right-size task resources
- Use scheduled scaling (scale down at night)
- Combine with Savings Plans

### Kubernetes
- Use node auto-scaling
- Implement pod disruption budgets
- Use Spot/Preemptible nodes for workers
- Optimize resource requests/limits
- Use cluster auto-scaler

## Getting Started

1. **Choose your deployment option** based on this guide
2. **Set up infrastructure** using Terraform or CloudFormation
3. **Configure the control plane** with appropriate profile
4. **Deploy your first tenant** and test
5. **Monitor and optimize** based on usage

## Support and Documentation

- **EC2 Documentation**: [infrastructure/ec2/README.md](./infrastructure/ec2/README.md)
- **ECS Documentation**: [infrastructure/ecs/README.md](./infrastructure/ecs/README.md)
- **Kubernetes Documentation**: See Helm charts and Kubernetes docs

## Conclusion

Choose your deployment option based on:
- **Budget**: EC2 ($35) < ECS ($70) < Kubernetes ($200+)
- **Complexity**: EC2 (Simple) < ECS (Moderate) < Kubernetes (Complex)
- **Scale**: EC2 (Small) < ECS (Medium) < Kubernetes (Large)
- **Availability**: EC2 (Low) < ECS (High) = Kubernetes (High)

**Quick Recommendations:**
- 🎯 **Just starting?** → EC2 + Docker
- 💼 **Small production?** → AWS ECS
- 🏢 **Enterprise scale?** → Kubernetes

The Managed Airflow Platform's provider abstraction makes it easy to start simple and grow into more sophisticated deployment options as your needs evolve.
