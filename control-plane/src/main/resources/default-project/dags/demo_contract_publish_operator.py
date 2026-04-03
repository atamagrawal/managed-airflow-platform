from __future__ import annotations

# =============================================================================
# Demo: ContractPublishOperator
# =============================================================================
# Producer-side step: after the pipeline builds stats, this operator calls the
# catalog hook (emit lineage, update status). With the YAML "dummy" connection,
# calls are effectively no-ops / logs only — still useful to show the producer pattern.
#
# Connection: local_dummy_data_contract_catalog (file-backed, no external service).
#
# Flow: build_output_stats → publish pushes stats XCom into the hook’s publish path (YAML hook is mostly a no-op).
from datetime import datetime

from airflow.providers.data.contracts.operators.contract_publish import ContractPublishOperator
from airflow.sdk import DAG, task

CATALOG_CONN_ID = "local_dummy_data_contract_catalog"
DATASET_URN = "urn:example:sample_dataset"


@task
def build_output_stats() -> dict:
    return {
        "row_count": 3,
        "schema": [
            {"name": "id", "type": "STRING", "nullable": False},
            {"name": "amount", "type": "FLOAT", "nullable": False},
        ],
    }


with DAG(
    dag_id="demo_contract_publish_operator",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["data-contracts", "demo", "ContractPublishOperator"],
    doc_md="""
### Demo: **ContractPublishOperator**

**Purpose:** **Producer** DAG step after data is written — stamp / announce the dataset to the catalog (lineage + status).

**With this stack:** The hook is **YAML “catalog-lite”**, so lineage/status calls are effectively logged or no-ops. Same code pattern plugs into **DataHub** later by swapping the Airflow connection.

**Talk track:** *“Once we’re happy with the data, we tell the catalog the dataset is ready so consumers can rely on contract metadata.”*

**Try this:** Run the DAG and inspect task logs for publish; compare to a real DataHub connection in a future environment.

**Prereqs:** Connection `local_dummy_data_contract_catalog`.
""",
) as dag:
    stats = build_output_stats()
    publish = ContractPublishOperator(
        task_id="publish_contract_signals",
        catalog_conn_id=CATALOG_CONN_ID,
        dataset_urn=DATASET_URN,
        stats_xcom_task_id="build_output_stats",
        update_contract_status=True,
        contract_status="ACTIVE",
        # Lineage is emitted in execute() via the catalog hook. Use emit_run_facet (default True)
        # to attach an Airflow run description facet; there is no emit_lineage constructor arg.
        emit_run_facet=True,
    )
    stats >> publish
