"""Shadow candidate DAG for ``demo_shadow_summary_tf``.

Uses ``@shadow_dag`` above ``@dag`` (AIP-09 / ``airflow.sdk.definitions.shadow``)
so the DAG Processor auto-registers the experiment when the file is parsed.

* ``context["shadow_output_path"]`` is set during shadow runs.
* Otherwise output falls back under ``$AIRFLOW_HOME/prod_output/...``.

Both this DAG and production use ``schedule=None`` — trigger production manually;
the platform then runs the shadow candidate for that DagRun (no cron schedule).
"""

from __future__ import annotations

import json
import os
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from airflow.sdk import dag, task, shadow_dag

_PROD_OUTPUT_DIR = (
    Path(os.environ.get("AIRFLOW_HOME", "~/airflow")).expanduser()
    / "prod_output"
    / "demo_shadow_summary"
)


def _raw_events(logical_date: datetime) -> list[dict]:
    base = logical_date.toordinal() % 100
    return [
        {"event_id": f"evt_{base + i:04d}", "user_id": f"u{(i % 5) + 1}", "amount": round(10.0 + i * 1.5, 2)}
        for i in range(15)
    ]


@task
def extract(**context) -> list[dict]:
    logical_date = context.get("logical_date")
    if logical_date is None:
        logical_date = datetime.now(timezone.utc)
    return _raw_events(logical_date)


@task
def transform_v2(raw_events: list[dict], **context) -> int:
    run_id: str = context["run_id"]
    shadow_output_path: str | None = context.get("shadow_output_path")

    if shadow_output_path:
        output_path = Path(shadow_output_path)
        output_path.parent.mkdir(parents=True, exist_ok=True)
    else:
        fallback_dir = _PROD_OUTPUT_DIR / run_id / "transform_v2"
        fallback_dir.mkdir(parents=True, exist_ok=True)
        output_path = fallback_dir / "output.jsonl"

    totals: dict[str, float] = defaultdict(float)
    counts: dict[str, int] = defaultdict(int)
    for evt in raw_events:
        totals[evt["user_id"]] += evt["amount"]
        counts[evt["user_id"]] += 1

    rows = [
        {
            "user_id": uid,
            "total_amount": round(totals[uid], 2),
            "transaction_count": counts[uid],  # intentional candidate-only field
        }
        for uid in sorted(totals)
    ]
    with output_path.open("w") as fh:
        for row in rows:
            fh.write(json.dumps(row) + "\n")

    return len(rows)


@task
def load(row_count: int) -> None:
    if row_count <= 0:
        raise ValueError("No rows produced")


@shadow_dag(
    shadows="demo_shadow_summary_tf",
    ttl="7d",
    divergence_alert=0.05,
    notify=None,
)
@dag(
    dag_id="demo_shadow_summary_v2_tf",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    tags=["demo", "shadow", "candidate", "taskflow"],
    doc_md=__doc__,
)
def demo_shadow_summary_v2_tf():
    load(transform_v2(extract()))


demo_shadow_summary_v2_tf()
