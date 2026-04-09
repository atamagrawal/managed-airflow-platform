"""Producer pattern with stacked TaskFlow annotations: ``@task`` + ``@contract_publish``.

Stats are passed through the data-contract publish path (local YAML connection = file-backed hook,
same idea as ``example/aip-07/minimal_decorators/dags/simple_data_contract_publish_decorators.py``).
"""

from __future__ import annotations

from datetime import datetime

from airflow.providers.data.contracts_decorators.decorators.contract_publish import (
    contract_publish,
)
from airflow.sdk import DAG, task

DATASET_URN = "urn:example:sample_dataset"


@task
@contract_publish(
    dataset_urn=DATASET_URN,
    upstream_urns=[],
    update_contract_status=True,
    contract_status="ACTIVE",
    emit_run_facet=True,
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
    tags=["data-contracts", "demo", "contract_publish", "decorators", "stacked"],
    doc_md=__doc__,
) as _:
    publish_sample_dataset_stats()
