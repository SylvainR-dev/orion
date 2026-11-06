# MicroCRM — Frontend

Interface web du CRM d'Orion, développée avec Angular. Elle permet de gérer les personnes et les organisations, et de visualiser leurs rattachements.

## Historique et évolution

Ce frontend est arrivé avec l'exercice, en même temps que le backend : une application Angular 17 avec des composants standalone, testée avec Karma, que l'énoncé me demandait d'industrialiser. Mon premier contact avec lui a été un problème de mesure : SonarCloud affichait 0 % de couverture, alors que les tests passaient. Je l'ai corrigé dans `karma.conf.js` et dans la configuration de Sonar (voir [`TESTING.md`](./TESTING.md)).

Le changement le plus important est venu de la refonte du backend. Le frontend consommait le format HAL de Spring Data REST. Quand j'ai remplacé cette couche par des contrôleurs REST classiques sous `/api/...`, j'ai dû migrer les services du frontend (`person.service.ts`, `organization.service.ts`) et les composants de détail. J'ai d'abord cru l'avoir fait, mais ce travail était resté dans un stash Git local et n'avait jamais été poussé : c'est en vérifiant l'état réel de `main` que je l'ai découvert. Je l'ai réintégré et testé de bout en bout, avec les deux applications démarrées. La relation personne ↔ organisation est maintenant reconstruite côté client par filtrage : j'ai préféré ne pas ajouter d'endpoint au backend pour ça.

Côté livraison, l'image du frontend est servie par Caddy. J'ai dû désactiver son HTTPS automatique (le `*` du Caddyfile est devenu `:80`) : sans cela, l'application répondait vers un port 443 que je n'exposais pas. Elle est passée d'un cluster EKS à une instance k3s, et sa chaîne de tests et de build de GitLab CI à GitHub Actions.

## Stack technique

| Composant | Technologie |
|---|---|
| Framework | Angular 17 (composants standalone, pas de `NgModule` central) |
| Gestionnaire de dépendances | NPM |
| Tests | Jasmine, Karma |
| Serveur de fichiers statiques (production) | Caddy |

## Prérequis

- **Node.js 20** : c'est la version que la CI utilise (voir `package.json` pour les contraintes d'Angular 17).
- **Angular CLI** : `npm install -g @angular/cli`, ou `npx ng` sans installation globale.

## Installation

```bash
cd front
npm install
```

## Lancer l'application en local

L'application a besoin du backend démarré (voir [`../back/README.md`](../back/README.md)). L'URL de l'API est configurée dans `src/app/config.ts` (`API_BASE_URL`, par défaut `http://localhost:8080`).

```bash
ng serve
```

L'application est accessible sur **`http://localhost:4200`**.

> **Sur le cluster**, le navigateur appelle l'API directement sur `localhost:8080` : il faut donc deux redirections de ports en parallèle (`kubectl port-forward svc/orion-frontend 4200:80` et `kubectl port-forward svc/orion-backend 8080:8080`). Je n'ai pas encore mis d'Ingress qui route `/api` sous le même domaine.

## Architecture

Composants standalone Angular qui consomment une API REST classique (`/api/persons`, `/api/organizations`). La relation personne ↔ organisation est reconstruite côté client par filtrage (`PersonService.fetchPersonOrganizations`, `OrganizationService.fetchOrganizationPersons`), plutôt que de dépendre d'un endpoint backend dédié.

Fichiers principaux :

- `person.service.ts` et `organization.service.ts` : appels HTTP vers l'API
- `person-details/` et `organization-details/` : pages de détail et d'édition
- `main-dashboard/` : vue d'ensemble

Détail et décisions : [`../ARCHITECTURE.md`](../ARCHITECTURE.md).

## Lancer les tests

```bash
ng test
```

Avec génération de la couverture :

```bash
ng test --code-coverage --watch=false
```

Rapport HTML : `coverage/microcrm/index.html`.

Couverture mesurée lors de la dernière vérification : **43 specs uniques, 98,46 % des instructions, 100 % des branches, 95,34 % des fonctions, 100 % des lignes**. Détail et historique : [`TESTING.md`](./TESTING.md).

## Build

```bash
ng build
```

Génère les fichiers statiques de production dans `dist/`, avec les optimisations standard de l'Angular CLI (AOT, tree-shaking, minification).

## Docker

L'image du frontend fait partie du `Dockerfile` multi-stage unique à la racine (stages `front-build` et `front`) : le build Angular est compilé, puis servi par Caddy, sans Node.js dans l'image finale.

```bash
docker build --target front -t microcrm-front .
```

La CI publie l'image sur GitHub Container Registry : `ghcr.io/sylvainr-dev/orion/front:<version>`. Pour tester le frontend et le backend ensemble en local, sans AWS, voir `docker-compose.yml` à la racine.

## Documentation complémentaire

| Document | Contenu |
|---|---|
| [`../ARCHITECTURE.md`](../ARCHITECTURE.md) | Architecture applicative et infrastructure |
| [`TESTING.md`](./TESTING.md) | Stratégie de tests, couverture, procédure |
| [`PERF.md`](./PERF.md) | Optimisations de build, dimensionnement, limites de performance |
| [`../SECURITY.md`](../SECURITY.md) | Sécurité applicative, dette de sécurité |

## Points de vigilance

- Pas d'audit de performance formalisé (Lighthouse CI, Core Web Vitals).
- Pas de mesure de performance côté navigateur.
- Aucun TLS sur l'application : elle n'est ouverte que par redirection de port.
