"""Producer pattern with TaskFlow ``@contract_publish_task`` (``@task.contract_publish``).

Stats are passed through the data-contract publish path (local YAML connection = file-backed hook,
same idea as ``example/aip-07/minimal_decorators/dags/simple_data_contract_publish_decorators.py``).
"""

from __future__ import annotations

from datetime import datetime

from airflow.providers.data.contracts_decorators.decorators.contract_publish import (
    contract_publish_task,
)
from airflow.sdk import DAG

CATALOG_CONN_ID = "local_dummy_data_contract_catalog"
DATASET_URN = "urn:example:sample_dataset"


@contract_publish_task(
    catalog_conn_id=CATALOG_CONN_ID,
    dataset_urn=DATASET_URN,
    upstream_urns=[],
    update_contract_status=True,
    contract_status="ACTIVE",
    emit_run_facet=True,
    task_id="publish_contract",
)
def publish_sample_dataset_stats() -> dict:
    return {
        "row_count": 3,
        "schema": [
            {"name": "id", "type": "STRING", "nullable": False},
            {"name": "amount", "type": "FLOAT", "nullable": False},
        ],
    }


with DAG(
    dag_id="demo_contract_publish_taskflow",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "contract_publish_task", "decorators"],
    doc_md=__doc__,
) as _:
    publish_sample_dataset_stats()
