# Performance — Frontend (Angular / Caddy)

Ce document décrit l'approche performance retenue pour le frontend MicroCRM (Orion) : optimisations de build, dimensionnement des ressources, monitoring en place, limites connues et pistes d'amélioration. Comme pour le backend, **aucun audit de performance formalisé** (Lighthouse CI, WebPageTest…) n'a été mis en place à ce stade du projet.

## 1. Vue d'ensemble

| Aspect | État actuel |
|---|---|
| Audit de performance (Lighthouse, Core Web Vitals) | ❌ Non implémenté |
| Optimisations de build (minification, tree-shaking, AOT) | ✅ Natives Angular CLI, actives par défaut |
| Dimensionnement ressources K8s | ✅ Défini (requests/limits) |
| Compression des assets servis | ⚠️ Non vérifiée explicitement (dépend de la config Caddy par défaut) |
| Monitoring de performance côté navigateur (RUM) | ❌ Non implémenté |

## 2. Optimisations de build déjà en place

Le build de production (`ng build`) bénéficie nativement des optimisations standard de l'Angular CLI, sans configuration additionnelle nécessaire :

- **AOT (Ahead-of-Time compilation)** : les templates sont compilés au build plutôt qu'au runtime dans le navigateur.
- **Tree-shaking** : élimination du code mort et des imports non utilisés.
- **Minification / uglification** du JavaScript et du CSS.
- **Budgets de build** définis par défaut dans `angular.json` (avertissement/erreur si un bundle dépasse un seuil de taille) — à vérifier et ajuster explicitement si ce n'est pas déjà fait, voir recommandations.

Aucune mesure de la taille réelle des bundles générés (`ng build --stats-json` + `webpack-bundle-analyzer`) n'a été réalisée à ce jour.

## 3. Image Docker et serveur de fichiers statiques

- **Image Docker multi-stage** : le stage `front-build` compile l'application Angular, le stage `front` final ne contient que les fichiers statiques compilés servis par **Caddy** — l'image ne contient ni Node.js, ni les dépendances npm de build, réduisant significativement sa taille et le temps de démarrage du conteneur.
- **Caddy** sert les fichiers sur le port `:80` (le `*` initial du Caddyfile, qui déclenchait l'auto-HTTPS de Caddy, a été retiré — le TLS est destiné à être terminé au niveau du Load Balancer AWS, pas dans le conteneur applicatif).
- La compression des réponses (gzip/brotli), activée par défaut par Caddy dans certaines configurations, **n'a pas été vérifiée explicitement** sur ce projet — à confirmer.

## 4. Dimensionnement des ressources (Kubernetes)

```yaml
resources:
  requests:
    memory: "64Mi"
  limits:
    memory: "128Mi"
```

Un serveur de fichiers statiques (Caddy) a une empreinte mémoire très faible comparée au backend applicatif ; ce dimensionnement reflète cette réalité plutôt qu'une mesure de charge. Comme côté back, aucune limite CPU explicite n'a été fixée.

## 5. Monitoring et observabilité — limite connue

Aucune métrique de performance côté navigateur n'est actuellement collectée : ni Core Web Vitals (LCP, FID/INP, CLS), ni Real User Monitoring (RUM), ni suivi du temps de chargement réel perçu par l'utilisateur. Le monitoring en place (Prometheus / Grafana) couvre l'infrastructure du cluster (CPU/RAM des pods Caddy), pas l'expérience utilisateur côté client.

Cette limite est documentée comme dette technique du projet, au même titre que l'absence de métriques applicatives côté backend (voir `PERF-back.md`).

## 6. Recommandations / axes d'amélioration

| Recommandation | Bénéfice attendu |
|---|---|
| Ajouter Lighthouse CI dans le pipeline (job dédié, sur `main` ou en MR) | Suivre objectivement les Core Web Vitals à chaque changement |
| Vérifier/activer explicitement la compression gzip ou brotli dans le Caddyfile | Réduire le poids des assets transférés au navigateur |
| Définir des budgets de build stricts dans `angular.json` (`budgets`) | Empêcher la régression silencieuse de la taille des bundles |
| Analyser la composition du bundle (`webpack-bundle-analyzer`) | Identifier les dépendances lourdes à optimiser ou lazy-loader |
| Mettre en place du lazy loading sur les routes non critiques | Réduire le bundle initial et améliorer le temps de premier rendu |
| Ajouter un en-tête de cache long (`Cache-Control`) sur les assets versionnés (hashés par Angular CLI) | Réduire les re-téléchargements sur visites répétées |
| Intégrer une solution de RUM légère (ex : Web Vitals API + envoi vers Grafana/Loki) | Obtenir une mesure de performance réelle côté utilisateur, pas seulement synthétique |
