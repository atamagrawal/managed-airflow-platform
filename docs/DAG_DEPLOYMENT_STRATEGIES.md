# DAG Deployment Strategies

## Overview

The Managed Airflow Platform supports two deployment strategies for organizing DAG files: **UNIFIED** and **SEPARATED**. This flexible configuration allows you to choose the approach that best fits your organizational needs.

## Strategy Comparison

### UNIFIED Strategy (Default)

All DAGs, whether standalone or part of a project, are deployed to a single `dags/` directory.

**Directory Structure:**
```
{localBaseDirectory}/{tenantId}/{deploymentId}/
├── dags/
│   ├── standalone_dag1.py
│   ├── standalone_dag2.py
│   ├── project1__dag_a.py     # Optional prefix
│   ├── project1__dag_b.py
│   └── project2__dag_c.py
├── plugins/
├── include/
└── tests/
```

**Pros:**
- ✅ Simpler Airflow configuration (single DAG folder)
- ✅ All DAGs visible in one place
- ✅ No need for multiple DAG folder scanning
- ✅ Faster DAG discovery by Airflow
- ✅ Easier local development and testing

**Cons:**
- ❌ Potential filename conflicts between projects
- ❌ Less organized for large number of projects
- ❌ Harder to track which DAGs belong to which project

**Best For:**
- Development and testing environments
- Small to medium deployments (< 10 projects)
- Teams that prefer simplicity
- Rapid prototyping

### SEPARATED Strategy

Standalone DAGs and project DAGs are kept in separate directories.

**Directory Structure:**
```
{localBaseDirectory}/{tenantId}/{deploymentId}/
├── dags/
│   ├── standalone_dag1.py
│   └── standalone_dag2.py
├── projects/
│   ├── project1/
│   │   ├── dags/
│   │   │   ├── dag_a.py
│   │   │   └── dag_b.py
│   │   ├── plugins/
│   │   ├── include/
│   │   └── tests/
│   └── project2/
│       ├── dags/
│       │   └── dag_c.py
│       ├── plugins/
│       └── include/
```

**Pros:**
- ✅ Clear separation between standalone and project DAGs
- ✅ Better organization for multiple projects
- ✅ No filename conflicts between projects
- ✅ Easy to identify project ownership
- ✅ Easier to manage project-specific configurations

**Cons:**
- ❌ Requires Airflow configuration for multiple DAG folders
- ❌ More complex directory structure
- ❌ Slightly slower DAG discovery
- ❌ More setup for local development

**Best For:**
- Production environments
- Large deployments (> 10 projects)
- Multi-team organizations
- Projects with many DAGs
- When project isolation is important

## Configuration

### Setting the Strategy

Edit your `application.yml` or use environment variables:

```yaml
dag:
  deployment:
    # Choose: UNIFIED or SEPARATED
    strategy: UNIFIED

    # Optional: Prefix project name in UNIFIED mode
    prefix-project-name: false
```

### Environment Variables

```bash
# Set strategy via environment variable
export DAG_DEPLOYMENT_STRATEGY=UNIFIED

# Enable project name prefixing
export DAG_DEPLOYMENT_PREFIX_PROJECT_NAME=true
```

### Profile-Specific Configuration

You can set different strategies for different profiles:

```yaml
---
# Local development (UNIFIED for simplicity)
spring:
  config:
    activate:
      on-profile: local

dag:
  deployment:
    strategy: UNIFIED
    prefix-project-name: false

---
# Production (SEPARATED for organization)
spring:
  config:
    activate:
      on-profile: prod

dag:
  deployment:
    strategy: SEPARATED
    prefix-project-name: false
```

## Project Name Prefixing

When using **UNIFIED** strategy, you can optionally prefix project DAG filenames with the project ID to avoid conflicts.

### Without Prefixing (Default)
```
dags/
├── my_dag.py          # From project1
└── my_dag.py          # From project2 - CONFLICT!
```

