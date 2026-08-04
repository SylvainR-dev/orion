# Sécurité — MicroCRM (Orion)

Ce document décrit l'approche DevSecOps mise en place sur le projet : contrôles de sécurité intégrés au pipeline (shift-left), gestion des secrets, sécurité applicative (back/front), sécurité des conteneurs, et dette de sécurité assumée au niveau infrastructure. Il complète `testing-back.md`, `testing-front.md`, `PERF-back.md` et `PERF-front.md`.

## 1. Approche générale — Shift-left

Le principe retenu dès la Partie 1 est d'intégrer les contrôles de sécurité **le plus tôt possible dans le pipeline**, plutôt qu'en aval côté Ops (l'incident déclencheur : un retour de CVE sur une image déjà transmise à l'équipe Ops avait retardé le premier déploiement). Concrètement :

| Contrôle | Outil | Positionnement dans le pipeline |
|---|---|---|
| Analyse statique du code (SAST) | SonarCloud | Juste après les tests, avant tout build d'image |
| Scan de vulnérabilités des images | Trivy | Immédiatement après chaque build d'image, avant le `push` vers le registre |
| Gestion des secrets | Variables CI/CD GitLab (protégées/masquées) | En amont, jamais commitées dans le code |

## 2. Sécurité applicative — Backend

L'architecture initiale (Spring Data REST pur, `@RepositoryRestResource`) exposait directement les entités JPA en HTTP, avec un CORS ouvert (`allowedOrigins("*")`) combiné à `PATCH`/`DELETE` accessibles sur `/**`, sans authentification visible. La refonte vers une architecture en couches (Controller/Service/Repository) a corrigé plusieurs points :

- **CORS restreint** : `SpringDataRestCustomization` (origine `*`) remplacée par une `CorsConfig` classique, avec origine restreinte et configurable au lieu d'un joker.
- **Validation d'entrée** : DTOs (`records` Java) annotés `@NotBlank`/`@Email` (Jakarta Validation) — un `Person` ne peut plus être créé avec un email vide ou mal formé, ce qui était possible auparavant.
- **Pas d'exposition directe du modèle JPA** : le contrat d'API (DTOs `*Request`/`*Response`) est découplé du schéma de base de données, contrairement à l'exposition brute des entités par Spring Data REST.
- **Gestion centralisée des erreurs** : `GlobalExceptionHandler` (`@RestControllerAdvice`) normalise les réponses d'erreur (404/409/400) plutôt que de laisser fuiter des stack traces ou des détails d'implémentation au client.
- **Correction d'un bug d'intégrité de données** : `CascadeType.ALL` sur la relation `Organization ↔ Person` provoquait une suppression en cascade non voulue des `Person` associées à la suppression d'une `Organization` — corrigé en `{PERSIST, MERGE}`. Bien que principalement un bug fonctionnel, il représentait un risque réel de perte de données.

**Limite connue** : l'absence de `spring-boot-starter-actuator` signifie qu'aucun mécanisme d'authentification/autorisation applicative (Spring Security) n'a été mis en place — l'API reste non authentifiée à ce stade, cohérent avec le périmètre de démonstration du projet mais à traiter avant toute exposition réelle.

## 3. Sécurité applicative — Frontend

- L'appel à l'API backend se fait désormais via une API REST classique (`/api/persons`, `/api/organizations`), sans dépendance au format HAL/Spring Data REST qui exposait davantage la structure interne du modèle de données.
- La relation personne ↔ organisation est reconstruite **côté client par filtrage**, un choix qui évite d'exposer un endpoint supplémentaire côté backend plutôt que d'ajouter de la surface d'attaque.
- Angular applique par défaut l'échappement contextuel (sanitization) du DOM, réduisant nativement le risque de XSS sur les données affichées — aucune vérification manuelle supplémentaire n'a été effectuée sur ce point.

## 4. Sécurité des images conteneurs — Trivy

```bash
trivy image --severity CRITICAL --exit-code 1 <image>
```

- Exécuté via l'image officielle `aquasec/trivy`, positionné juste après chaque build d'image et avant tout `push` vers le registre.
- Bloquant : toute faille **CRITICAL** détectée interrompt la publication de l'image (`--exit-code 1`).
- Résultat concret obtenu sur ce projet : 4 puis 3 CVE critiques réelles détectées sur la version de Tomcat embarquée par Spring Boot, corrigées en surchargeant `ext['tomcat.version']` dans `build.gradle` (mise à jour vers `10.1.55`).

## 5. Analyse statique du code — SonarCloud

- Deux projets SonarCloud distincts : un pour le backend (Java, via le plugin Gradle `org.sonarqube`), un pour le frontend (Angular/TypeScript, via `sonar-scanner-cli`).
- **Quality Gate bloquante** : aucune fusion vers `main` n'est possible tant que la Quality Gate échoue (couverture de code, bugs bloquants/critiques, vulnérabilités critiques, dette technique, duplication).
- Résultat actuel : Quality Gate passée des deux côtés (94,4 % de couverture sur le nouveau code backend, 0 hotspot de sécurité ; 0 nouvelle issue côté frontend).
- Un point de vigilance a été signalé en cours de projet : le token SonarCloud exposé lors d'une session de travail, dont la régénération reste à confirmer.

## 6. Gestion des secrets

