"""Production DAG for shadow demonstration.

This DAG writes deterministic daily per-user totals under
``$AIRFLOW_HOME/prod_output/demo_shadow_summary`` so a shadow candidate
can be compared against it.

Runs are **manual only** (``schedule=None``): trigger this DAG from the UI or
CLI when you want a baseline run; the scheduler does not create periodic runs.
"""

from __future__ import annotations

import json
import os
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from airflow.sdk import DAG, task

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
def transform(raw_events: list[dict], **context) -> int:
    run_id: str = context["run_id"]
    output_dir = _PROD_OUTPUT_DIR / run_id / "transform"
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "output.jsonl"

    totals: dict[str, float] = defaultdict(float)
    for evt in raw_events:
        totals[evt["user_id"]] += evt["amount"]

    rows = [{"user_id": uid, "total_amount": round(total, 2)} for uid, total in sorted(totals.items())]
    with output_path.open("w") as fh:
        for row in rows:
            fh.write(json.dumps(row) + "\n")

    return len(rows)


@task
def load(row_count: int) -> None:
    if row_count <= 0:
        raise ValueError("No rows produced")


with DAG(
    dag_id="demo_shadow_summary_tf",
    schedule=None,
    start_date=datetime(2026, 1, 1),
    catchup=False,
    tags=["demo", "shadow", "production", "taskflow"],
    doc_md=__doc__,
) as _:
    load(transform(extract()))