### With Prefixing
```
dags/
├── project1__my_dag.py    # From project1
└── project2__my_dag.py    # From project2 - No conflict
```

**Configuration:**
```yaml
dag:
  deployment:
    strategy: UNIFIED
    prefix-project-name: true  # Enable prefixing
```

**Note:** Prefixing only applies to project-based DAGs. Standalone DAGs keep their original names.

## Airflow Configuration

### UNIFIED Strategy

No additional Airflow configuration needed. Use the default `dags_folder`:

```python
# airflow.cfg
[core]
dags_folder = /opt/airflow/dags
```

### SEPARATED Strategy

Configure Airflow to scan multiple DAG directories:

#### Option 1: Multiple DAG Folders (Airflow 2.0+)

```python
# airflow.cfg
[core]
dags_folder = /opt/airflow/dags,/opt/airflow/projects/*/dags
```

#### Option 2: Use `.airflowignore`

Keep a single `dags_folder` but use symlinks or mount multiple directories.

#### Option 3: Docker Compose Configuration

```yaml
services:
  airflow-scheduler:
    volumes:
      - ${LOCAL_BASE_DIRECTORY}/${DEPLOYMENT_ID}/dags:/opt/airflow/dags
      - ${LOCAL_BASE_DIRECTORY}/${DEPLOYMENT_ID}/projects:/opt/airflow/projects
    environment:
      - AIRFLOW__CORE__DAGS_FOLDER=/opt/airflow/dags:/opt/airflow/projects/*/dags
```

## Migration Between Strategies

### From UNIFIED to SEPARATED

1. Change configuration:
   ```yaml
   dag:
     deployment:
       strategy: SEPARATED
   ```

2. Redeploy all projects:
   ```bash
   curl -X POST http://localhost:8080/api/v1/projects/{projectId}/deploy
   ```

3. The platform will automatically create the new directory structure

4. Update Airflow configuration to scan multiple folders

5. Restart Airflow to pick up new configuration

### From SEPARATED to UNIFIED

1. Change configuration:
   ```yaml
   dag:
     deployment:
       strategy: UNIFIED
       prefix-project-name: true  # Recommended to avoid conflicts
   ```

2. Redeploy all projects:
   ```bash
   curl -X POST http://localhost:8080/api/v1/projects/{projectId}/deploy
   ```

3. DAGs will be consolidated into single `dags/` folder

4. Update Airflow configuration to use single `dags_folder`

5. Restart Airflow

## Deployment Workflow

### UNIFIED Strategy Deployment

1. **Create Project**
   ```bash
   POST /api/v1/projects
   ```

2. **Add DAG Files**
   ```bash
   POST /api/v1/projects/{projectId}/files
   ```

3. **Deploy Project**
   ```bash
   POST /api/v1/projects/{projectId}/deploy
   ```

4. **Result:** DAGs written to `{deployment}/dags/[{projectId}__]{fileName}`

5. **Airflow:** Picks up DAGs immediately from single folder

### SEPARATED Strategy Deployment

1. **Create Project**
   ```bash
   POST /api/v1/projects
   ```

2. **Add DAG Files**
   ```bash
   POST /api/v1/projects/{projectId}/files
   ```

3. **Deploy Project**
   ```bash
   POST /api/v1/projects/{projectId}/deploy
   ```

4. **Result:** DAGs written to `{deployment}/projects/{projectId}/dags/{fileName}`

5. **Airflow:** Scans multiple directories for DAGs

## Triggering DAGs

**Both strategies work identically for triggering:**

```bash
# Trigger a DAG (works for both strategies)
POST /api/v1/dags/{dagId}/trigger
```

The trigger functionality uses Airflow's REST API and doesn't depend on file location.

## Best Practices

### 1. Choose Based on Scale

- **< 5 projects**: Use UNIFIED
- **5-10 projects**: Either strategy works
- **> 10 projects**: Use SEPARATED