| Secret | Emplacement | Portée / scope |
|---|---|---|
| Credentials AWS (`AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`) | Variable CI/CD protégée | Disponible uniquement sur la branche protégée `main` |
| `GITLAB_TOKEN` (semantic-release) | Variable CI/CD protégée et masquée | Scope `api` |
| `DORA_GITLAB_TOKEN` (script métriques DORA) | Variable CI/CD | Scope `read_api` (lecture seule, principe de moindre privilège respecté) |
| `SONAR_TOKEN` / `SONAR_HOST_URL` | Variable CI/CD | **Non protégée** volontairement, pour fonctionner aussi sur les merge requests |
| `REGISTRY_USERNAME` / `REGISTRY_PASSWORD` (deploy token GitLab) | Variable CI/CD | Scope `read_registry`, consommé par Ansible pour créer l'`imagePullSecret` |
| Credentials registre en local | `ansible/registry-credentials.yml` | Fichier gitignoré, jamais commité |
| `GRAFANA_ADMIN_PASSWORD` | ⚠️ Non encore défini en variable CI/CD | Mot de passe par défaut `prom-operator` toujours utilisé — **point ouvert à corriger** |

**Principe appliqué** : aucun secret n'est codé en dur dans le code ou les scripts ; tout passe par les variables CI/CD de GitLab. Un incident a néanmoins été rencontré en cours de projet (confusion entre credentials AWS et deploy token GitLab collés par erreur dans les mauvaises variables), illustrant l'importance de bien distinguer les types de credentials — voir section 9.

## 7. Sécurité de l'infrastructure Cloud

| Aspect | État actuel | Statut |
|---|---|---|
| Utilisateur IAM `orion-terraform` | Doté du rôle `AdministratorAccess` | ⚠️ Dette assumée — disproportionné, à restreindre en conditions réelles |
| Endpoint API du cluster EKS | Public, sans restriction IP, sans VPN/bastion | ⚠️ Dette assumée — simplification pédagogique |
| RBAC Kubernetes | Configuration par défaut du cluster | ⚠️ Non durci, namespace `orion` dédié comme seule isolation |
| NetworkPolicy | Aucune | ⚠️ Absente — pas de restriction des communications inter-pods |
| State Terraform | Backend S3 distant, versionné, verrouillage natif `use_lockfile` | ✅ Sécurisé (accès contrôlé par IAM), mais pas de verrouillage DynamoDB dédié (simplification assumée) |
| Déploiement infrastructure (`terraform-apply`) | Déclenchement manuel (`when: manual`) | ✅ Garde-fou humain volontaire avant toute modification facturée |

## 8. Processus de traitement des vulnérabilités

1. **Détection** : par SonarCloud (code source) ou Trivy (image conteneur), lors de l'exécution du pipeline.
2. **Évaluation de la criticité** : sévérité déterminée automatiquement par l'outil (CVSS pour Trivy, règles de sévérité SonarSource).
3. **Blocage et notification** : toute vulnérabilité critique bloque le pipeline (Quality Gate / `--exit-code 1` Trivy) ; l'équipe concernée est notifiée par l'échec visible du pipeline.
4. **Correction** : sur une branche dédiée, qui repasse par le pipeline complet avant fusion vers `main`.
5. **Déploiement du correctif** : suit le circuit standard (build → sécurité → publication → déploiement).

## 9. Dette de sécurité — synthèse et priorisation

| Item | Risque | Priorité de correction |
|---|---|---|
| `orion-terraform` avec `AdministratorAccess` | Élevé (accès total au compte AWS depuis la CI) | Haute |
| Endpoint EKS public sans restriction IP | Moyen (surface d'attaque exposée sur Internet) | Haute |
| `GRAFANA_ADMIN_PASSWORD` par défaut (`prom-operator`) | Moyen (accès non autorisé aux dashboards/métriques) | Haute |
| Pas d'authentification applicative (API backend ouverte) | Moyen (cohérent avec le périmètre démo actuel) | Moyenne |
| Absence de NetworkPolicy | Faible à moyen (mouvement latéral en cas de compromission d'un pod) | Moyenne |
| HSQLDB embarqué (pas de chiffrement au repos, pas de gestion d'accès dédiée) | Faible (démonstration uniquement) | Basse — non représentatif d'une cible de production |
| Token SonarCloud potentiellement exposé | À confirmer | Haute (à vérifier immédiatement) |

## 10. Recommandations

| Recommandation | Bénéfice attendu |
|---|---|
| Remplacer `AdministratorAccess` par une policy IAM scoping strictement les actions Terraform nécessaires | Réduction drastique de la surface d'attaque en cas de compromission du token CI |
| Restreindre l'accès à l'API EKS par IP (ou mettre en place un VPN/bastion) | Suppression de l'exposition publique inutile |
| Définir `GRAFANA_ADMIN_PASSWORD` comme variable CI/CD protégée et régénérer le mot de passe par défaut | Fermer un accès non autorisé simple à exploiter |
| Confirmer la régénération du token SonarCloud potentiellement exposé | Éviter une compromission déjà identifiée mais non close |
| Ajouter Spring Security (a minima une authentification basique ou API key) avant toute exposition réelle | Fermer l'accès non authentifié actuel à l'API |
| Ajouter des NetworkPolicy Kubernetes (namespace `orion` isolé du namespace `monitoring`) | Limiter le mouvement latéral en cas de compromission d'un pod |
| Ajouter Gitleaks (détection de secrets committés par erreur) au pipeline | Filet de sécurité supplémentaire, déjà identifié dans le plan de sécurité initial mais non implémenté |
