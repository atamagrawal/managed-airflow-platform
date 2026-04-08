"""Consumer pattern: ``@contract_ready_task`` then ``@contract_breach_guard_task`` then plain work.

Chains the same flow as ``example/aip-07/minimal_decorators/dags/simple_data_contract_consumer_decorators.py``:
wait until the contract is ready for the URN, ensure it is not breached, then run a placeholder downstream task.
"""

from __future__ import annotations

from datetime import datetime

from airflow.providers.data.contracts_decorators.decorators.contract_breach_guard import (
    contract_breach_guard_task,
)
from airflow.providers.data.contracts_decorators.decorators.contract_ready import contract_ready_task
from airflow.sdk import DAG, task

CATALOG_CONN_ID = "local_dummy_data_contract_catalog"
DATASET_URN = "urn:example:sample_dataset"


@contract_ready_task(
    catalog_conn_id=CATALOG_CONN_ID,
    poke_interval=5,
    timeout=120,
    mode="poke",
    task_id="wait_for_contract",
)
def wait_for_sample_dataset() -> str:
    return DATASET_URN


@contract_breach_guard_task(
    catalog_conn_id=CATALOG_CONN_ID,
    on_breach="fail",
    task_id="guard_contracts",
)
def guard_upstream_contracts() -> list[str]:
    return [DATASET_URN]


@task(task_id="downstream_placeholder")
def downstream_placeholder() -> str:
    return "ok"


with DAG(
    dag_id="demo_contract_consumer_taskflow",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=[
        "data-contracts",
        "demo",
        "contract_ready_task",
        "contract_breach_guard_task",
        "decorators",
    ],
    doc_md=__doc__,
) as _:
    wait_for_sample_dataset() >> guard_upstream_contracts() >> downstream_placeholder()
