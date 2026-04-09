"""Data-contract validation with stacked TaskFlow annotations: ``@task`` + ``@contract_validate``.

One of several decorator examples in this folder. Mirrors
``example/aip-07/minimal_decorators/dags/simple_data_contract_decorators.py``: task output is checked
against ``contracts/sample_dataset.yaml``.

Requires ``apache-airflow-providers-data-contracts`` and
``apache-airflow-providers-data-contracts-decorators`` (see template ``extra-requirements.txt``).
"""

from __future__ import annotations
from datetime import datetime

from airflow.providers.data.contracts_decorators.decorators.contract_validate import (
    contract_validate,
)
from airflow.sdk import DAG, task

DATASET_URN = "urn:example:sample_dataset"


@task
@contract_validate(
    dataset_urn=DATASET_URN,
    validate_schema=True,
    validate_completeness=True,
    validate_freshness=False,
    validate_sla=False,
    report_breach_to_catalog=False,
)
def validate_sample_dataset() -> dict:
    return {
        "row_count": 3,
        "schema": [
            {"name": "id", "type": "STRING", "nullable": False},
            {"name": "amount", "type": "FLOAT", "nullable": False},
        ],
    }


with DAG(
    dag_id="demo_contract_validate_taskflow",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "contract_validate", "decorators", "stacked"],
    doc_md=__doc__,
) as _:
    validate_sample_dataset()
