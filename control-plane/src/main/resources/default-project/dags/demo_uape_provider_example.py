from __future__ import annotations

from datetime import datetime

from airflow.operators.bash import BashOperator
from airflow.operators.empty import EmptyOperator
from airflow.sdk import DAG


with DAG(
    dag_id="demo_uape_provider_example",
    schedule=None,
    start_date=datetime(2025, 1, 1),
    catchup=False,
    tags=["uape", "demo", "provider"],
    doc_md="""
### Demo: **UAPE provider** (uncertainty-aware parallelization hints)

**Purpose:** Show how the UAPE provider classifies tasks and reports structural parallelization hints.

**Graph shape in this demo:**
- `start -> prep_a -> join`
- `start -> prep_b -> join`
- `start -> opaque_side_job -> join`

`prep_a` and `prep_b` use `EmptyOperator`, so with the current UAPE policy they are **clear** tasks.
`opaque_side_job` uses `BashOperator`, so UAPE treats it as **opaque** and abstains from parallel hints with it.

**Run the demo report (CLI):**
1. Trigger this DAG once so it is serialized.
2. Run:
   - `airflow uape independence-report demo_uape_provider_example`
   - `airflow uape export demo_uape_provider_example --format json`

Expected outcome:
- One clear overlap hint between `prep_a` and `prep_b`
- Abstentions for pairs that include `opaque_side_job`

If your deployment has the UAPE plugin UI enabled, open the DAG page and check the
**UAPE recommendations** tab for the same report rendered in the UI.
""",
) as dag:
    start = EmptyOperator(task_id="start")

    # These two tasks are intentionally "clear" under current UAPE defaults.
    prep_a = EmptyOperator(task_id="prep_a")
    prep_b = EmptyOperator(task_id="prep_b")

    # BashOperator is currently opaque for UAPE, used to demonstrate abstentions.
    opaque_side_job = BashOperator(
        task_id="opaque_side_job",
        bash_command='echo "simulate opaque task" && sleep 1',
    )

    join = EmptyOperator(task_id="join")

    start >> [prep_a, prep_b, opaque_side_job] >> join
