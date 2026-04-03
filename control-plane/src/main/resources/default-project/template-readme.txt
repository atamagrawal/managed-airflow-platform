Default project template (new projects)
=======================================

Layout under this folder mirrors the project repo (dags/, contracts/, …). Edit files here to
change what new projects get — no Java changes.

Dummy catalog connection (no external services)
-----------------------------------------------
All demo DAGs use connection id: local_dummy_data_contract_catalog
  - conn_type: data_contract_yaml
  - Points URN urn:example:sample_dataset → contracts/sample_dataset.yaml under /opt/airflow

Local docker-compose runs `airflow connections add` for that id (airflow-init).
The template also ships airflow_settings.yaml on the Project so the definition is visible and
can be re-imported if needed.

Placeholders in any template file: ${projectId}, ${projectName}

Config: project.default-template.* in application.yml
