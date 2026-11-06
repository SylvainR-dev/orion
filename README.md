# Orion — MicroCRM

## Historique et évolution

Ce dépôt est né comme un exercice de certification : le Projet 6 (Option B) de la formation Expert DevOps d'OpenClassrooms. Le scénario voulait que je sois expert DevOps chez Orion, une entreprise fictive dont la CTO, Maria, me demandait d'industrialiser une application full-stack, MicroCRM (un frontend Angular et un backend Spring Boot). Au départ, presque tout était manuel : les images Docker étaient construites à la main, envoyées par e-mail à l'équipe Ops, puis scannées trop tard, ce qui avait déjà retardé un premier déploiement.

**Partie 1, un pipeline d'intégration continue.** J'ai commencé par auditer le processus existant, puis j'ai construit une chaîne sur GitLab CI : versionnement sémantique, tests, analyse SonarCloud, scan Trivy placé juste après la construction des images (au lieu de la fin de chaîne), et publication dans un registre interne. Pour le déploiement, je voulais une vraie machine virtuelle, mais VirtualBox n'a pas voulu se compiler sur mon poste. J'ai donc simulé un serveur dans un conteneur Docker (Debian, SSH, Docker imbriqué) sur lequel Ansible déployait, avec un contrôle de santé et un retour automatique à la version précédente.

**Partie 2, une vraie infrastructure cloud.** J'ai ensuite remplacé ce serveur simulé par un cluster Amazon EKS provisionné avec Terraform, configuré avec Ansible et déployé avec Helm, avec une supervision (Prometheus, Loki, Grafana) et des métriques DORA. En parallèle, j'ai refondu le backend : il exposait directement ses entités avec Spring Data REST, sans aucune couche de logique métier ni validation. Je l'ai remplacé par une architecture Controller / Service / Repository avec des DTO, et la couverture de tests est passée de 0 % à 94 %.

**Réduire le coût.** La formation validée, je voulais garder le projet comme laboratoire personnel. Mais le plan de contrôle d'EKS (environ 73 $ par mois) plus la NAT Gateway coûtaient trop cher pour cet usage. J'ai donc remplacé le cluster par une seule instance EC2 qui exécute k3s, une distribution Kubernetes légère : l'API Kubernetes est la même, donc j'ai gardé mes charts Helm.

**Séparer et passer sur GitHub.** L'instance qui tournait en continu me coûtait encore trop cher. J'ai donc créé une instance dédiée à Orion, avec son propre état Terraform, que j'éteins à la fin de chaque session (`make down`) et que je rallume quand je travaille (`make up`). J'ai aussi déplacé le projet de GitLab vers GitHub, pour n'avoir qu'un seul dépôt de référence et parce que GitHub Actions est l'outil de CI/CD sur lequel je voulais m'entraîner. Je tenais à avoir un vrai déploiement continu dans Actions. Mais l'API de k3s n'est pas exposée sur Internet et l'instance est souvent éteinte : j'ai donc choisi que le job de déploiement s'authentifie auprès d'AWS par OIDC (aucune clé stockée dans GitHub) et envoie ses commandes à l'instance par AWS Systems Manager, après une approbation manuelle. J'ai enfin ajouté des contrôles DevSecOps : détection de secrets (Gitleaks) et scan de la configuration Helm et Terraform (Trivy).

Chaque document de ce dépôt contient sa propre section « Historique et évolution », propre à son sujet.

## Sommaire

