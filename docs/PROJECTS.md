# Project Management Guide

## Overview

The Managed Airflow Platform now supports **Astronomer-style projects**, allowing you to organize your Airflow workflows using a structured directory-based approach. Projects provide a complete development environment for your DAGs, plugins, dependencies, and configurations - all managed through an intuitive UI and REST API.

## What is a Project?

An Airflow project is a structured collection of files and configurations that define a complete Airflow application, similar to how Astronomer organizes Airflow deployments. Instead of managing individual DAG files, you can now create projects that contain:

- **DAGs** - Multiple DAG files organized in a `dags/` directory
- **Plugins** - Custom Airflow plugins in a `plugins/` directory
- **Includes** - Shared utilities and libraries in an `include/` directory
- **Tests** - Unit tests for your DAGs in a `tests/` directory
- **Dependencies** - Python packages (`requirements.txt`) and OS packages (`packages.txt`)
- **Configuration** - Dockerfile, Airflow settings, environment variables

## Project Structure

Each project follows the standard Astronomer directory structure:

```
my-airflow-project/
├── dags/                      # Airflow DAG files
│   ├── my_dag.py
│   ├── another_dag.py
│   └── utils/                 # DAG-specific utilities
│       └── helpers.py
│
├── plugins/                   # Custom Airflow plugins
│   └── my_custom_plugin.py
│
├── include/                   # Shared code and utilities
│   ├── sql/
│   │   └── queries.sql
│   └── common/
│       └── utils.py
│
├── tests/                     # Unit tests
│   └── test_dags.py
│
├── requirements.txt           # Python dependencies
├── packages.txt               # OS-level packages
├── Dockerfile                 # Custom Docker image
├── airflow_settings.yaml      # Airflow connections, variables, pools
├── .airflowignore            # Files to ignore
└── .env                       # Environment variables
```

## Creating a Project

### Via Web UI

1. Navigate to the **Projects** page from the sidebar
2. Click the **"Create Project"** button
3. Fill in the project details:
   - **Name**: A unique identifier for your project (e.g., `my-data-project`)
   - **Description**: Optional description of your project
   - **Deployment**: Select the Airflow deployment to associate with this project
   - **Airflow Version**: Specify the Airflow version (e.g., `2.8.1`)
   - **Owner**: Project owner name
   - **Tags**: Comma-separated tags for organization

4. Configure project files in the tabs:
   - **Basic Info**: Core project details
   - **Configuration Files**: Edit `requirements.txt`, `packages.txt`, `Dockerfile`, etc.
   - **Git Integration**: Link to a Git repository for version control

5. Click **"Create"** to save the project

### Via REST API

```bash
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{
    "deploymentId": "prod-etl",
    "name": "my-data-project",
    "description": "Production data pipelines",
    "airflowVersion": "2.8.1",
    "requirementsTxt": "pandas==2.0.0\nrequests==2.31.0\nsqlalchemy==2.0.0",
    "packagesTxt": "gcc\nlibpq-dev\nunixodbc-dev",
    "dockerfile": "FROM apache/airflow:2.8.1\n\nCOPY requirements.txt /requirements.txt\nRUN pip install --no-cache-dir -r /requirements.txt\n\nCOPY . /opt/airflow/",
    "airflowIgnore": "__pycache__/\n*.pyc\nvenv/\ntests/",
    "owner": "data-team",
    "tags": "production,etl"
  }'
```

## Managing Project Files

### Adding Files via UI

1. Navigate to your project details page
2. Click on the **"Files"** tab
3. Click **"Add File"**
4. Fill in the file details:
   - **File Path**: Path within project (e.g., `dags/my_dag.py`)
   - **File Name**: Name of the file (e.g., `my_dag.py`)
   - **File Type**: Select from DAG, PLUGIN, INCLUDE, TEST, UTIL, OTHER
   - **Content**: Enter or paste the file content
   - **Description**: Optional description

5. Click **"Add File"**

### Adding Files via API

```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/files \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "dags/my_etl_dag.py",
    "fileName": "my_etl_dag.py",
    "fileType": "DAG",
    "content": "from airflow import DAG\nfrom datetime import datetime\n\ndag = DAG(\n    dag_id=\"my_etl_dag\",\n    start_date=datetime(2024, 1, 1),\n    schedule=\"@daily\"\n)\n",
    "description": "Daily ETL pipeline"
  }'
```

### File Types

- **DAG**: Airflow DAG definition files (placed in `dags/` directory)
- **PLUGIN**: Custom Airflow plugins (placed in `plugins/` directory)
- **INCLUDE**: Shared utilities and libraries (placed in `include/` directory)
- **TEST**: Unit tests (placed in `tests/` directory)
- **UTIL**: Utility files
- **OTHER**: Any other files

## Deploying a Project

### Via UI

1. Navigate to the **Projects** page
2. Find your project in the list
3. Click the **"Deploy"** button
4. The project will be deployed to the associated Airflow deployment
5. Project status will change from `DRAFT` → `DEPLOYING` → `DEPLOYED`

### Via API

