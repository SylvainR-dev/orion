# Architecture — MicroCRM (Orion)

Ce document décrit l'architecture globale du projet MicroCRM : architecture applicative (backend/frontend), architecture des conteneurs, architecture de l'infrastructure Cloud (IaC), architecture du pipeline CI/CD et architecture de supervision. Il complète `testing-back.md`, `testing-front.md`, `PERF-back.md`, `PERF-front.md` et `SECURITY.md`.

## 1. Vue d'ensemble

```
┌─────────────┐        HTTPS         ┌──────────────────────┐
│  Navigateur │ ───────────────────▶ │  Frontend (Angular)  │
│  utilisateur│                      │  servi par Caddy :80 │
└─────────────┘                      └──────────┬───────────┘
                                                  │ /api/persons
                                                  │ /api/organizations
                                                  ▼
                                      ┌──────────────────────┐
                                      │  Backend (Spring     │
                                      │  Boot / Java)        │
                                      └──────────┬───────────┘
                                                  │ JPA
                                                  ▼
                                      ┌──────────────────────┐
                                      │  HyperSQL (embarqué) │
                                      └──────────────────────┘
```

L'ensemble tourne sous forme de deux Pods Kubernetes distincts (frontend, backend) sur un cluster **Amazon EKS**, provisionné par Terraform, configuré par Ansible et déployé par Helm. Le pipeline **GitLab CI/CD** orchestre l'intégralité du cycle : build, qualité, sécurité, publication, infrastructure, déploiement, supervision.

## 2. Architecture applicative — Backend

Le backend suit une architecture en couches classique, remplaçant l'exposition directe des entités JPA (Spring Data REST) mise en place initialement :

```
Client HTTP
   │
   ▼
Controller (REST, DTOs)
   │
   ▼
Service (logique métier, exceptions)
   │
   ▼
Repository (Spring Data JPA)
   │
   ▼
HyperSQL (embarqué)
```

- **Packages dédiés** par domaine métier : `person/`, `organization/` — chacun regroupant entité, repository, service, controller et DTOs.
- **DTOs en `record` Java** (`*Request`/`*Response`), validés via Jakarta Validation (`@NotBlank`, `@Email`), découplant le contrat d'API du schéma de persistance.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`) centralise la traduction des exceptions métier (`PersonNotFoundException`, `EmailAlreadyUsedException`, `OrganizationNotFoundException`) en réponses HTTP normalisées (404/409/400).
- **`CorsConfig`** remplace l'ancienne `SpringDataRestCustomization`, avec une origine restreinte et configurable.
- **Modèle de données** : relation `@ManyToMany` bidirectionnelle entre `Person` et `Organization`, avec cascade limitée à `{PERSIST, MERGE}` (corrigée depuis `ALL`, qui provoquait des suppressions en cascade non désirées).

## 3. Architecture applicative — Frontend

- **Angular standalone components** (pas de `NgModule` central), consommant une API REST classique côté backend (`/api/persons`, `/api/organizations`), après migration hors du format HAL/Spring Data REST.
- La **relation personne ↔ organisation** est reconstruite **côté client, par filtrage**, plutôt que d'ajouter un endpoint backend dédié — un choix délibéré pour limiter les changements côté API.
- Servi en production par **Caddy** sur le port `:80` (auto-HTTPS désactivé, le TLS étant terminé au niveau du Load Balancer AWS).

## 4. Architecture des conteneurs

Un **unique `Dockerfile` multi-stage** à la racine du dépôt produit les 3 artefacts de déploiement de l'application :

```
front-build ──▶ front        (image Caddy + build Angular statique)
back-build  ──▶ back         (image JRE + jar Spring Boot)
            └─▶ standalone   (front + back dans une seule image)
```

- Un `docker-compose.yml` permet de valider localement le fonctionnement conjoint front + back, sans dépendance à AWS.
- Les 3 images sont publiées vers le **GitLab Container Registry** (conservé depuis la Partie 1, pas de bascule vers Amazon ECR), taguées avec le numéro de version (semantic-release) et `latest`.

