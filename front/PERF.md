# Performance — Frontend (Angular / Caddy)

## Historique et évolution

Comme pour le backend, je n'ai jamais fait d'audit de performance formalisé sur ce frontend : je l'ai dimensionné et j'en ai observé le comportement. Ma première intervention sur sa livraison a été un problème de configuration plus que de performance : l'image servait l'application avec Caddy, dont l'HTTPS automatique redirigeait toutes les requêtes vers le port 443, que je n'exposais pas. Le site ne répondait plus. J'ai remplacé le `*` du `Caddyfile` par `:80`. À l'époque, je prévoyais de terminer le TLS sur un Load Balancer AWS devant le cluster EKS.

Ce Load Balancer n'a jamais été mis en place, et je n'ai pas non plus d'Ingress aujourd'hui : l'application tourne sur une instance k3s unique, et je l'ouvre par redirection de port depuis mon poste. Elle n'a donc aucun TLS pour l'instant, et c'est un point que je dois traiter si je l'expose un jour. Le déplacement d'EKS vers une instance unique m'a obligé à relire le dimensionnement : le frontend est de loin le plus léger des composants, et il partage maintenant une seule machine de 4 Go avec le backend et toute la supervision.

La mesure de taille du bundle est la dernière chose que j'ai gagnée : je pensais n'en avoir aucune, et en relisant le journal de ma CI j'ai vu que la compilation Angular l'affiche à chaque exécution. J'ai donc pu mettre un premier chiffre (section 2).

## 1. Vue d'ensemble

| Aspect | État actuel |
|---|---|
| Audit de performance (Lighthouse, Core Web Vitals) | ❌ Non implémenté |
| Optimisations de build (AOT, tree-shaking, minification) | ✅ Natives Angular CLI, actives par défaut |
| Taille du bundle | ✅ Visible dans le journal de la CI (section 2) |
| Dimensionnement Kubernetes | ✅ Défini (demandes et limites) |
| Compression des réponses de Caddy | ⚠️ Non vérifiée |
| Mesure côté navigateur (RUM) | ❌ Non implémentée |

## 2. Optimisations de build et taille du bundle

Le build de production (`ng build --optimization`) applique les optimisations standard de l'Angular CLI :

- **AOT** : les templates sont compilés au build et non dans le navigateur ;
- **tree-shaking** : le code mort est éliminé ;
- **minification** du JavaScript et du CSS.

**Taille mesurée** lors d'une exécution de la CI (version 1.19.0, étape de construction de l'image du frontend) :

| Fichier | Taille brute | Transfert estimé |
|---|---|---|
| `main` | 278,54 kB | 72,16 kB |
| `polyfills` | 33,24 kB | 10,76 kB |
| `styles` | 0 octet | 0 octet |
| **Total initial** | **311,78 kB** | **82,92 kB** |

La compilation prend environ 5 secondes. Ce chiffre reste un instantané : je ne le suis pas dans le temps. Les **budgets** de l'Angular CLI (avertissement ou erreur si un bundle dépasse un seuil) sont définis dans `angular.json` ; je n'ai pas vérifié leurs valeurs depuis la création du projet.

## 3. Image Docker et serveur de fichiers

- **Image multi-stage** : le stage `front-build` compile Angular, et le stage `front` ne contient que les fichiers statiques et **Caddy**. Il n'y a ni Node.js ni dépendances npm de build dans l'image finale.
- **Caddy** sert les fichiers sur le port 80. L'HTTPS automatique est désactivé (`:80` dans le `Caddyfile`).
- **Compression** : je n'ai pas vérifié si Caddy compresse les réponses avec ma configuration. Pour le tester, une fois l'application ouverte par redirection de port :

  ```bash
  curl -sI -H "Accept-Encoding: gzip" http://localhost:4200/ | grep -i content-encoding
  ```

  Si rien ne s'affiche, la compression n'est pas active.

## 4. Dimensionnement (Kubernetes)

Valeurs du chart `helm/orion/values.yaml` pour le frontend :

```yaml
resources:
  requests:
    cpu: 100m
    memory: 64Mi
  limits:
    cpu: 250m
    memory: 128Mi
```

Un serveur de fichiers statiques a une très faible empreinte mémoire, bien inférieure à celle du backend. Ces valeurs reflètent cette réalité, pas une mesure de charge. Les sondes de santé du pod démarrent après 5 secondes, avec une période de 10 secondes.

## 5. Supervision : limite connue

Je ne collecte aucune métrique de performance côté navigateur : ni Core Web Vitals (LCP, INP, CLS), ni mesure réelle du temps de chargement perçu. Prometheus et Grafana ne couvrent que l'infrastructure (processeur et mémoire des pods). C'est la même limite que pour le backend (voir [`../back/PERF-back.md`](../back/PERF-back.md)).

## 6. Pistes d'amélioration

| Piste | Bénéfice attendu |
|---|---|
| Ajouter un TLS devant l'application (Ingress avec certificat) | Sécuriser l'accès si je l'expose un jour |
| Router `/api` sous le même domaine que l'application (Ingress) | Supprimer le second `port-forward` et la dépendance à `localhost:8080` |
| Vérifier et activer la compression gzip ou brotli dans le `Caddyfile` | Réduire le poids transféré |
| Vérifier les budgets de `angular.json` et les fixer à des valeurs strictes | Empêcher une régression silencieuse de la taille |
| Analyser la composition du bundle (`webpack-bundle-analyzer`) | Repérer les dépendances lourdes |
| Ajouter du lazy loading sur les routes non critiques | Réduire le bundle initial |
| En-tête `Cache-Control` long sur les fichiers hachés par l'Angular CLI | Éviter de retélécharger les fichiers à chaque visite |
| Lighthouse CI dans le pipeline | Suivre objectivement les Core Web Vitals |
| Mesure réelle côté navigateur (API Web Vitals vers Grafana ou Loki) | Connaître l'expérience utilisateur réelle |
