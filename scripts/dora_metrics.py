import os
import statistics
from datetime import datetime, timezone, timedelta
import requests
from prometheus_client import CollectorRegistry, Gauge, push_to_gateway

GITLAB_API = os.environ["CI_API_V4_URL"]
PROJECT_ID = os.environ["CI_PROJECT_ID"]
TOKEN = os.environ["DORA_GITLAB_TOKEN"]
PUSHGATEWAY_URL = os.environ.get("PUSHGATEWAY_URL", "http://localhost:9091")
WINDOW_DAYS = int(os.environ.get("DORA_WINDOW_DAYS", "30"))

HEADERS = {"PRIVATE-TOKEN": TOKEN}


def get_paginated(url, params):
    results = []
    page = 1
    while True:
        params["page"] = page
        params["per_page"] = 100
        r = requests.get(url, headers=HEADERS, params=params, timeout=30)
        r.raise_for_status()
        batch = r.json()
        if not batch:
            break
        results.extend(batch)
        if not r.headers.get("x-next-page"):
            break
        page += 1
    return results


def parse_dt(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def fetch_deploy_jobs(since):
    pipelines = get_paginated(
        f"{GITLAB_API}/projects/{PROJECT_ID}/pipelines",
        {"ref": "main", "updated_after": since.isoformat()},
    )
    deploy_jobs = []
    for pipeline in pipelines:
        jobs = get_paginated(
            f"{GITLAB_API}/projects/{PROJECT_ID}/pipelines/{pipeline['id']}/jobs",
            {},
        )
        for job in jobs:
            if job["name"] == "deploy" and job["status"] in ("success", "failed"):
                job["pipeline_created_at"] = pipeline["created_at"]
                deploy_jobs.append(job)
    return sorted(deploy_jobs, key=lambda j: j["created_at"])


def compute_metrics(deploy_jobs, window_days):
    successes = [j for j in deploy_jobs if j["status"] == "success"]
    failures = [j for j in deploy_jobs if j["status"] == "failed"]
    total = len(deploy_jobs)

    # 1. Deployment Frequency : déploiements réussis / jour
    deployment_frequency = len(successes) / window_days if window_days else 0.0

    # 2. Lead Time for Changes : durée moyenne entre le déclenchement du pipeline
    #    sur main (proxy du commit) et la fin du déploiement réussi
    lead_times = [
        (parse_dt(j["finished_at"]) - parse_dt(j["pipeline_created_at"])).total_seconds()
        for j in successes
        if j.get("finished_at") and j.get("pipeline_created_at")
    ]
    lead_time_avg = statistics.mean(lead_times) if lead_times else 0.0

    # 3. Change Failure Rate : % de déploiements en échec sur le total
    change_failure_rate = (len(failures) / total * 100) if total else 0.0

    # 4. MTTR : durée moyenne entre un déploiement en échec et le prochain déploiement réussi
    restore_times = []
    for failure in failures:
        if not failure.get("finished_at"):
            continue
        failure_end = parse_dt(failure["finished_at"])
        next_success = next(
            (s for s in successes if s.get("finished_at") and parse_dt(s["finished_at"]) > failure_end),
            None,
        )
        if next_success:
            restore_times.append((parse_dt(next_success["finished_at"]) - failure_end).total_seconds())
    mttr_avg = statistics.mean(restore_times) if restore_times else 0.0

    return {
        "deployment_frequency_per_day": deployment_frequency,
        "lead_time_seconds": lead_time_avg,
        "change_failure_rate_percent": change_failure_rate,
        "mttr_seconds": mttr_avg,
        "sample_size": total,
    }


def push_metrics(metrics):
    registry = CollectorRegistry()
    for name, value in metrics.items():
        Gauge(f"orion_dora_{name}", f"Métrique DORA Orion : {name}", registry=registry).set(value)
    push_to_gateway(PUSHGATEWAY_URL, job="dora_metrics", registry=registry)


def main():
    since = datetime.now(timezone.utc) - timedelta(days=WINDOW_DAYS)
    deploy_jobs = fetch_deploy_jobs(since)
    metrics = compute_metrics(deploy_jobs, WINDOW_DAYS)
    print(metrics)
    push_metrics(metrics)


if __name__ == "__main__":
    main()