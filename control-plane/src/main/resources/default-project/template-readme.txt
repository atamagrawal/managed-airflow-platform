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

Placeholders in any template file: ${projectId}, ${projectName}

Config: project.default-template.templates + active in application.yml. Optional extra-requirements.txt per template folder.