- [Aperçu de l'architecture](#aperçu-de-larchitecture)
- [Structure du dépôt](#structure-du-dépôt)
- [Stack technique](#stack-technique)
- [Prérequis](#prérequis)
- [Utilisation au quotidien](#utilisation-au-quotidien)
- [Créer l'infrastructure de zéro](#créer-linfrastructure-de-zéro)
- [Pipeline CI/CD](#pipeline-cicd)
- [Secrets et accès](#secrets-et-accès)
- [Sauvegarde et rollback](#sauvegarde-et-rollback)
- [Limites connues](#limites-connues)
- [Documentation complète](#documentation-complète)

## Aperçu de l'architecture

```
Navigateur → Frontend (Angular / Caddy) → Backend (Spring Boot) → HyperSQL (embarquée)
```

L'application tourne dans deux pods Kubernetes (frontend, backend) sur une instance EC2 dédiée qui exécute k3s. L'infrastructure est provisionnée par **Terraform**, l'instance est configurée par **Ansible**, l'application et la supervision sont déployées par **Helm**, et le tout est livré par **GitHub Actions**. Je n'ouvre aucun port de l'instance à Internet, à part le SSH depuis mon adresse : j'accède à l'API de k3s par un tunnel SSH.

Schéma détaillé, décisions d'architecture et justifications : voir [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Structure du dépôt

```
.
├── back/                    # API Spring Boot (voir back/README.md)
├── front/                   # Application Angular (voir front/README.md)
├── terraform-orion/         # Instance EC2, groupe de sécurité, IAM (OIDC GitHub, SSM)
├── ansible/                 # bootstrap-k3s.yml (installe k3s), cd-tools.yml (agent SSM, Helm, git)
├── helm/
│   ├── orion/               # Chart applicatif (front/back)
│   └── monitoring/          # Values Helm de la stack Prometheus / Loki / Grafana + dashboards
├── scripts/                 # backup-terraform-state.sh, dora_metrics.py
├── .github/workflows/       # ci.yml : le pipeline CI/CD
├── Dockerfile               # Multi-stage unique (targets front / back / standalone)
├── docker-compose.yml       # Test local front + back, sans dépendance AWS
├── Makefile                 # Commandes du quotidien (up, down, deploy...)
├── .trivyignore             # Exception Trivy documentée (voir Limites connues)
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
| Conteneurisation | Docker (multi-stage), GitHub Container Registry (GHCR) |
| Infrastructure | Terraform (≥ 1.10), AWS EC2 |
| Configuration de l'instance | Ansible |
| Orchestration | k3s (Kubernetes léger, un seul nœud) |
| Déploiement applicatif | Helm |
| CI/CD | GitHub Actions, semantic-release |
| Qualité / Sécurité | SonarCloud, Trivy (images et configuration), Gitleaks |
| Déploiement sans clé | OIDC GitHub, AWS Systems Manager |
| Supervision | Prometheus, Loki, Grafana, Prometheus Pushgateway |

## Prérequis

- **AWS CLI** configuré avec des identifiants disposant des droits nécessaires (voir `SECURITY.md`)
- **Terraform** ≥ 1.10
- **Ansible**
- **kubectl** et **Helm**
- **Docker** (pour construire les images en local si besoin)
- **make** et **Python 3** (le script des métriques DORA tourne dans un environnement virtuel `.venv`)

## Utilisation au quotidien

L'instance est éteinte entre deux sessions de travail. Les commandes passent par le `Makefile` :

| Commande | Effet |
|---|---|
| `make up` | Rallume l'instance, attend le SSH et ouvre le tunnel vers l'API de k3s |
| `make down` | Ferme le tunnel et éteint l'instance (seul le disque reste facturé) |
| `make status` | Affiche l'identifiant, l'état et l'adresse IP de l'instance |
| `make tunnel` | Rouvre le tunnel SSH (par exemple après une mise en veille du PC) |
| `make install` | Installe k3s sur l'instance (une seule fois) et récupère le kubeconfig |
| `make cd-tools` | Installe l'agent SSM, Helm et git sur l'instance (une seule fois) |
| `make deploy` | Déploie la dernière version publiée, ou `VERSION=x.y.z`, avec Helm |
| `make dora` | Calcule les métriques DORA depuis GitHub et les envoie au Pushgateway |

L'adresse IP publique change à chaque démarrage, puisque je n'utilise pas d'IP Elastic. Comme je passe par un tunnel SSH vers `127.0.0.1:6443`, le certificat de k3s reste valide et rien n'est à régénérer.

Une session type :

```bash
make up
export KUBECONFIG=$PWD/ansible/kubeconfig-orion.yaml   # à refaire dans chaque nouveau terminal
kubectl get nodes
# ... travail ...
make down
```

## Créer l'infrastructure de zéro

L'ordre est important : chaque étape dépend de la précédente.

### 1. Provisionner l'instance (Terraform)

```bash
cd terraform-orion
echo "admin_ip_cidr = \"$(curl -4 -s ifconfig.me)/32\"" > terraform.tfvars   # fichier ignoré par git
terraform init
terraform plan
terraform apply
```

Ceci crée l'instance (Debian 12, `t3.medium`, disque de 15 Gio), son groupe de sécurité (SSH uniquement, depuis mon adresse), et les ressources IAM du déploiement continu. L'état est stocké dans un backend S3 distant (`orion-terraform-state-<identifiant-de-compte>`, clé `orion-k3s/terraform.tfstate`), avec versioning et verrouillage natif (`use_lockfile`).

### 2. Installer k3s et les outils du CD (Ansible)

```bash
make install
make cd-tools
```

Le premier playbook installe k3s (Traefik et ServiceLB désactivés, car Orion n'a pas d'Ingress) et récupère le kubeconfig. Le second installe l'agent SSM, Helm et git, dont le job de déploiement a besoin. Les deux sont idempotents.

### 3. Déployer la supervision (Helm)

Le tunnel doit être ouvert (`make tunnel`) et `KUBECONFIG` exporté.

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update
kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install loki-stack grafana/loki-stack \
  --namespace monitoring -f helm/monitoring/loki-stack-values.yaml --wait --timeout 10m

read -s -p "Mot de passe admin Grafana : " GRAFANA_PW; echo
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring -f helm/monitoring/kube-prometheus-stack-values.yaml \
  --set-string grafana.adminPassword="$GRAFANA_PW" --wait --timeout 10m
unset GRAFANA_PW

helm upgrade --install prometheus-pushgateway prometheus-community/prometheus-pushgateway \
  --namespace monitoring -f helm/monitoring/pushgateway-values.yaml --wait --timeout 5m
```

> **Deux points d'attention.** `loki-stack` doit être déployé avant `kube-prometheus-stack`, pour éviter un conflit de datasource Grafana par défaut (détail dans `ARCHITECTURE.md`). Et j'utilise `--set-string` pour le mot de passe : avec `--set`, un mot de passe composé uniquement de chiffres est lu comme un nombre et fait échouer le chart.

Pour charger mon dashboard DORA (les autres dashboards sont déjà fournis par le chart) :

```bash
kubectl create configmap grafana-dashboard-orion-dora-metrics \
  --from-file=dashboard.json=helm/monitoring/dashboards/orion-dora-metrics.json \
  -n monitoring --dry-run=client -o yaml \
  | kubectl label --local -f - grafana_dashboard=1 -o yaml | kubectl apply -f -
```

### 4. Configurer GitHub

- **Settings → Actions → General → Workflow permissions** : *Read and write permissions* (nécessaire à semantic-release).
- **Settings → Environments** : créer l'environnement `production` avec moi comme relecteur obligatoire (c'est lui qui impose l'approbation avant tout déploiement).
- **Settings → Secrets and variables → Actions** : créer le secret `SONAR_TOKEN`.
- Rendre publics les packages GHCR `orion/front`, `orion/back` et `orion/standalone` après la première publication, pour que k3s puisse les télécharger sans secret.

## Pipeline CI/CD

Le pipeline (`.github/workflows/ci.yml`) s'exécute sur chaque Pull Request et sur chaque push vers `main`. Sur une Pull Request, seuls les contrôles tournent. Sur `main`, il va jusqu'au déploiement.

```
tests (Karma / JUnit + JaCoCo)  ─▶  SonarCloud (Quality Gate)
Gitleaks (secrets)              ─┤
Trivy config (Helm, Terraform)  ─┴▶  version (semantic-release)
                                          │
                                          ▼
                                   build front / back
                                          │
                                          ▼
                       publish-images (build × 3 + scan Trivy + push GHCR)
                                          │
                                          ▼
              deploy (approbation manuelle, OIDC, AWS SSM, helm upgrade --atomic)
```

J'ai placé la création de la version après les tests, l'analyse Sonar et les contrôles de sécurité : aucun tag, aucune image et aucun déploiement n'existent pour une version qui échoue à ces contrôles.

- **Qualité** : la Quality Gate de SonarCloud est bloquante, pour le backend et le frontend.
- **Secrets** : Gitleaks analyse les commits de la Pull Request.
- **Configuration** : Trivy scanne `helm/` et `terraform-orion/`. Cette première version est non bloquante (`EXIT_CODE: "0"`) : je lis le rapport, je corrige ou je justifie chaque constat, puis je passerai le contrôle en bloquant.
- **Images** : Trivy scanne chaque image juste après sa construction et bloque la publication en cas de faille critique.
- **Déploiement** : le job `deploy` utilise l'environnement `production` (approbation manuelle). Il s'authentifie à AWS par OIDC (rôle `orion-github-deploy`, limité à ce dépôt et à cet environnement), vérifie que l'instance tourne, puis envoie par SSM une commande qui clone la version taguée et lance `helm upgrade --atomic`. Aucun port n'est ouvert. Si l'instance est éteinte, le job échoue avec un message clair : je lance `make up`, puis **Re-run failed jobs**.

Schéma complet et détail de chaque job : voir [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Secrets et accès

- **GitHub** : un seul secret, `SONAR_TOKEN`. La CI ne détient aucune clé AWS : l'accès passe par OIDC.
- **Poste local** : les identifiants AWS (utilisateur `orion-terraform`) sont dans `~/.aws`, le kubeconfig (`ansible/kubeconfig-orion.yaml`) et `terraform.tfvars` sont ignorés par git.
- Détail de la politique d'accès et des droits IAM : voir [`SECURITY.md`](./SECURITY.md).

## Sauvegarde et rollback

- **Sauvegarde** : état Terraform (versioning S3 et snapshots explicites avec `./scripts/backup-terraform-state.sh`). Détail et justification des exclusions : [`BACKUP.md`](./BACKUP.md).
- **Rollback** : `helm upgrade --atomic` restaure automatiquement la révision précédente si les pods de la nouvelle version ne deviennent pas prêts. Détail : [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Limites connues

- **Le déploiement continu demande que l'instance soit allumée.** C'est un choix de coût : sinon le job échoue et je dois relancer après `make up`.
- **Une faille Spring ignorée volontairement.** CVE-2026-47884 (`XsltView`) n'est corrigée que dans Spring Framework 7, donc Spring Boot 4. Mon backend n'utilise aucune `XsltView`. Je l'ai donc ignorée dans `.trivyignore`, avec une justification et une date d'expiration (2027-02-04).
- **Image de base Alpine 3.19 en fin de vie** : Trivy signale que ses résultats peuvent être incomplets.
- **Métriques DORA** : l'API GitHub ne renvoie que la dernière tentative de chaque run, donc les échecs suivis d'un « Re-run » n'apparaissent pas. Comme le pipeline s'arrête à la publication des images, l'événement mesuré est cette publication, pas le déploiement.
- **Supervision déployée à la main** (Helm depuis mon poste), pas par la CI.
- **HyperSQL embarqué**, pas d'authentification applicative, pas de NetworkPolicy : voir `SECURITY.md`.

## Documentation complète

| Document | Contenu |
|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Architecture applicative, infrastructure, pipeline, supervision, décisions techniques |
| [`SECURITY.md`](./SECURITY.md) | Sécurité applicative, gestion des secrets, dette de sécurité, droits IAM |
| [`BACKUP.md`](./BACKUP.md) | Stratégie de sauvegarde et de restauration |
| [`back/README.md`](./back/README.md) | Backend — installation, lancement, tests, endpoints |
| [`back/TESTING.md`](./back/TESTING.md) | Backend — stratégie de tests et couverture |
| [`back/PERF-back.md`](./back/PERF-back.md) | Backend — performance et dimensionnement |
| [`front/README.md`](./front/README.md) | Frontend — installation, lancement, tests |
| [`front/TESTING.md`](./front/TESTING.md) | Frontend — stratégie de tests et couverture |
| [`front/PERF.md`](./front/PERF.md) | Frontend — performance et optimisations de build |