```bash
curl -X POST http://localhost:8080/api/v1/projects/{projectId}/deploy
```

### What Happens During Deployment

When you deploy a project:

1. **Directory Structure Creation**: The platform creates the project directory structure:
   ```
   {deployment-path}/projects/{project-id}/
   ├── dags/
   ├── plugins/
   ├── include/
   └── tests/
   ```

2. **Configuration Files**: All configuration files are written:
   - `requirements.txt`
   - `packages.txt`
   - `Dockerfile`
   - `airflow_settings.yaml`
   - `.airflowignore`
   - `.env`

3. **Project Files**: All DAGs, plugins, includes, and test files are written to their respective directories

4. **Airflow Integration**: The project is made available to the Airflow deployment
   - For **Local** deployments: Files are written to the local filesystem
   - For **Kubernetes**: Files can be mounted via ConfigMaps or PersistentVolumes
   - For **ECS/EC2**: Files are synced to the appropriate locations

## Project Configuration Files

### requirements.txt

Specify Python package dependencies:

```txt
# Data processing
pandas==2.0.0
numpy==1.24.0

# Database connections
sqlalchemy==2.0.0
psycopg2-binary==2.9.0

# API integrations
requests==2.31.0
boto3==1.28.0

# Airflow providers
apache-airflow-providers-amazon==8.0.0
apache-airflow-providers-postgres==5.0.0
```

### packages.txt

Specify OS-level packages needed by your project:

```txt
# Compilers and build tools
gcc
g++

# Database drivers
libpq-dev
unixodbc-dev

# Other utilities
curl
git
```

### Dockerfile

Customize the Docker image for your project:

```dockerfile
FROM apache/airflow:2.8.1

# Install system packages
USER root
COPY packages.txt /packages.txt
RUN apt-get update && \
    xargs apt-get install -y < /packages.txt && \
    apt-get clean

# Install Python dependencies
USER airflow
COPY requirements.txt /requirements.txt
RUN pip install --no-cache-dir -r /requirements.txt

# Copy project files
COPY --chown=airflow:airflow . /opt/airflow/
```

### airflow_settings.yaml

Configure Airflow connections, variables, and pools:

```yaml
connections:
  - conn_id: my_postgres_conn
    conn_type: postgres
    host: postgres.example.com
    port: 5432
    schema: my_database
    login: airflow_user
    password: ${POSTGRES_PASSWORD}

  - conn_id: my_aws_conn
    conn_type: aws
    extra: |
      {
        "region_name": "us-east-1"
      }

variables:
  - key: environment
    value: production
  - key: data_path
    value: /opt/airflow/data

pools:
  - pool_name: default_pool
    pool_slot: 128
    pool_description: Default pool
```

### .airflowignore

Specify files and directories that Airflow should ignore:

```txt
# Python cache
__pycache__/
*.py[cod]
*$py.class

# Virtual environments
venv/
env/
.venv/

# IDE files
.vscode/
.idea/
*.swp

# Tests
tests/
pytest.ini

# Git
.git/
.gitignore
```

### .env

Environment variables for your project:

```bash
# Database
DATABASE_URL=postgresql://user:pass@localhost:5432/db

# AWS
AWS_DEFAULT_REGION=us-east-1

# API Keys (use secrets management in production!)
API_KEY=your-api-key-here

# Feature flags
ENABLE_FEATURE_X=true
```

## Updating a Project

### Via UI

1. Navigate to your project details page
2. Click the **"Edit"** button
3. Update any project details or configuration
4. Click **"Update"**
5. Click **"Deploy"** to apply changes to Airflow

### Via API

```bash
curl -X PUT http://localhost:8080/api/v1/projects/{projectId} \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Updated description",
    "requirementsTxt": "pandas==2.1.0\nrequests==2.31.0"
  }'
```

## Projects vs Individual DAGs

### When to Use Projects

Use projects when you:
- Have multiple related DAGs that share code or dependencies
- Need custom Airflow plugins
- Require specific Python or OS-level packages
- Want to organize your DAGs in a structured way
- Need to version control your entire Airflow application
- Want to follow Astronomer's best practices

### When to Use Individual DAGs

Use individual DAGs when you:
- Have a single, standalone DAG
- Don't need custom dependencies
- Want quick prototyping
- Have simple workflow requirements

## Project Status Lifecycle

Projects move through the following states:

1. **DRAFT**: Initial state after creation
2. **VALIDATING**: Configuration and files are being validated
3. **VALID**: Validation passed, ready for deployment
4. **INVALID**: Validation failed, check logs for errors
5. **DEPLOYING**: Deployment in progress
6. **DEPLOYED**: Successfully deployed to Airflow
7. **FAILED**: Deployment failed, check logs
8. **UPDATING**: Modifications being applied
9. **DELETING**: Project being deleted
10. **DELETED**: Project has been removed

## Best Practices

### 1. Organize Your Code

