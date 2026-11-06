# Architecture — MicroCRM (Orion)

## Historique et évolution

Quand j'ai commencé ce projet, il n'y avait pas vraiment d'architecture de déploiement : l'équipe Dev construisait des images Docker à la main, les envoyait par e-mail à l'équipe Ops, qui les scannait puis les lançait avec des commandes Docker. Mon premier travail a été de remplacer ces gestes manuels par une chaîne automatisée, et l'environnement cible a ensuite changé quatre fois.

**1. Un serveur de démonstration simulé.** J'ai d'abord déployé avec Ansible sur un faux serveur : un conteneur Debian avec SSH et Docker imbriqué. Je voulais une vraie machine virtuelle, mais VirtualBox ne se compilait pas sur mon poste. Ce choix était contraint, mais il m'a suffi pour valider la chaîne d'intégration continue.

**2. Un cluster Amazon EKS.** Pour avoir un environnement réaliste, j'ai provisionné avec Terraform un VPC sur deux zones, une NAT Gateway et un cluster EKS avec des nœuds managés. J'ai alors adopté le partage des rôles que je garde aujourd'hui : Terraform crée l'infrastructure, Ansible configure, Helm déploie. Cet environnement était réaliste, mais il avait un coût fixe élevé (environ 73 $ par mois rien que pour le plan de contrôle d'EKS, plus la NAT Gateway).

**3. Une seule instance EC2 avec k3s.** Une fois la formation validée, je voulais garder ce projet comme laboratoire. J'ai remplacé EKS par une instance EC2 qui exécute k3s, une distribution Kubernetes légère. J'ai choisi de rester sur Kubernetes plutôt que de passer à Docker Compose, pour garder mes charts Helm et le mécanisme de retour automatique. J'ai appris à mes dépens qu'une `t3.small` ne suffit pas : l'API de k3s expirait dès que la supervision était déployée, donc je suis passé à une `t3.medium` (4 Gio).

**4. Une instance dédiée que j'éteins.** Cette instance hébergeait aussi d'autres projets, donc je ne pouvais pas l'arrêter, et elle me coûtait trop cher. J'ai donc créé une instance réservée à Orion, avec son propre état Terraform (une clé différente dans le même bucket, pour ne jamais toucher à l'autre), sans IP fixe, avec un disque réduit à 15 Gio. Je l'allume et l'éteins avec `make up` et `make down`. Comme son adresse change à chaque démarrage, je n'expose plus l'API de k3s : j'y accède par un tunnel SSH vers `127.0.0.1:6443`, ce qui garde le certificat valide. Seul le port SSH est ouvert, uniquement depuis mon adresse.

**Le déploiement continu.** J'ai migré la CI de GitLab vers GitHub Actions, et je tenais à avoir un vrai déploiement continu dedans. Mais un runner GitHub ne peut pas joindre une API qui n'est pas exposée, sur une instance souvent éteinte. J'ai envisagé trois solutions : ouvrir le SSH à tout Internet (je l'ai écartée par sécurité), installer un runner auto-hébergé sur l'instance (GitHub le déconseille sur un dépôt public, et il consommerait de la mémoire), ou passer à une approche GitOps avec Flux (qui ne laisserait plus de job de déploiement dans Actions, alors que c'est ce que je voulais pratiquer). J'ai retenu AWS Systems Manager avec une authentification OIDC : le job `deploy` n'a aucune clé stockée dans GitHub, il endosse un rôle AWS limité à ce dépôt et à l'environnement `production`, puis envoie à l'instance une commande qui lance `helm upgrade --atomic`. Je l'ai fait passer après une approbation manuelle, et j'ai aussi réorganisé le pipeline pour que rien ne soit versionné, publié ni déployé avant la fin des tests, de l'analyse Sonar et des contrôles de sécurité.

**L'application.** En parallèle, j'ai refondu le backend. Il exposait directement ses entités avec Spring Data REST, sans couche de logique métier, sans validation et avec un CORS ouvert à tous. Je l'ai remplacé par des couches Controller / Service / Repository avec des DTO.

