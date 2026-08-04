# Stratégie et exécution des tests — Frontend (Angular)

Ce document décrit la stratégie de tests automatisés du frontend MicroCRM (Orion), les taux de couverture actuels, ainsi que les commandes permettant de les exécuter localement et dans le pipeline CI/CD.

## 1. Vue d'ensemble

| Outils | Type de tests | Couverture actuelle |
|---|---|---|
| Jasmine, Karma, karma-coverage | Composants standalone, services, pipes | **100 % (132 specs)** |

Le rapport de couverture (format `lcov`) est généré à chaque exécution des tests et remonté automatiquement à SonarCloud via le pipeline CI/CD — aucune saisie manuelle de taux de couverture n'est nécessaire.

## 2. Architecture testée

Les tests couvrent les composants standalone Angular, les services HTTP (`/api/persons`, `/api/organizations`) et la logique de reconstruction de la relation personne ↔ organisation, recalculée côté client par filtrage depuis la migration hors Spring Data REST (pour éviter toute modification du backend). 132 specs au total, réparties sur l'ensemble de l'application.

## 3. Exécuter les tests en local

```bash
cd front
ng test
```

Par défaut, `karma.conf.js` est configuré pour utiliser **ChromeHeadless** (essentiel en environnement CI/serveur sans affichage graphique — l'usage d'un Chrome non headless a été identifié comme cause de blocage des tests en CI lors d'une session de debug précédente).

Pour lancer les tests avec génération de la couverture :

```bash
ng test --code-coverage
```

## 4. Consulter la couverture

```bash
open front/coverage/microcrm/index.html   # rapport HTML lisible
```

Le rapport `lcov` consommé par SonarCloud est généré au même endroit :

```
front/coverage/microcrm/lcov.info
```

Configuration `karma.conf.js` :

```js
reporters: ["progress", "kjhtml", "coverage"],
coverageReporter: {
  dir: require("path").join(__dirname, "coverage/microcrm"),
  subdir: ".",
  reporters: [
    { type: "html" },
    { type: "text-summary" },
    { type: "lcov" }, // format requis par SonarCloud
  ],
},
```

Et dans `sonar-project.properties` (le scanner CLI ne devine pas ce chemin automatiquement, contrairement au plugin Gradle côté back) :

```properties
sonar.javascript.lcov.reportPaths=coverage/microcrm/lcov.info
```

## 5. Taux de couverture actuels

| Métrique | Taux |
|---|---|
| Couverture globale (statements) | 100 % |
| Nombre de specs | 132 |
| Quality Gate SonarCloud | ✅ Passée (0 nouvelle issue) |

## 6. Intégration dans le pipeline CI/CD

Le job `test-front` transmet son rapport `lcov` en artefact au job `sonarqube-front`, les deux jobs travaillant dans des espaces isolés :

```yaml
test-front:
  stage: test
  script:
    - cd front && ng test --code-coverage
  artifacts:
    paths:
      - front/coverage/microcrm/lcov.info
    expire_in: 1 hour

sonarqube-front:
  stage: quality
  needs:
    - job: test-front
      artifacts: true
  script:
    - cd front && sonar-scanner
```

**Règles de déclenchement :** `test-front` dispose de règles explicites (`merge_request_event` **ou** push sur branche), condition nécessaire pour que le `needs:` de `sonarqube-front` reste valide en contexte de pipeline de merge request — sans règle explicite, GitLab exclut ce job par défaut des pipelines `merge_request_event`.

**Politique de fusion :** aucune fusion vers `main` n'est possible tant que les tests ou la Quality Gate SonarCloud échouent (Quality Gate bloquante).

## 7. Bonnes pratiques appliquées

- La couverture à 100 % a été atteinte progressivement lors de la migration de l'application hors Spring Data REST (passage à une API REST classique `/api/persons`, `/api/organizations`), en écrivant les specs au fil de chaque changement plutôt qu'en rattrapage final.
- Le flag `--code-coverage` est systématiquement utilisé en CI pour garantir que le rapport `lcov` est toujours régénéré à jour, sans étape manuelle.
- L'usage de ChromeHeadless dans `karma.conf.js` garantit un comportement identique entre l'exécution locale et l'exécution en CI, évitant les blocages liés à l'absence d'affichage graphique sur les runners.
