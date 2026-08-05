# Orion — MicroCRM

Projet DevOps réalisé dans le cadre de la formation OpenClassrooms Expert DevOps (Projet 6 — Option B). Application Full-Stack MicroCRM (gestion de personnes et d'organisations) pour l'entreprise fictive **Orion**, avec une chaîne CI/CD complète et une infrastructure Cloud provisionnée par le code.

## Sommaire

- [Aperçu de l'architecture](#aperçu-de-larchitecture)
- [Structure du dépôt](#structure-du-dépôt)
- [Stack technique](#stack-technique)
- [Prérequis](#prérequis)
- [Déploiement de l'infrastructure — pas à pas](#déploiement-de-linfrastructure--pas-à-pas)
- [Déploiement applicatif](#déploiement-applicatif)
- [Déploiement de la supervision (monitoring)](#déploiement-de-la-supervision-monitoring)
- [Variables CI/CD requises](#variables-cicd-requises)
- [Destruction de l'infrastructure](#destruction-de-linfrastructure)
- [Pipeline CI/CD](#pipeline-cicd)
- [Sauvegarde et rollback](#sauvegarde-et-rollback)
- [Documentation complète](#documentation-complète)

## Aperçu de l'architecture

```
Navigateur → Frontend (Angular / Caddy) → Backend (Spring Boot) → HyperSQL (embarquée)
```

Déployée sur **Amazon EKS** (Kubernetes managé), provisionnée par **Terraform**, configurée par **Ansible**, déployée par **Helm**, avec une chaîne CI/CD complète sur **GitLab CI**.

Schéma détaillé, décisions d'architecture et justifications : voir [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Structure du dépôt

```
.
├── back/                   # API Spring Boot (voir back/README.md)
├── front/                  # Application Angular (voir front/README.md)
├── terraform/              # Provisioning de l'infrastructure AWS (VPC, EKS, node group, IAM)
├── ansible/                # Configuration "day-2" du cluster (namespace, secrets, StorageClass)
├── helm/
│   ├── orion/               # Chart applicatif (front/back)
│   └── monitoring/          # Values Helm pour la stack PLG (Prometheus/Loki/Grafana) + dashboards exportés
├── scripts/                # Scripts d'automatisation (sauvegarde, métriques DORA)
├── Dockerfile               # Multi-stage unique (targets front / back / standalone)
├── docker-compose.yml       # Test local front + back, sans dépendance AWS
├── .gitlab-ci.yml           # Pipeline CI/CD complet
├── ARCHITECTURE.md
├── SECURITY.md
├── BACKUP.md
└── (documents complémentaires — voir section Documentation complète)
```

## Stack technique

| Catégorie | Outil |
|---|---|
| Backend | Spring Boot, Gradle, HyperSQL |
| Frontend | Angular, Karma |
| Conteneurisation | Docker (multi-stage), GitLab Container Registry |
| Provisioning infra | Terraform (≥ 1.15), modules `terraform-aws-modules/vpc`, `terraform-aws-modules/eks` |
| Configuration cluster | Ansible (collection `kubernetes.core`) |
| Déploiement applicatif | Helm 3 |
| Orchestration | Amazon EKS (Kubernetes) |
| CI/CD | GitLab CI, semantic-release |
| Qualité / Sécurité | SonarCloud, Trivy |
| Supervision | Prometheus, Loki, Grafana, Prometheus Pushgateway |

## Prérequis

Pour déployer l'infrastructure et l'application depuis un poste local (en plus de ce qui est nécessaire pour le développement applicatif, voir `back/README.md` et `front/README.md`) :

- **AWS CLI** configuré avec des identifiants disposant des permissions nécessaires (voir `SECURITY.md` pour le détail de la policy IAM utilisée)
- **Terraform** ≥ 1.15
- **Ansible** + collection `kubernetes.core` (`ansible-galaxy collection install -r ansible/requirements.yml`)
- **kubectl**
- **Helm** 3
- **Docker** (pour builder les images en local si besoin)

## Déploiement de l'infrastructure — pas à pas

L'ordre est important : chaque étape dépend de la précédente.

### 1. Provisionner l'infrastructure AWS (Terraform)

```bash
cd terraform
terraform init
terraform plan
terraform apply
```

Ceci provisionne le VPC, le cluster EKS et son node group managé. Le state est stocké dans un backend S3 distant (`orion-terraform-state-476646938402`, clé `eks/terraform.tfstate`), avec versioning et verrouillage natif (`use_lockfile`) — aucune configuration locale supplémentaire nécessaire au-delà des credentials AWS.

> **Coût** : ce projet fonctionne en cycle destroy/recreate pour rester dans les limites du Free Tier AWS. Voir [Destruction de l'infrastructure](#destruction-de-linfrastructure).

### 2. Configurer le cluster (Ansible)

```bash
aws eks update-kubeconfig --region eu-west-3 --name orion-eks

cd ansible
ansible-playbook -i inventory.ini bootstrap-k8s.yml \
  -e "registry_username=<deploy-token-username>" \
  -e "registry_password=<deploy-token-password>"
```

Ce playbook (idempotent, rejouable sans risque) crée :
- le namespace applicatif `orion`
- le secret `gitlab-registry-secret` (authentification au registre d'images GitLab)
- la StorageClass `ebs-gp3` (persistance des volumes, utilisée par la stack de supervision)

### 3. Déployer l'application (Helm)

```bash
helm upgrade --install orion helm/orion/ --namespace orion \
  --set backend.tag="<version>" --set frontend.tag="<version>" \
  --atomic --timeout 5m
```

Remplacer `<version>` par le tag d'image souhaité (voir les tags disponibles sur le registre GitLab, ou `latest`).

Le flag `--atomic` garantit un retour automatique à la version stable précédente en cas d'échec du déploiement — voir [`ARCHITECTURE.md` §6.1](./ARCHITECTURE.md#61-mécanisme-de-rollback-applicatif) pour la validation de ce mécanisme en conditions réelles.

## Déploiement applicatif

Voir l'étape 3 ci-dessus. Pour une itération applicative sans reprovisioning infra, cette seule commande suffit (namespace et secrets déjà en place via Ansible).

## Déploiement de la supervision (monitoring)

```bash
aws eks update-kubeconfig --region eu-west-3 --name orion-eks

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install loki-stack grafana/loki-stack \
  --namespace monitoring -f helm/monitoring/loki-stack-values.yaml --wait --timeout 10m

helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring -f helm/monitoring/kube-prometheus-stack-values.yaml \
  --set grafana.adminPassword=$GRAFANA_ADMIN_PASSWORD --wait --timeout 10m

helm upgrade --install prometheus-pushgateway prometheus-community/prometheus-pushgateway \
  --namespace monitoring -f helm/monitoring/pushgateway-values.yaml --wait --timeout 5m
```

> **Ordre important** : `loki-stack` doit être déployé avant `kube-prometheus-stack` pour éviter un conflit de ConfigMap de datasource Grafana par défaut (voir `ARCHITECTURE.md` §7 pour le détail du correctif appliqué).

Les dashboards Grafana custom sont automatiquement réinjectés au démarrage via des ConfigMaps labellisés (`grafana_dashboard=1`), générés à partir des fichiers `helm/monitoring/dashboards/*.json`.

## Variables CI/CD requises

À configurer dans **Settings → CI/CD → Variables** du projet GitLab avant toute exécution du pipeline complet :

| Variable | Protégée | Masquée | Usage |
|---|---|---|---|
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | ✅ | ✅ | Authentification Terraform/AWS (utilisateur `orion-terraform`, policy scopée — voir `SECURITY.md`) |
| `GITLAB_TOKEN` | ✅ | ✅ | semantic-release (scope `api`) |
| `SONAR_TOKEN` / `SONAR_HOST_URL` | ❌ (volontairement) | ✅ | Analyse SonarCloud, doit fonctionner aussi sur les merge requests |
| `REGISTRY_USERNAME` / `REGISTRY_PASSWORD` | — | ✅ | Deploy token GitLab (scope `read_registry`), consommé par Ansible |
| `GRAFANA_ADMIN_PASSWORD` | ✅ | ✅ | Mot de passe administrateur Grafana |
| `GIT_PUSH_TOKEN` | ✅ | ✅ | Personal Access Token (scope `write_repository`), utilisé par le job `export-dashboards` pour committer automatiquement |
| `DORA_GITLAB_TOKEN` | — | ✅ | Script de calcul des métriques DORA (scope `read_api`) |

## Destruction de l'infrastructure

Pratique de travail du projet : l'infrastructure est détruite en fin de session et recréée au besoin, pour limiter la consommation du quota AWS Free Tier.

```bash
cd terraform
terraform destroy
```

Avant toute opération jugée risquée, un snapshot explicite du state peut être créé :

```bash
./scripts/backup-terraform-state.sh
```

Voir [`BACKUP.md`](./BACKUP.md) pour le détail de la stratégie de sauvegarde.

## Pipeline CI/CD

Le pipeline complet (`.gitlab-ci.yml`) enchaîne automatiquement : versioning sémantique → tests → qualité (SonarCloud) → build → publication des images (avec scan Trivy) → provisioning infra (`terraform-plan` auto, `terraform-apply` manuel) → configuration cluster (Ansible) → déploiement applicatif (Helm) → supervision.

Schéma complet et détail de chaque job : voir [`ARCHITECTURE.md` §6](./ARCHITECTURE.md#6-architecture-du-pipeline-cicd).

## Sauvegarde et rollback

- **Sauvegarde** : state Terraform (versioning S3 + snapshots explicites) et dashboards Grafana (export automatisé). Détail et justification des exclusions : [`BACKUP.md`](./BACKUP.md).
- **Rollback** : automatique au niveau applicatif via Helm (`--rollback-on-failure`), testé en conditions réelles. Détail : [`ARCHITECTURE.md` §6.1](./ARCHITECTURE.md#61-mécanisme-de-rollback-applicatif).

## Documentation complète

| Document | Contenu |
|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Architecture applicative, infrastructure, pipeline, supervision, décisions techniques |
| [`SECURITY.md`](./SECURITY.md) | Sécurité applicative, gestion des secrets, dette de sécurité, policy IAM |
| [`BACKUP.md`](./BACKUP.md) | Stratégie de sauvegarde et de restauration |
| [`back/README.md`](./back/README.md) | Backend — installation, lancement, tests, endpoints |
| [`back/TESTING.md`](./back/TESTING.md) | Backend — stratégie de tests et couverture |
| [`back/PERF-back.md`](./back/PERF-back.md) | Backend — performance et dimensionnement |
| [`front/README.md`](./front/README.md) | Frontend — installation, lancement, tests |
| [`front/TESTING.md`](./front/TESTING.md) | Frontend — stratégie de tests et couverture |
| [`front/PERF.md`](./front/PERF.md) | Frontend — performance et optimisations de build |

## Contexte du projet

Développé dans le cadre du Projet 6 (Option B — Gérez une démarche DevOps) de la formation Expert DevOps d'OpenClassrooms, pour l'entreprise fictive Orion sous la direction de la CTO fictive Maria. Le dépôt et son historique de commits suivent une démarche trunk-based avec Conventional Commits et semantic-release.