## 1. Vue d'ensemble

```
┌─────────────┐                      ┌──────────────────────┐
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

L'ensemble tourne dans deux pods Kubernetes distincts (frontend, backend), sur une instance EC2 dédiée qui exécute k3s. **Terraform** provisionne l'instance et les accès, **Ansible** la prépare, **Helm** déploie l'application et la supervision, et **GitHub Actions** orchestre la qualité, la sécurité, la publication et le déploiement.

## 2. Architecture applicative — Backend

Le backend suit une architecture en couches classique, qui remplace l'exposition directe des entités JPA (Spring Data REST) du début :

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

- **Packages par domaine** : `person/` et `organization/` regroupent chacun l'entité, le repository, le service, le contrôleur et les DTO.
- **DTO en `record` Java** (`*Request` / `*Response`), validés avec Jakarta Validation (`@NotBlank`, `@Email`), pour découpler le contrat d'API du schéma de persistance.
- **`GlobalExceptionHandler`** (`@RestControllerAdvice`) traduit les exceptions métier (`PersonNotFoundException`, `EmailAlreadyUsedException`, `OrganizationNotFoundException`) en réponses HTTP normalisées (404, 409, 400).
- **`CorsConfig`** remplace l'ancienne `SpringDataRestCustomization`, avec une origine restreinte et configurable.
- **Modèle de données** : relation `@ManyToMany` bidirectionnelle entre `Person` et `Organization`. La cascade est limitée à `{PERSIST, MERGE}` : avec `ALL`, supprimer une organisation supprimait aussi ses personnes. C'est un test d'intégration que je venais d'écrire qui m'a fait découvrir ce bug.
- **Version de Tomcat** : je fixe `ext['tomcat.version']` dans `back/build.gradle` (actuellement 10.1.59) pour corriger les failles critiques que Trivy détecte dans la version fournie par Spring Boot.

## 3. Architecture applicative — Frontend

- **Angular avec composants standalone** (pas de `NgModule` central), qui consomme une API REST classique (`/api/persons`, `/api/organizations`), après migration hors du format HAL de Spring Data REST.
- La **relation personne ↔ organisation** est reconstruite côté client par filtrage, plutôt que d'ajouter un endpoint côté backend : j'ai voulu limiter les changements d'API.
- Servi par **Caddy** sur le port 80, avec l'HTTPS automatique désactivé. Il n'y a ni Ingress ni répartiteur de charge devant l'application : je l'ouvre en local avec `kubectl port-forward`. Je n'ai pas encore traité ce point (voir la section 9).

## 4. Architecture des conteneurs

Un **`Dockerfile` multi-stage** unique, à la racine, produit les trois artefacts :

```
front-build ──▶ front        (image Caddy + build Angular statique)
back-build  ──▶ back         (image JRE + jar Spring Boot)
            └─▶ standalone   (front + back dans une seule image)
```

- `docker-compose.yml` permet de valider front et back ensemble en local, sans AWS.
- Les trois images sont publiées sur **GitHub Container Registry** (`ghcr.io/sylvainr-dev/orion/<front|back|standalone>`), taguées avec le numéro de version et `latest`. Le dépôt est public et les packages le sont aussi, donc l'instance les télécharge **sans aucun secret** : j'ai supprimé l'ancien `imagePullSecret`.

## 5. Architecture de l'infrastructure (IaC)

Trois outils, avec des responsabilités séparées :

| Outil | Rôle | Ce qu'il gère |
|---|---|---|
| **Terraform** (`terraform-orion/`) | Provisioning | Instance EC2 (Debian 12, `t3.medium`, disque gp3 de 15 Gio), groupe de sécurité (SSH uniquement, depuis mon adresse), ressources IAM du déploiement continu |
| **Ansible** (`ansible/`) | Configuration de l'instance | `bootstrap-k3s.yml` installe k3s (Traefik et ServiceLB désactivés, inutiles sans Ingress) ; `cd-tools.yml` installe l'agent SSM, Helm et git |
| **Helm** (`helm/`) | Déploiement | Chart `orion` (front et back), et les charts de supervision `kube-prometheus-stack`, `loki-stack`, `prometheus-pushgateway` |

```
Terraform (instance + IAM)
      │
      ▼