## 5. Architecture de l'infrastructure Cloud (IaC)

Trois outils se partagent des responsabilités strictement séparées :

| Outil | Rôle | Ce qu'il gère |
|---|---|---|
| **Terraform** | Provisioning | VPC (2 AZ, sous-réseaux publics/privés, 1 NAT Gateway), cluster EKS 1.30, node group managé (t3.small, min 1 / desired 2 / max 3) |
| **Ansible** | Configuration day-2 | Namespace `orion`, secret `imagePullSecret` (registre GitLab), StorageClass EBS CSI (IRSA) |
| **Helm** | Déploiement applicatif | Chart `orion` (front/back), + charts tiers `kube-prometheus-stack`, `loki-stack`, `prometheus-pushgateway` |

```
Terraform (infra AWS brute)
      │
      ▼
Ansible (namespace + secrets + storageclass, day-2 config)
      │
      ▼
Helm (déploiement applicatif + supervision)
```

- **State Terraform** centralisé sur un backend **S3 distant** (`orion-terraform-state-<account-id>`, versionné), avec verrouillage natif `use_lockfile` (Terraform ≥ 1.10) plutôt qu'une table DynamoDB dédiée — simplification pédagogique assumée.
- **Namespace et secret volontairement exclus du chart Helm** applicatif, pour éviter un conflit d'ownership avec les ressources gérées par Ansible.
- **Pratique de travail** : l'infrastructure est détruite chaque soir (`terraform destroy`) et recréée chaque matin (`terraform apply`), pour limiter la consommation du quota AWS Free Tier — rendu possible uniquement par la centralisation du state sur S3.

## 6. Architecture du pipeline CI/CD

```
push / merge request sur GitLab
        │
        ▼
  version (semantic-release : tag + changelog + release)
        │
        ▼
  test-front / test-back (Karma / JUnit + JaCoCo/lcov)
        │
        ▼
  sonarqube-front / sonarqube-back (Quality Gate bloquante)
        │
        ▼
  build-front / build-back (compilation applicative)
        │
        ▼
  publish-images (build Docker × 3 + scan Trivy + push registre GitLab)
        │
        ▼
  terraform-plan (auto, main) ──▶ terraform-apply (manuel)
        │
        ▼
  ansible-bootstrap (auto, idempotent)
        │
        ▼
  deploy (Helm upgrade --install --rollback-on-failure)
        │
        ▼
  monitoring-deploy (stack PLG) + dora-metrics (schedule quotidien 6h)
```

- Un bloc `workflow:` global limite le déclenchement aux pipelines de merge request ou aux push sur `main`, pour éviter le doublon de pipelines observé en cours de projet.
- `terraform-plan`/`terraform-apply` sont exclus des pipelines de merge request (les credentials AWS, en variables protégées, ne sont disponibles que sur `main`).
- `terraform-apply` reste **manuel** (`when: manual`) par choix délibéré : garde-fou humain avant toute modification facturée de l'infrastructure AWS.

### 6.1. Mécanisme de rollback applicatif

Le déploiement applicatif (job `deploy`) s'appuie sur le flag Helm `--rollback-on-failure` (remplaçant `--atomic`, deprecated) :

```yaml
helm upgrade --install orion helm/orion/ --namespace orion
    --set backend.tag="$VERSION" --set frontend.tag="$VERSION"
    --rollback-on-failure --timeout 5m
```

Si les pods de la nouvelle révision ne deviennent pas `Ready` dans le délai imparti (image inexistante, crash au démarrage, etc.), Helm **annule automatiquement la révision en échec et restaure la dernière révision stable**, sans intervention manuelle.

Ce mécanisme a été **testé en conditions réelles** sur le cluster EKS, en déployant volontairement un tag d'image inexistant (`version-inexistante-9999`). Résultat observé via `helm history orion -n orion` :

