# MicroCRM — Backend

API REST du CRM d'Orion, développée avec Spring Boot. Elle gère des personnes (`Person`) et des organisations (`Organization`), ainsi que leur rattachement.

## Historique et évolution

Ce backend est arrivé avec l'exercice : une application Spring Boot avec Gradle, HyperSQL et JUnit, que l'énoncé me demandait d'industrialiser. En lisant son code, j'ai vu qu'il n'avait en fait aucune couche applicative. Il utilisait Spring Data REST : les repositories JPA étaient exposés tels quels en HTTP, sans contrôleur, sans service et sans DTO. C'est rapide pour faire un prototype, mais cela posait des problèmes de fond : aucune logique métier ne pouvait exister, le modèle de données était exposé directement au client, aucune entrée n'était validée, et le CORS était ouvert à tous les domaines, avec `DELETE` autorisé sur toutes les routes et sans authentification.

Avant de tout reprendre, j'ai vérifié dans les documents du projet que Spring Data REST n'était pas une exigence : seuls Spring Boot, Gradle, JUnit, HyperSQL et SonarQube étaient imposés. J'ai donc décidé de refondre le backend en architecture classique **Controller / Service / Repository**, avec des DTO validés. Je l'ai fait sur une branche dédiée, en commits courts, pour pouvoir revenir en arrière à tout moment. Pendant cette refonte, j'ai corrigé des problèmes que je n'avais pas vus d'abord : une relation `@ManyToMany` mise à jour d'un seul côté, une `NullPointerException` possible, des dates en `java.util.Date`, et surtout une cascade JPA qui supprimait les personnes en même temps que leur organisation (c'est un test d'intégration qui me l'a révélé).

Depuis, le backend a surtout évolué par la sécurité : Trivy a signalé des failles critiques dans la version de Tomcat fournie par Spring Boot, que je corrige en fixant `ext['tomcat.version']` dans `build.gradle`, et une faille Spring que j'ai dû ignorer avec une justification (voir [`../SECURITY.md`](../SECURITY.md)). Le frontend a été adapté aux nouvelles routes `/api/...`.

## Stack technique

| Composant | Technologie |
|---|---|
| Langage / framework | Java, Spring Boot 3.2.5 |
| Build | Gradle 8.7 (wrapper inclus, rien à installer) |
| Base de données | HyperSQL (HSQLDB), embarquée en mémoire, sans serveur externe |
| Tests | JUnit 5, Mockito, `@DataJpaTest`, `@WebMvcTest` |
| Couverture | JaCoCo |
| Analyse statique | SonarCloud |

## Prérequis

- **JDK 17** : c'est la version de compilation du projet (`sourceCompatibility = '17'`), et celle que la CI utilise. L'image Docker, elle, exécute l'application avec un JRE 21.
- Aucune base de données à installer : HyperSQL est embarquée.

Le wrapper Gradle (`./gradlew`) télécharge la bonne version de Gradle au premier lancement.

## Lancer l'application en local

```bash
cd back
./gradlew bootRun
```

L'API démarre sur **`http://localhost:8080`**. Vérification rapide :

```bash
curl http://localhost:8080/api/persons
curl http://localhost:8080/api/organizations
```

Au démarrage, `InitialDataFixture` insère un jeu de données de test.

## Architecture

```
Controller (REST, DTO) → Service (logique métier) → Repository (Spring Data JPA) → HyperSQL
```

Packages principaux :

- `person/` : entité `Person`, `PersonController`, `PersonService`, `PersonRepository` et DTO
- `organization/` : entité `Organization`, `OrganizationController`, `OrganizationService`, `OrganizationRepository` et DTO
- `common/` : `GlobalExceptionHandler`, configuration CORS

Détail et décisions : [`../ARCHITECTURE.md`](../ARCHITECTURE.md).

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

Les erreurs sont normalisées par `GlobalExceptionHandler` : 404 (introuvable), 409 (e-mail déjà utilisé) et 400 (validation).

## Lancer les tests

```bash
cd back
./gradlew test
```

Le rapport de couverture JaCoCo est généré automatiquement après les tests. Rapport HTML : `back/build/reports/jacoco/test/html/index.html`. Détail de la stratégie et de la couverture : [`TESTING.md`](./TESTING.md).

## Analyse de code (SonarCloud)

```bash
export SONAR_TOKEN=<votre-jeton>
export SONAR_HOST_URL=https://sonarcloud.io
./gradlew sonar
```

En CI, c'est le job `sonarqube-back` qui le fait, avec le secret GitHub `SONAR_TOKEN`. Le projet est `sylvainr-dev_microcrm-backend`, dans l'organisation `sylvainr-dev`.

## Build et image Docker

```bash
./gradlew build            # génère le jar dans build/libs/ (et rejoue les tests)
```

L'image du backend fait partie du `Dockerfile` multi-stage unique à la racine du dépôt :

```bash
docker build --target back -t microcrm-back .
```

La CI publie l'image sur GitHub Container Registry : `ghcr.io/sylvainr-dev/orion/back:<version>`. Pour tester le frontend et le backend ensemble en local, sans AWS, voir `docker-compose.yml` à la racine.

## Documentation complémentaire

| Document | Contenu |
|---|---|
| [`../ARCHITECTURE.md`](../ARCHITECTURE.md) | Architecture applicative et infrastructure |
| [`TESTING.md`](./TESTING.md) | Stratégie de tests, couverture, procédure |
| [`PERF-back.md`](./PERF-back.md) | Dimensionnement, supervision, limites de performance |
| [`../SECURITY.md`](../SECURITY.md) | Sécurité applicative, secrets, dette de sécurité |

## Points de vigilance

- HyperSQL embarquée : les données disparaissent à chaque redémarrage du pod, ce n'est pas adapté à la production.
- Pas de Spring Security : l'API n'est pas authentifiée (voir `../SECURITY.md`).
- Pas de Spring Boot Actuator : aucune métrique applicative n'est exposée.

## Références

- [Gradle](https://docs.gradle.org)
- [Spring Boot Gradle Plugin 3.2.5](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/html/)
- [Spring Web](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/index.html#web)
- [Spring Data JPA](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/index.html#data.sql.jpa-and-spring-data)
