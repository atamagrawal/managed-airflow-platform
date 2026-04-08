TaskFlow data-contract starter template
=======================================

Several DAGs under dags/, one decorator style each (Apache Airflow data-contracts-decorators), aligned with
example/aip-07/minimal_decorators in the Airflow repo:

  contract_validate_task, contract_publish_task, contract_ready + contract_breach_guard_task,
  contract_trigger_user_guard_task.

Placeholders: ${projectId}, ${projectName}

extra-requirements.txt at this root is merged into new projects' requirements.txt by the template seeder.

Set project.default-template.active: taskflow-contracts (or env PROJECT_DEFAULT_TEMPLATE_ACTIVE) to use this template.
