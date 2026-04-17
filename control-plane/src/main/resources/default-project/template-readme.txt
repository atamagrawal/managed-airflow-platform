Default project template (new projects)
=======================================

Layout under this folder mirrors the project repo (dags/, contracts/, …). Edit files here to
change what new projects get — no Java changes.

Dummy catalog connection (no external services)
-----------------------------------------------
All contract demos use the system-managed default connection id:
  - data_contract_yaml_default
  - conn_type: data_contract_yaml
  - Maps URN urn:example:sample_dataset -> contracts/sample_dataset.yaml under /opt/airflow

Local docker-compose runs `airflow connections add` during airflow-init.
The template also ships airflow_settings.yaml on the project so this connection definition is visible
and can be re-imported if needed.

UAPE provider demo DAG
----------------------
`dags/demo_uape_provider_example.py` is included as a lightweight example for the UAPE provider.
It is intentionally structured with:
  - clear tasks (`EmptyOperator`) that UAPE can recommend for overlap, and
  - one opaque task (`BashOperator`) where UAPE abstains.

After syncing and triggering once, run:
  - airflow uape independence-report demo_uape_provider_example
  - airflow uape export demo_uape_provider_example --format json

Placeholders in any template file: ${projectId}, ${projectName}

Config: project.default-template.templates + active in application.yml. Optional extra-requirements.txt per template folder.