### 2. Enable Prefixing for UNIFIED

If using UNIFIED with multiple projects:

```yaml
dag:
  deployment:
    strategy: UNIFIED
    prefix-project-name: true
```

### 3. Consistent Naming Conventions

Whether using UNIFIED or SEPARATED, maintain consistent DAG naming:

```python
# Good
dag_id = "data_team_etl_daily"
dag_id = "ml_team_training_weekly"

# Avoid
dag_id = "dag1"
dag_id = "test"
```

### 4. Document Your Strategy

Add a README to your deployment directory:

```markdown
# Deployment: production-etl

**Strategy**: SEPARATED
**Reason**: Multiple teams with many DAGs
**Airflow Config**: Multiple DAG folders enabled
```

### 5. Test Before Production

Always test strategy changes in a development environment:

```bash
# Test with local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Create test deployment
# Add test projects
# Verify DAG discovery
```

## Troubleshooting

### DAGs Not Appearing in Airflow

**UNIFIED Strategy:**
- Check that DAGs are in `{deployment}/dags/` directory
- Verify no Python syntax errors
- Check Airflow logs for parsing errors

**SEPARATED Strategy:**
- Verify Airflow is configured to scan project directories
- Check `dags_folder` configuration in `airflow.cfg`
- Ensure proper directory permissions
- Check that glob pattern works: `/opt/airflow/projects/*/dags`

### Filename Conflicts

**Problem:** Multiple projects have DAGs with same filename

**Solution:**
1. Enable project name prefixing:
   ```yaml
   prefix-project-name: true
   ```

2. Or switch to SEPARATED strategy:
   ```yaml
   strategy: SEPARATED
   ```

### Performance Issues

**SEPARATED Strategy Slow?**
- Reduce number of directories Airflow scans
- Use `.airflowignore` to exclude unnecessary files
- Consider consolidating small projects

**UNIFIED Strategy Slow?**
- Too many DAGs in single directory
- Consider SEPARATED strategy
- Split into multiple deployments

## Examples

### Example 1: Small Team (UNIFIED)

**Use Case:** 3 projects, 15 total DAGs

**Configuration:**
```yaml
dag:
  deployment:
    strategy: UNIFIED
    prefix-project-name: false
```

**Result:**
```
dags/
├── daily_etl.py
├── hourly_sync.py
├── ml_training.py
└── ...
```

### Example 2: Large Organization (SEPARATED)

**Use Case:** 20 projects, 100+ DAGs

**Configuration:**
```yaml
dag:
  deployment:
    strategy: SEPARATED
```

**Result:**
```
dags/
├── shared_utils.py
projects/
├── data-engineering/
│   └── dags/ (25 DAGs)
├── ml-platform/
│   └── dags/ (30 DAGs)
└── analytics/
    └── dags/ (45 DAGs)
```

### Example 3: Hybrid Approach

**Use Case:** Core standalone DAGs + project-based teams

**Configuration:**
```yaml
dag:
  deployment:
    strategy: SEPARATED
```

**Result:**
```
dags/
├── core_monitoring.py
├── health_checks.py
projects/
├── team-a/
│   └── dags/ (their DAGs)
└── team-b/
    └── dags/ (their DAGs)
```

## Summary

| Feature | UNIFIED | SEPARATED |
|---------|---------|-----------|
| Setup Complexity | Low | Medium |
| Airflow Config | Simple | Multi-folder |
| Organization | Flat | Hierarchical |
| Conflict Risk | Higher | Lower |
| Performance | Faster | Slightly slower |
| Best For | Dev, Small | Prod, Large |

**Recommendation:** Start with UNIFIED for development, move to SEPARATED as you scale.

## Additional Resources

- [Project Management Guide](PROJECTS.md)
- [User Guide](USER_GUIDE.md)
- [Architecture Documentation](ARCHITECTURE.md)

---

**Need Help?** Open an issue on GitHub or check the troubleshooting section above.
