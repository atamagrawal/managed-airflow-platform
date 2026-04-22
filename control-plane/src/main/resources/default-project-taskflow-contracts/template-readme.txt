TaskFlow data-contract starter template
=======================================

Several DAGs under dags/, one decorator style each (Apache Airflow data-contracts-decorators), aligned with
example/aip-07/minimal_decorators in the Airflow repo:

  contract_validate_task, contract_publish_task, contract_ready + contract_breach_guard_task,
  contract_trigger_user_guard_task.

Shadow DAG example pair (AIP-09 style):
  demo_shadow_daily_summary_taskflow.py (production DAG ``demo_shadow_summary_tf``) +
  demo_shadow_daily_summary_v2_taskflow.py (candidate ``demo_shadow_summary_v2_tf``: ``@shadow_dag`` stacked on ``@dag`` so the DAG processor auto-registers the experiment).
  Both use ``schedule=None`` (trigger manually). The candidate demonstrates ``shadow_output_path`` for shadow runs and a local fallback for standalone testing.

Also includes:
  demo_uape_provider_taskflow.py — UAPE provider example DAG with clear and opaque branches
  so `airflow uape independence-report` shows both overlap hints and conservative abstentions.

Placeholders: ${projectId}, ${projectName}

extra-requirements.txt at this root is merged into new projects' requirements.txt by the template seeder.

Set project.default-template.active: taskflow-contracts (or env PROJECT_DEFAULT_TEMPLATE_ACTIVE) to use this template.
