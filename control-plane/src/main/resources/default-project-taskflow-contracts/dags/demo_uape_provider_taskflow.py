"""UAPE provider example DAG for TaskFlow template projects.

This DAG is intentionally shaped for UAPE (Uncertainty-Aware Parallelization Engine):
- two sibling ``EmptyOperator`` tasks (clear under current conservative policy), and
- one sibling ``@task`` TaskFlow task (opaque under current policy).

After this DAG is parsed/serialized, run:
  airflow uape independence-report demo_uape_provider_taskflow
  airflow uape export demo_uape_provider_taskflow --format json

Expected:
- clear overlap hint for ``prep_a`` <-> ``prep_b``
- abstentions for pairs involving ``opaque_side_job``
"""

from __future__ import annotations

from datetime import datetime

from airflow.operators.empty import EmptyOperator
from airflow.sdk import DAG, task


@task
def opaque_side_job() -> str:
    # Intentionally opaque for UAPE: TaskFlow decorator compiles to a non-allowlisted operator type.
    return "simulated opaque branch"


with DAG(
    dag_id="demo_uape_provider_taskflow",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["uape", "demo", "provider", "taskflow"],
    doc_md=__doc__,
) as _:
    start = EmptyOperator(task_id="start")
    prep_a = EmptyOperator(task_id="prep_a")
    prep_b = EmptyOperator(task_id="prep_b")
    opaque = opaque_side_job()
    join = EmptyOperator(task_id="join")

    start >> [prep_a, prep_b, opaque] >> join