Ansible (k3s, agent SSM, Helm, git)
      │
      ▼
Helm (application + supervision)
```

- **État Terraform** : backend S3 distant, versionné, avec verrouillage natif `use_lockfile` (Terraform ≥ 1.10), plutôt qu'une table DynamoDB dédiée. J'ai choisi cette simplification pour ne pas ajouter un composant à gérer.
- **Isolation** : l'état d'Orion utilise la clé `orion-k3s/terraform.tfstate`, distincte de celle de mes autres instances, pour qu'une opération ici ne puisse jamais toucher une autre machine.
- **Terraform ne tourne pas dans Actions** : je l'exécute depuis mon poste, car il demande des droits IAM étendus que je ne veux pas confier à la CI.
- **Namespace** : Helm crée le namespace `orion` (`--create-namespace`). Ansible ne gère plus ni namespace ni secret, puisqu'il n'y a plus de registre privé.
- **Accès à l'API** : l'API de k3s n'est jamais exposée. Un tunnel SSH vers `127.0.0.1:6443` suffit, et le kubeconfig garde l'adresse `127.0.0.1`, donc il reste valide à chaque démarrage malgré l'IP qui change.

### Droits créés pour le déploiement continu (`terraform-orion/iam.tf`)

| Ressource | Rôle |
|---|---|
| Fournisseur OIDC `token.actions.githubusercontent.com` | Permet à AWS de faire confiance aux jetons émis par GitHub Actions |
| Rôle `orion-github-deploy` | Endossé par le job `deploy` uniquement : sa condition limite l'accès à ce dépôt et à l'environnement `production` |
| Politique du rôle | Envoyer une commande SSM (`ssm:SendCommand`) vers **cette instance seulement**, lire le résultat, et décrire les instances |
| Rôle et profil d'instance `orion-k3s-instance` | Permet à l'agent SSM de l'instance de s'enregistrer auprès d'AWS (politique gérée `AmazonSSMManagedInstanceCore`) |

## 6. Architecture du pipeline CI/CD

Le pipeline (`.github/workflows/ci.yml`) s'exécute sur chaque Pull Request et sur chaque push vers `main`. Sur une Pull Request, seuls les contrôles tournent ; les jobs `version`, `publish-images` et `deploy` sont réservés à `main`.

```
tests (Karma / JUnit + JaCoCo) ─▶ SonarCloud (Quality Gate)
Gitleaks (secrets)             ─┐
Trivy config (Helm, Terraform) ─┼─▶ version (semantic-release : tag + release)
SonarCloud                     ─┘            │
                                             ▼
                                   build-front / build-back
                                             │
                                             ▼
                      publish-images (build × 3 + scan Trivy + push GHCR)
                                             │
                                             ▼
        deploy (approbation manuelle, OIDC, AWS SSM, helm upgrade --atomic)
