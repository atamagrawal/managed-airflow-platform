"""Consumer pattern with stacked annotations: ``@task`` + guards.

Chains the same flow as ``example/aip-07/minimal_decorators/dags/simple_data_contract_consumer_decorators.py``:
run a one-shot readiness check for the URN, ensure it is not breached, then run a placeholder downstream task.
"""

from __future__ import annotations

from datetime import datetime

from airflow.providers.data.contracts_decorators.decorators.contract_breach_guard import (
    contract_breach_guard,
)
from airflow.providers.data.contracts_decorators.decorators.contract_ready import contract_ready
from airflow.sdk import DAG, task

DATASET_URN = "urn:example:sample_dataset"


@task
@contract_ready()
def wait_for_sample_dataset() -> str:
    return DATASET_URN


@task
@contract_breach_guard(
    on_breach="fail",
)
def guard_upstream_contracts() -> list[str]:
    return [DATASET_URN]


@task
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
        "contract_ready",
        "contract_breach_guard",
        "decorators",
        "stacked",
    ],
    doc_md=__doc__,
) as _:
    wait_for_sample_dataset() >> guard_upstream_contracts() >> downstream_placeholder()