```
REVISION  STATUS      DESCRIPTION
1         superseded  Install complete
2         failed      Upgrade "orion" failed: context canceled
3         deployed    Rollback to 1
```

La révision 3 confirme le retour automatique à la révision 1 (dernière version stable), avec les pods `orion-backend`/`orion-frontend` de nouveau `Running` sur le tag d'image correct.

Ce mécanisme remplace le rollback Ansible/health-check mis en place en Partie 1 (environnement de démo simulé, SSH + Docker), abandonné avec la bascule vers Kubernetes/Helm en Partie 2 — le rollback est désormais nativement géré par Helm plutôt que scripté manuellement.

## 7. Architecture de supervision (monitoring)

```
┌─────────────────────────── namespace monitoring ───────────────────────────┐
│                                                                              │
│   Prometheus  ◀── scrape ──  kube-state-metrics / node-exporter / pods     │
│       │                                                                     │
│       ├── Pushgateway ◀── push ── scripts/dora_metrics.py (API GitLab)     │
│       │                                                                     │
│   Loki  ◀── logs ── promtail (via loki-stack)                              │
│       │                                                                     │
│   Grafana  ── dashboards ── « Orion - DORA Metrics »                       │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **PLG plutôt qu'ELK** : Loki a été retenu à la place d'Elasticsearch pour sa légèreté sur des nœuds contraints en RAM (Free Tier), la formulation « ELK ou équivalent » ayant été validée en amont par Maria.
- **Prometheus Pushgateway** avec `ServiceMonitor` labellisé `release: kube-prometheus-stack`, indispensable pour être scrapé par l'opérateur Prometheus.
- Les deux charts (`kube-prometheus-stack` et `loki-stack`) génèrent chacun une ConfigMap de datasource Grafana par défaut ; le sidecar de datasources du chart `loki-stack` est désactivé (`grafana.sidecar.datasources.enabled: false`) et Loki est déclaré manuellement comme source additionnelle non par défaut, pour éviter le conflit.

## 8. Décisions d'architecture clés

| Décision | Justification |
|---|---|
| Trunk-based development léger (branche `main` protégée + branches courtes) | Taille d'équipe réduite (fictive), complexité d'un Git Flow complet non justifiée |
| Refonte Spring Data REST → architecture en couches | Aucune logique métier possible dans l'architecture initiale, CORS dangereux, pas de DTO |
| PLG (Prometheus/Loki/Grafana) plutôt qu'ELK | Contrainte RAM des nœuds Free Tier, autorisé explicitement par le brief |
| Séparation stricte Terraform / Ansible / Helm | Évite les conflits d'ownership, responsabilité claire par outil |
| Verrouillage S3 natif plutôt que DynamoDB | Simplification pédagogique assumée (Terraform ≥ 1.10 uniquement) |
| `terraform-apply` manuel | Garde-fou humain avant toute modification facturée de l'infrastructure |
| Registre GitLab Container Registry conservé (pas d'ECR) | Évite un composant d'infrastructure supplémentaire à gérer |
| Rollback applicatif géré nativement par Helm (`--rollback-on-failure`) | Testé et validé en conditions réelles sur EKS (déploiement d'un tag inexistant, retour automatique confirmé via `helm history`) |

## 9. Limites d'architecture connues

Cette architecture porte plusieurs simplifications assumées pour un contexte pédagogique, déjà détaillées dans `SECURITY.md` et `PERF-back.md`/`PERF-front.md` :

- Endpoint API EKS public, sans VPN/bastion.
- Utilisateur IAM `orion-terraform` avec `AdministratorAccess`.
- HyperSQL embarqué, non représentatif d'une base de production.
- Absence de Spring Boot Actuator (pas de métriques applicatives, probes en `tcpSocket`).
- Pas d'autoscaling applicatif (HPA) ni de NetworkPolicy.

Ces points sont documentés comme dette technique explicite plutôt que comme oublis, et constituent des points de discussion attendus en soutenance.