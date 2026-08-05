# MicroCRM — Frontend

Interface web du CRM d'Orion, développée en Angular. Permet de gérer les personnes et les organisations, et de visualiser leurs rattachements.

## Stack technique

| Composant | Technologie |
|---|---|
| Framework | Angular 17 (composants standalone, pas de `NgModule` central) |
| Gestionnaire de dépendances | NPM |
| Tests | Jasmine, Karma |
| Serveur de fichiers statiques (production) | Caddy |

## Prérequis

- **Node.js** (version compatible Angular 17, voir `package.json` pour la version exacte attendue)
- **Angular CLI** (`npm install -g @angular/cli`, ou utiliser `npx ng` sans installation globale)

## Installation

```bash
cd front
npm install
```

## Lancer l'application en local

L'application a besoin du backend démarré en parallèle (voir `back/README.md`) — l'URL de l'API est configurée dans `src/app/config.ts` (`API_BASE_URL`, par défaut `http://localhost:8080`).

```bash
ng serve
```

L'application est accessible sur **`http://localhost:4200`**.

## Architecture

Composants standalone Angular consommant une API REST classique (`/api/persons`, `/api/organizations`). La relation personne ↔ organisation est reconstruite côté client par filtrage (`PersonService.fetchPersonOrganizations`, `OrganizationService.fetchOrganizationPersons`), plutôt que de dépendre d'un endpoint backend dédié.

Fichiers principaux :
- `person.service.ts` / `organization.service.ts` — appels HTTP vers l'API
- `person-details/`, `organization-details/` — pages de détail/édition
- `main-dashboard/` — vue d'ensemble

Documentation complète : voir [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## Lancer les tests

```bash
ng test
```

Avec génération du rapport de couverture :

```bash
ng test --code-coverage --watch=false
```

Rapport HTML consultable dans `coverage/microcrm/index.html`.

Couverture actuelle : **43 specs uniques, 98,46 % statements / 100 % branches / 95,34 % fonctions / 100 % lignes**. Détail complet et historique : voir [`TESTING-front.md`](./TESTING-front.md).

> Karma est configuré en **ChromeHeadless** par défaut (`karma.conf.js`) pour fonctionner sans affichage graphique, aussi bien en local qu'en CI.

## Build

```bash
ng build
```

Génère les fichiers statiques de production dans `dist/`, avec les optimisations Angular CLI standards (AOT, tree-shaking, minification).

## Docker

L'image frontend fait partie d'un `Dockerfile` multi-stage unique à la racine du dépôt (stages `front-build` et `front`) : le build Angular est compilé puis servi par Caddy, sans Node.js dans l'image finale.

```bash
docker build --target front -t microcrm-front .
```

Pour tester front + back ensemble en local sans dépendance à AWS, voir le `docker-compose.yml` à la racine du dépôt.

## Documentation complémentaire

| Document | Contenu |
|---|---|
| [`ARCHITECTURE.md`](./ARCHITECTURE.md) | Architecture applicative détaillée, décisions techniques |
| [`TESTING-front.md`](./TESTING-front.md) | Stratégie de tests, couverture, procédure complète |
| [`PERF-front.md`](./PERF-front.md) | Optimisations de build, dimensionnement, limites de performance connues |
| [`SECURITY.md`](./SECURITY.md) | Sécurité applicative, dette de sécurité |

## Points de vigilance connus

- Pas d'audit de performance formalisé (Lighthouse CI, Core Web Vitals) à ce stade.
- Pas de monitoring de performance côté navigateur (RUM).

Détail complet dans les documents ci-dessus.
