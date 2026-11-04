import os
import statistics
from datetime import datetime, timezone, timedelta
import requests
from prometheus_client import CollectorRegistry, Gauge, push_to_gateway

# API GitHub (remplace l'API GitLab). Le repo est public : le jeton est facultatif,
# il releve seulement la limite d'appels (60/h sans jeton, 5000/h avec).
GITHUB_API = "https://api.github.com"
REPO = os.environ.get("GITHUB_REPOSITORY", "SylvainR-dev/orion")
TOKEN = os.environ.get("GITHUB_TOKEN", "")
# Job de la CI considere comme "livraison" (la CI s'arrete a la publication GHCR).
DEPLOY_JOB_NAME = os.environ.get("DORA_DEPLOY_JOB", "publish-images")
PUSHGATEWAY_URL = os.environ.get("PUSHGATEWAY_URL", "http://localhost:9091")
WINDOW_DAYS = int(os.environ.get("DORA_WINDOW_DAYS", "30"))

HEADERS = {
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
}
if TOKEN:
    HEADERS["Authorization"] = f"Bearer {TOKEN}"


def get_paginated(url, params, key):
    # GitHub renvoie un objet {"<key>": [...]}, on pagine jusqu'a une page incomplete.
    results = []
    page = 1
    while True:
        params["page"] = page
        params["per_page"] = 100
        r = requests.get(url, headers=HEADERS, params=params, timeout=30)
        r.raise_for_status()
        batch = r.json()[key]
        results.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    return results


def parse_dt(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def fetch_deploy_jobs(since):
    runs = get_paginated(
        f"{GITHUB_API}/repos/{REPO}/actions/runs",
        {
            "branch": "main",
            "event": "push",
            "created": f">={since.strftime('%Y-%m-%dT%H:%M:%SZ')}",
        },
        "workflow_runs",
    )
    deploy_jobs = []
    for run in runs:
        jobs = get_paginated(
            f"{GITHUB_API}/repos/{REPO}/actions/runs/{run['id']}/jobs",
            {},
            "jobs",
        )
        for job in jobs:
            if job["name"] == DEPLOY_JOB_NAME and job["conclusion"] in ("success", "failure"):
                # On reutilise les memes champs que dans la version GitLab,
                # pour ne pas toucher a compute_metrics().
                job["status"] = "success" if job["conclusion"] == "success" else "failed"
                job["finished_at"] = job["completed_at"]
                job["pipeline_created_at"] = run["created_at"]
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
