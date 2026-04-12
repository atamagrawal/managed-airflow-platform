from __future__ import annotations

# =============================================================================
# Demo: ContractBreachGuardOperator
# =============================================================================
# Consumer gate: downstream tasks run only if the contract status is not BREACHED.
# With the local YAML contract, status comes from contracts/sample_dataset.yaml.
#
# Experiment: set `status: BREACHED` in that file, trigger this DAG — the guard fails.
# Break-glass: Airflow Variable `demo_contract_guard_bypass` = true.
#
# Connection: data_contract_yaml_default (local files only).
#
# Flow: guard checks contract status from YAML → if not BREACHED, consumer placeholder runs.
from datetime import datetime

from airflow.operators.python import PythonOperator
from airflow.providers.data.contracts.operators.contract_breach_guard import ContractBreachGuardOperator
from airflow.sdk import DAG

CATALOG_CONN_ID = "data_contract_yaml_default"
DATASET_URN = "urn:example:sample_dataset"


def _downstream_placeholder():
    print("Consumer logic would run here (contract is healthy).")


with DAG(
    dag_id="demo_contract_breach_guard_operator",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "ContractBreachGuardOperator"],
    doc_md="""
### Demo: **ContractBreachGuardOperator**

**Purpose:** **Consumer** gate — do not run expensive work if upstream datasets are in a **BREACHED** contract state.

**How it works:** Reads contract **status** for each listed URN (here one: `urn:example:sample_dataset`).
If status is `BREACHED`, `on_breach="fail"` stops the DAG; set to `"skip"` or `"warn"` for different UX.

**Talk track:** *“We don’t process data if the producer’s contract guarantees are broken — unless ops flips an Airflow Variable for break-glass.”*

**Try this:**
1. Set `status: BREACHED` in `contracts/sample_dataset.yaml`, deploy/sync files, trigger DAG → guard fails.
2. Add Variable `demo_contract_guard_bypass` = `true` → guard passes (see operator `override_var`).

**Prereqs:** Connection `data_contract_yaml_default`.
""",
) as dag:
    guard = ContractBreachGuardOperator(
        task_id="ensure_contracts_healthy",
        catalog_conn_id=CATALOG_CONN_ID,
        dataset_urns=[DATASET_URN],
        on_breach="fail",
        override_var="demo_contract_guard_bypass",  # Variable name; set to true/1/yes to bypass
    )
    consume = PythonOperator(
        task_id="consumer_work",
        python_callable=_downstream_placeholder,
    )
    guard >> consume
