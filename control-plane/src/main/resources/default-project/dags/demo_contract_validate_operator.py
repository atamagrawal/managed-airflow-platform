from __future__ import annotations

# =============================================================================
# Demo: ContractValidateOperator
# =============================================================================
# Compares task output (XCom "stats": row_count + schema) to the contract loaded
# via the LOCAL dummy connection below — YAML on disk only, no external catalog.
#
# Connection: data_contract_yaml_default (see airflow_settings.yaml).
# Try lowering row_count in build_contract_stats below minimum in the contract to see a failure.
#
# Flow: build_contract_stats pushes a dict to XCom → validate pulls it and checks schema + row count
#       against contracts/sample_dataset.yaml (via URN mapping on the connection).
from datetime import datetime

from airflow.providers.data.contracts.operators.contract_validate import ContractValidateOperator
from airflow.sdk import DAG, task

CATALOG_CONN_ID = "data_contract_yaml_default"
DATASET_URN = "urn:example:sample_dataset"


@task
def build_contract_stats() -> dict:
    # Shape matches what ContractValidateOperator expects: row_count + schema list (name, type, nullable).
    return {
        "row_count": 3,
        "schema": [
            {"name": "id", "type": "STRING", "nullable": False},
            {"name": "amount", "type": "FLOAT", "nullable": False},
        ],
    }


with DAG(
    dag_id="demo_contract_validate_operator",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "ContractValidateOperator"],
    doc_md="""
### Demo: **ContractValidateOperator**

**Purpose:** Prove pipeline output matches the **data contract** (schema + row-count rules).

**How it works:**
1. `build_contract_stats` returns stats and stores them in XCom (default key `return_value`).
2. `validate_contract` loads the contract for `urn:example:sample_dataset` using the **local dummy**
   connection (`data_contract_yaml` → YAML file only, no external catalog).
3. Validation compares XCom stats to `contracts/sample_dataset.yaml`.

**Talk track:** *“After the extract/transform task, we assert the dataset still matches what downstream teams agreed on.”*

**Try this:** Set `row_count` below `min_row_count` in the contract YAML, or return an extra/missing column in `schema`, then trigger the DAG and show the task failure.

**Prereqs:** Connection `data_contract_yaml_default` exists (configured via airflow_settings.yaml).
""",
) as dag:
    stats = build_contract_stats()
    validate = ContractValidateOperator(
        task_id="validate_contract",
        catalog_conn_id=CATALOG_CONN_ID,
        dataset_urn=DATASET_URN,
        stats_xcom_task_id="build_contract_stats",
        validate_schema=True,
        validate_completeness=True,
        validate_freshness=False,  # would need data_as_of in stats + freshness rules in YAML
        validate_sla=False,  # keep demo minimal
        report_breach_to_catalog=False,  # avoid calling catalog on failure (dummy hook is file-only)
    )
    stats >> validate