```

J'ai placé `version` **après** tous les contrôles : aucun tag, aucune image et aucun déploiement ne sont créés pour une version qui échoue aux tests, à Sonar ou aux contrôles de sécurité.

| Job | Rôle | Condition |
|---|---|---|
| `test-front`, `test-back` | Tests unitaires et d'intégration, rapports de couverture transmis à Sonar | Toujours |
| `sonarqube-front`, `sonarqube-back` | Analyse statique, Quality Gate bloquante | Après les tests |
| `gitleaks` | Détection de secrets dans les commits | Toujours |
| `trivy-config` | Scan de la configuration `helm/` et `terraform-orion/` | Toujours (non bloquant pour l'instant) |
| `version` | semantic-release : tag, release, journal des changements | Push sur `main`, après les contrôles |
| `build-front`, `build-back` | Compilation applicative | Après Sonar et `version` |
| `publish-images` | Construction des trois images, scan Trivy, publication sur GHCR | Push sur `main`, si une nouvelle version existe |
| `deploy` | Déploiement sur l'instance k3s | Push sur `main`, environnement `production` |

Quelques points de fonctionnement :

- **Images** : Trivy scanne chaque image juste après sa construction et bloque la publication en cas de faille **critique**. Pour le back et le standalone, une exception documentée (`.trivyignore`) couvre CVE-2026-47884, avec une date d'expiration.
- **Déploiement** : le job `deploy` attend mon approbation (environnement `production`). Il s'authentifie à AWS par OIDC, vérifie que l'instance `orion-k3s` tourne, puis envoie par SSM une commande qui clone la version taguée et lance `helm upgrade --atomic`. Il attend la fin de la commande et affiche ses logs. Si l'instance est éteinte, il échoue avec un message clair : je lance `make up`, puis **Re-run failed jobs**.
- **Versions** : semantic-release déduit la version des messages de commit (Conventional Commits). Un commit `docs`, `chore` ou `ci` ne crée aucune version, donc rien n'est publié ni déployé.
- **Limite connue** : le tag est créé avant la construction des images. Si le build ou la publication échoue ensuite, une version existe sans image, comme cela m'est arrivé pour la 1.19.0 (un premier envoi refusé par GHCR, résolu en relançant le job).

### 6.1. Mécanisme de retour arrière applicatif

Le déploiement utilise le drapeau Helm `--atomic` :

```bash
helm upgrade --install orion helm/orion --namespace orion --create-namespace \
  --set backend.tag="$VERSION" --set frontend.tag="$VERSION" \
  --atomic --timeout 5m
```

Si les pods de la nouvelle révision ne deviennent pas prêts dans le délai, Helm **annule la révision en échec et restaure la dernière révision stable**, sans intervention.

Je l'ai validé en conditions réelles sur EKS, en déployant volontairement un tag d'image inexistant. `helm history` montrait la révision en échec, puis une révision « Rollback to 1 » qui rétablissait la version précédente. Je n'ai pas rejoué ce test sur k3s, où le mécanisme est le même.

Ce mécanisme remplace le retour arrière que j'avais scripté en Partie 1 avec Ansible (contrôle de santé, puis restauration de la version précédente) : Helm le fait nativement.

## 7. Architecture de supervision

```
┌─────────────────────────── namespace monitoring ───────────────────────────┐
│                                                                             │
│   Prometheus  ◀── scrape ──  kube-state-metrics / node-exporter / pods     │
│       │                                                                     │
│       ├── Pushgateway ◀── push ── scripts/dora_metrics.py (API GitHub)     │
│       │                                                                     │
│   Loki  ◀── logs ── promtail (via loki-stack)                              │
│       │                                                                     │
│   Grafana  ── dashboards ── « Orion - DORA Metrics »                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Prometheus, Loki et Grafana plutôt qu'ELK** : Loki est bien plus léger qu'Elasticsearch, ce qui compte sur une instance de 4 Gio de mémoire. Maria avait validé la formule « ELK ou équivalent ».
- **Conflit de datasource Grafana** : les charts `kube-prometheus-stack` et `loki-stack` créent chacun une datasource par défaut, et Grafana refuse de démarrer avec deux. J'ai désactivé celle de `loki-stack` (`grafana.sidecar.datasources.enabled: false`) et déclaré Loki comme source additionnelle non par défaut. Je déploie `loki-stack` en premier.
- **Limites de ressources de Grafana** : celles que j'avais fixées pour une `t3.small` (192 Mio de mémoire, 200 m de CPU) empêchaient la version 13 de démarrer, le contrôle de santé le tuait avant la fin du démarrage. Je les ai relevées à 256 Mio / 100 m (demande) et 512 Mio / 1 CPU (limite).
- **Déploiement de la supervision** : je le fais avec Helm depuis mon poste (voir `README.md`), pas via la CI.
- **Métriques DORA** : `scripts/dora_metrics.py` interroge l'API GitHub Actions et envoie les valeurs au Pushgateway, que Prometheus collecte. L'événement mesuré est la publication des images (`publish-images`), puisque le déploiement a lieu après une approbation manuelle. L'API ne renvoie que la dernière tentative de chaque run : un échec suivi d'un « Re-run » n'apparaît donc pas dans le taux d'échec.

