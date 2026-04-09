"""Trigger-user guard with stacked annotations, matching the minimal decorators example.

Stacked style shown here:
* ``@task`` outer with ``@contract_trigger_user_guard(dataset_urn=...)`` inner.

Edit the system-mapped YAML for ``urn:example:sample_dataset`` (``allowed_trigger_users``) for your environment.

Flow Deck puts the signed-in platform user in ``dag_run.conf["managed_platform"]["triggered_by_username"]``.
Airflow’s ``@contract_trigger_user_guard`` compares against ``dag_run.triggering_user_name`` (the REST auth user).
Flow Deck normally triggers with the **signed-in** platform user (after FAB sync). List those usernames under
``allowed_trigger_users``, or use ``airflow.api.trigger-username`` / ``use-logged-in-user-for-dag-triggers: false`` for a fixed API user.
"""

from __future__ import annotations
from datetime import datetime

from airflow.providers.data.contracts_decorators.decorators.contract_trigger_user_guard import (
    contract_trigger_user_guard,
)
from airflow.sdk import DAG, task

DATASET_URN = "urn:example:sample_dataset"


@task
@contract_trigger_user_guard(
    dataset_urn=DATASET_URN,
    when_triggering_user_missing="allow",
    on_unauthorized="fail",
)
def workload_with_stacked_guard() -> str:
    return "ok"


with DAG(
    dag_id="demo_contract_trigger_user_guard_taskflow",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "contract_trigger_user_guard", "decorators", "trigger-user", "stacked"],
    doc_md=__doc__,
) as _:
    workload_with_stacked_guard()
