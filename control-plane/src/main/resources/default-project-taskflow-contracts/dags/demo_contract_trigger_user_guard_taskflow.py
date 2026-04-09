"""Trigger-user guard from contract YAML, matching the minimal decorators example.

Two styles are shown:

* ``@with_contract_trigger_user_from_yaml`` stacked under ``@task``.
* ``@contract_trigger_user_guard_task(contract_yaml_path=...)`` as a single decorated task.

Edit ``contracts/sample_dataset.yaml`` (``allowed_trigger_users``) for your environment.

Flow Deck puts the signed-in platform user in ``dag_run.conf["managed_platform"]["triggered_by_username"]``.
Airflow’s ``@contract_trigger_user_guard`` compares against ``dag_run.triggering_user_name`` (the REST auth user).
Flow Deck normally triggers with the **signed-in** platform user (after FAB sync). List those usernames under
``allowed_trigger_users``, or use ``airflow.api.trigger-username`` / ``use-logged-in-user-for-dag-triggers: false`` for a fixed API user.
"""

from __future__ import annotations

import os
from datetime import datetime

from airflow.providers.data.contracts_decorators.decorators.contract_trigger_user_guard import (
    contract_trigger_user_guard_task,
)
from airflow.providers.data.contracts_decorators.decorators.with_contract_trigger_user_from_yaml import (
    with_contract_trigger_user_from_yaml,
)
from airflow.sdk import DAG, task

_PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTRACT_YAML = os.path.join(_PROJECT_ROOT, "contracts", "sample_dataset.yaml")


@task
@with_contract_trigger_user_from_yaml(CONTRACT_YAML)
def workload_with_stacked_guard() -> str:
    return "ok"


@contract_trigger_user_guard_task(
    task_id="single_operator_body",
    contract_yaml_path=CONTRACT_YAML,
    when_triggering_user_missing="allow",
    on_unauthorized="fail",
)
def workload_as_single_decorated_task() -> str:
    return "ok"


with DAG(
    dag_id="demo_contract_trigger_user_guard_taskflow",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "contract_trigger_user_guard_task", "decorators", "trigger-user"],
    doc_md=__doc__,
) as _:
    workload_with_stacked_guard()
    workload_as_single_decorated_task()
