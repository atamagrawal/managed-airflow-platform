from __future__ import annotations

# =============================================================================
# Demo: ContractReadySensor (sensor, not an operator — waits until contract is ready)
# =============================================================================
# Polls the catalog until the contract is ACTIVE. With the dummy YAML connection,
# this reads the same local file as the other demos.
#
# Try setting status to something other than ACTIVE in contracts/sample_dataset.yaml to watch
# the sensor wait or fail (BREACHED + fail_on_breach=True raises).
#
# Connection: data_contract_yaml_default.
#
# Flow: sensor pokes until status is ACTIVE → then consumer_after_ready runs.
from datetime import datetime

from airflow.operators.python import PythonOperator
from airflow.providers.data.contracts.sensors.contract_ready import ContractReadySensor
from airflow.sdk import DAG

CATALOG_CONN_ID = "data_contract_yaml_default"
DATASET_URN = "urn:example:sample_dataset"


def _after_ready():
    print("Contract is ACTIVE — safe to consume the dataset.")


with DAG(
    dag_id="demo_contract_ready_sensor",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "ContractReadySensor"],
    doc_md="""
### Demo: **ContractReadySensor**

**Purpose:** **Consumer** waits until the contract is **ACTIVE** (and optionally “fresh” enough) before running downstream tasks.

**How it works:** Periodically loads the contract for the URN; returns **success** when `status == ACTIVE`.
If `fail_on_breach=True` and status is `BREACHED`, the sensor **fails** immediately instead of waiting.

**Talk track:** *“We don’t start the reporting DAG until the bronze dataset contract is green in the catalog.”*

**Try this:** Change `status` in `contracts/sample_dataset.yaml` to something other than `ACTIVE` and re-trigger;
observe the sensor in **reschedule** / poke until timeout, or failure on `BREACHED`.

**Params here:** `poke_interval=15`, `timeout=120` — shortened for demos (tune in production).

**Prereqs:** Connection `data_contract_yaml_default`.
""",
) as dag:
    wait = ContractReadySensor(
        task_id="wait_for_active_contract",
        catalog_conn_id=CATALOG_CONN_ID,
        dataset_urn=DATASET_URN,
        poke_interval=15,  # seconds between checks (demo-friendly)
        timeout=120,  # seconds before sensor gives up (demo-friendly)
        fail_on_breach=True,
    )
    work = PythonOperator(
        task_id="consumer_after_ready",
        python_callable=_after_ready,
    )
    wait >> work
