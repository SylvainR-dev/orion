# MicroCRM — Backend

API REST du CRM d'Orion, développée en Spring Boot. Fournit la gestion des personnes (`Person`) et des organisations (`Organization`), avec leur relation de rattachement.

## Stack technique

| Composant | Technologie |
|---|---|
| Langage / Framework | Java, Spring Boot 3.2.5 |
| Build | Gradle 8.7 (wrapper inclus, pas besoin d'installer Gradle) |
| Base de données | HyperSQL (HSQLDB), embarquée en mémoire — pas de serveur externe requis |
| Tests | JUnit 5, Mockito, `@DataJpaTest`, `@WebMvcTest` |
| Couverture de code | JaCoCo |
| Analyse statique | SonarCloud |

## Prérequis

- **JDK 21** (ou version compatible Spring Boot 3.2.5)
- Aucune base de données à installer — HyperSQL est embarquée

Le wrapper Gradle (`./gradlew`) télécharge automatiquement la bonne version de Gradle au premier lancement, aucune installation manuelle nécessaire.

## Lancer l'application en local

```bash
cd back
./gradlew bootRun
```

L'API démarre sur **`http://localhost:8080`**.

Vérification rapide :

```bash
curl http://localhost:8080/api/persons
curl http://localhost:8080/api/organizations
```

## Architecture

Le backend suit une architecture en couches classique :

```
Controller (REST, DTOs) → Service (logique métier) → Repository (Spring Data JPA) → HyperSQL
```

Packages principaux :
- `person/` — entité `Person`, `PersonController`, `PersonService`, `PersonRepository`, DTOs
- `organization/` — entité `Organization`, `OrganizationController`, `OrganizationService`, `OrganizationRepository`, DTOs
- `common/` — `GlobalExceptionHandler`, configuration CORS

Documentation complète : voir [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Endpoints principaux

| Méthode | Route | Description |
|---|---|---|
| `GET` | `/api/persons` | Liste des personnes |
| `GET` | `/api/persons/{id}` | Détail d'une personne |
| `POST` | `/api/persons` | Créer une personne |
| `PUT` | `/api/persons/{id}` | Mettre à jour une personne |
| `DELETE` | `/api/persons/{id}` | Supprimer une personne |
| `GET` | `/api/organizations` | Liste des organisations |
| `GET` | `/api/organizations/{id}` | Détail d'une organisation |
| `POST` | `/api/organizations` | Créer une organisation |
| `PUT` | `/api/organizations/{id}` | Mettre à jour une organisation |
| `DELETE` | `/api/organizations/{id}` | Supprimer une organisation |
| `POST` | `/api/organizations/{organizationId}/persons/{personId}` | Associer une personne à une organisation |
| `DELETE` | `/api/organizations/{organizationId}/persons/{personId}` | Dissocier une personne d'une organisation |

## Lancer les tests

```bash
./gradlew test
```

Avec génération du rapport de couverture (déclenché automatiquement après les tests) :

```bash
./gradlew test jacocoTestReport
```

Rapport HTML consultable dans `build/reports/jacoco/test/html/index.html`.

Couverture actuelle : **94 % instructions / 50 % branches**. Détail complet, ventilation par package et procédure de test : voir [`TESTING-back.md`](./TESTING-back.md).

## Analyse de code (SonarCloud)

```bash
./gradlew sonar
```

Nécessite les variables d'environnement `SONAR_TOKEN` et `SONAR_HOST_URL` (déjà configurées en CI/CD, à définir en local si besoin d'une analyse manuelle).

## Build

```bash
./gradlew build
```

Génère le `.jar` exécutable dans `build/libs/`.

## Docker

L'image backend fait partie d'un `Dockerfile` multi-stage unique à la racine du dépôt (stages `back-build` et `back`) :

```bash
docker build --target back -t microcrm-back .
```

Pour tester front + back ensemble en local sans dépendance à AWS, voir le `docker-compose.yml` à la racine du dépôt.

## Documentation complémentaire

| Document | Contenu |
|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Architecture applicative détaillée, décisions techniques |
| [`TESTING-back.md`](./TESTING-back.md) | Stratégie de tests, couverture, procédure complète |
| [`PERF-back.md`](./PERF-back.md) | Dimensionnement, monitoring, limites de performance connues |
| [`SECURITY.md`](./SECURITY.md) | Sécurité applicative, gestion des secrets, dette de sécurité |

## Points de vigilance connus

- Base de données HyperSQL embarquée — non adaptée à un usage de production réel (pas de persistance externe, pas de réplication).
- Absence de Spring Boot Actuator — pas de métriques applicatives exposées, pas d'authentification sur l'API à ce stade (voir `SECURITY.md`).

Détail complet dans les documents ci-dessus.