```
dags/
├── etl/                    # ETL pipelines
│   ├── daily_etl.py
│   └── hourly_etl.py
├── ml/                     # ML pipelines
│   ├── training.py
│   └── inference.py
└── utils/                  # Shared DAG utilities
    └── helpers.py

include/
├── sql/                    # SQL queries
│   ├── extract.sql
│   └── transform.sql
└── config/                 # Configuration
    └── settings.py
```

### 2. Pin Your Dependencies

Always specify exact versions in `requirements.txt`:

```txt
# Good
pandas==2.0.0
requests==2.31.0

# Avoid
pandas>=2.0.0
requests
```

### 3. Use Environment Variables

Store sensitive data in `.env` and reference in your DAGs:

```python
import os
from airflow import DAG

DATABASE_URL = os.getenv('DATABASE_URL')
API_KEY = os.getenv('API_KEY')
```

### 4. Write Tests

Include unit tests for your DAGs in the `tests/` directory:

```python
# tests/test_my_dag.py
import pytest
from airflow.models import DagBag

def test_dag_loads():
    dagbag = DagBag(dag_folder='dags/', include_examples=False)
    assert len(dagbag.import_errors) == 0

def test_dag_structure():
    dagbag = DagBag(dag_folder='dags/')
    dag = dagbag.get_dag('my_etl_dag')
    assert len(dag.tasks) == 5
```

### 5. Document Your Project

Add a README.md to your project explaining:
- What the project does
- How to run it locally
- Dependencies and requirements
- Configuration steps
- Common troubleshooting

## Git Integration

### Linking to a Git Repository

1. When creating or editing a project, specify:
   - **Git Repository**: Repository URL (e.g., `https://github.com/user/repo.git`)
   - **Git Branch**: Branch to track (e.g., `main`)

2. The platform will track the repository URL for reference

### Future Git-Sync Integration

Planned features include:
- Automatic synchronization from Git repositories
- Commit hash tracking
- Automated deployment on Git push
- Branch-based environments

## API Reference

### Create Project
```
POST /api/v1/projects
```

### List All Projects
```
GET /api/v1/projects
```

### Get Project by ID
```
GET /api/v1/projects/{projectId}
```

### List Projects by Deployment
```
GET /api/v1/projects/deployment/{deploymentId}
```

### Update Project
```
PUT /api/v1/projects/{projectId}
```

### Delete Project
```
DELETE /api/v1/projects/{projectId}
```

### Deploy Project
```
POST /api/v1/projects/{projectId}/deploy
```

### Add File to Project
```
POST /api/v1/projects/{projectId}/files
```

### List Project Files
```
GET /api/v1/projects/{projectId}/files
```

## Troubleshooting

### Project Deployment Fails

**Check logs for errors:**
- Verify all file paths are correct
- Ensure `requirements.txt` packages are available
- Check Dockerfile syntax
- Verify deployment has sufficient resources

### DAGs Not Appearing in Airflow

**Possible causes:**
1. Project not deployed - click "Deploy" button
2. DAG files have syntax errors - check file content
3. Files not in `dags/` directory - verify file paths
4. `.airflowignore` is too restrictive - review ignore patterns

### Dependency Installation Errors

**Solutions:**
- Pin exact package versions in `requirements.txt`
- Add missing system packages to `packages.txt`
- Check for package compatibility issues
- Review Docker build logs

## Examples

### Example 1: Simple ETL Project

```bash
# Create project
curl -X POST http://localhost:8080/api/v1/projects \
  -H "Content-Type: application/json" \
  -d '{
    "deploymentId": "prod",
    "name": "simple-etl",
    "description": "Simple ETL pipeline",
    "requirementsTxt": "pandas==2.0.0\nsqlalchemy==2.0.0",
    "airflowVersion": "2.8.1"
  }'

# Add DAG file
curl -X POST http://localhost:8080/api/v1/projects/simple-etl/files \
  -H "Content-Type: application/json" \
  -d '{
    "filePath": "dags/etl_pipeline.py",
    "fileName": "etl_pipeline.py",
    "fileType": "DAG",
    "content": "from airflow import DAG\n..."
  }'

# Deploy
curl -X POST http://localhost:8080/api/v1/projects/simple-etl/deploy
```

### Example 2: ML Pipeline Project

See the examples in the repository:
- `examples/ml-pipeline-project/` - Complete ML pipeline project
- `examples/data-engineering-project/` - Data engineering project

## Migrating from Individual DAGs to Projects

To migrate existing DAGs to a project:

1. Create a new project
2. Add each DAG as a file in the project
3. Consolidate shared code into `include/` directory
4. Move custom plugins to `plugins/` directory
5. Create `requirements.txt` with all dependencies
6. Add tests to `tests/` directory
7. Deploy the project
8. Verify all DAGs appear in Airflow
9. Delete old individual DAG entries (optional)

## Next Steps

- **[User Guide](USER_GUIDE.md)** - Complete platform usage guide
- **[Setup Guide](SETUP.md)** - Platform setup instructions
- **[API Documentation](../README.md#api-documentation)** - Full API reference

---

**Need Help?**
- Check the troubleshooting section above
- Review the examples in the repository
- Open an issue on GitHub
- Contact support

**Making Airflow development easier, one project at a time!** 🚀