## 8. Décisions d'architecture clés

| Décision | Justification |
|---|---|
| k3s sur une instance EC2 plutôt qu'EKS | EKS coûtait environ 73 $/mois de plan de contrôle plus la NAT Gateway ; k3s garde l'API Kubernetes et mes charts Helm |
| Kubernetes plutôt que Docker Compose | Je conserve Helm, les probes et le retour arrière automatique |
| Instance dédiée à Orion, éteinte entre deux sessions | L'instance qui tournait en continu coûtait trop cher ; seul le disque reste facturé quand elle est arrêtée |
| API k3s accessible uniquement par tunnel SSH | Rien n'est exposé à Internet, et l'adresse IP qui change ne casse plus le certificat |
| Déploiement par AWS SSM avec OIDC | Un runner GitHub ne peut pas joindre une API non exposée ; aucune clé stockée dans GitHub, aucun port ouvert |
| Approbation manuelle avant le déploiement | Garde-fou humain, et rôle IAM limité à l'environnement `production` |
| `version` après les tests, Sonar et la sécurité | Aucun tag, aucune image ni aucun déploiement pour une version qui échoue aux contrôles |
| GHCR public, sans secret de téléchargement | Dépôt public : moins de secrets à gérer |
| Terraform lancé depuis mon poste, pas depuis Actions | Il demande des droits IAM étendus que je ne veux pas confier à la CI |
| Verrouillage S3 natif plutôt que DynamoDB | Moins de composants à gérer (Terraform ≥ 1.10 uniquement) |
| Trivy sur les images et sur la configuration | Les failles de la configuration (Helm, Terraform) échappent au scan d'images |
| Prometheus / Loki / Grafana plutôt qu'ELK | Légèreté sur une instance de 4 Gio |
| Retour arrière géré par Helm (`--atomic`) | Natif, testé en conditions réelles sur EKS |
| Refonte Spring Data REST → couches Controller / Service / Repository | Aucune logique métier possible dans l'architecture initiale, CORS dangereux, pas de DTO |
| Trunk-based léger (branche `main` + branches courtes + Pull Requests) | Équipe réduite, un Git Flow complet n'était pas justifié |

## 9. Limites d'architecture connues

Ce sont des choix de simplification, pas des oublis :

- **Le déploiement continu exige que l'instance soit allumée.** Sinon le job échoue avec un message clair, et je relance après `make up`.
- **Pas d'Ingress ni de répartiteur de charge** : j'ouvre l'application avec `kubectl port-forward`. Le frontend appelle le backend directement, donc il faut deux port-forward (front et back). Je n'ai pas encore routé `/api` sous le même domaine.
- **Un seul nœud**, donc aucune haute disponibilité.
- **HyperSQL embarqué** : les données disparaissent à chaque redémarrage de pod (voir `BACKUP.md`).
- **Pas de Spring Boot Actuator** : les probes sont de simples `tcpSocket`, et il n'y a pas de métriques applicatives.
- **Pas d'autoscaling** (HPA) ni de NetworkPolicy.
- **La supervision n'est pas déployée par la CI** : je la déploie à la main avec Helm.
- **Image de base Alpine 3.19 en fin de vie** : Trivy prévient que sa détection peut être incomplète. Je devrais passer à une version supportée.
- **Faille Spring ignorée** : CVE-2026-47884 (`XsltView`) n'est corrigée que dans Spring Framework 7 (donc Spring Boot 4). Mon backend n'utilise aucune `XsltView`, donc je l'ai ignorée avec justification et date d'expiration dans `.trivyignore`.
