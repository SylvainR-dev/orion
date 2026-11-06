# Performance — Backend (Spring Boot / k3s)

## Historique et évolution

Je n'ai jamais mené de campagne de mesure sur ce backend : mon approche de la performance a toujours été de **dimensionner, puis observer**. Au départ, le backend tournait sur des nœuds EKS `t3.small` (2 vCPU, 2 Go), les seuls acceptés par mon compte AWS, et ces nœuds devaient aussi héberger le frontend et toute la supervision. J'ai donc fixé les ressources du pod de façon empirique : le conteneur démarrait de façon stable avec 512 Mio de mémoire demandée et 768 Mio en limite, sans tuning de la JVM.

Quand j'ai remplacé EKS par une instance EC2 avec k3s pour réduire les coûts, la même taille s'est révélée insuffisante. Avec la supervision déployée, l'API de k3s expirait ; je suis passé à une `t3.medium` (4 Go). La mise à jour de Grafana en version 13 m'a ensuite rappelé qu'un dimensionnement choisi une fois ne reste pas valable : les limites que j'avais fixées pour l'ancienne version l'empêchaient de démarrer, et c'est en les relevant que j'ai résolu le problème.

Le retour arrière a lui aussi changé : en Partie 1, je l'avais scripté avec Ansible (un contrôle de santé qui réessayait plusieurs fois, parce que Spring Boot met plusieurs secondes à démarrer). Aujourd'hui, c'est Helm (`--atomic`) qui s'en charge, avec les probes Kubernetes. Enfin, mes métriques DORA lisaient l'API GitLab ; elles lisent maintenant celle de GitHub Actions.

## 1. Vue d'ensemble

| Aspect | État actuel |
|---|---|
| Test de charge ou de stress | ❌ Non implémenté |
| Dimensionnement Kubernetes | ✅ Défini (demandes et limites) |
| Métriques applicatives (JVM, HTTP) | ❌ Non exposées (pas de Spring Boot Actuator) |
| Métriques d'infrastructure (CPU, mémoire) | ✅ Collectées par Prometheus (kube-prometheus-stack) |
| Indicateurs de livraison | ✅ Métriques DORA |

## 2. Contrainte de plateforme

Le backend, le frontend et toute la supervision (Prometheus, Loki, Grafana, Pushgateway) partagent **une seule instance `t3.medium`** (2 vCPU, 4 Go) qui exécute aussi k3s. Toute décision de dimensionnement doit composer avec cette contrainte.

Historique du choix de la taille : à l'époque d'EKS, le compte était limité aux types éligibles au Free Tier et refusait la `t3.medium`. Sur EC2, la `t3.medium` est passée, et la `t3.small` s'est révélée trop juste avec la supervision.

## 3. Dimensionnement (Kubernetes)

Valeurs du chart `helm/orion/values.yaml` pour le backend :

```yaml
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 500m
    memory: 768Mi
```

Ces valeurs ont été fixées par observation du démarrage, pas par une mesure de charge. À titre d'ordre de grandeur, j'ai observé environ 200 Mo de mémoire résidente pour le processus Java sur une instance précédente (avant la montée de Tomcat en 10.1.59), sans avoir refait la mesure depuis.

**JVM** : je n'ai fixé aucun réglage du tas (`-Xmx`, `-Xms`). L'application s'exécute avec un JRE 21 sur Alpine, dont les valeurs par défaut tiennent compte des limites du conteneur. Par défaut, la JVM limite son tas à environ 25 % de la mémoire du conteneur, soit une centaine de mégaoctets avec une limite de 768 Mio. C'est une valeur par défaut de la JVM, que je n'ai pas validée par un test de montée en charge.

## 4. Démarrage et disponibilité

Spring Boot met plusieurs secondes à démarrer (contexte Spring, JPA, HyperSQL). Cette latence est prise en compte par les probes du chart :

- **readiness** : test de connexion TCP sur le port 8080, après 20 secondes, toutes les 10 secondes ;
- **liveness** : même test, après 30 secondes, toutes les 15 secondes.

Un test immédiat produirait de faux échecs. Avec `helm upgrade --atomic`, si les pods de la nouvelle version ne deviennent pas prêts en 5 minutes, Helm restaure la révision précédente.

Je n'ai jamais mesuré le temps de démarrage. Pour l'obtenir :

```bash
kubectl -n orion logs deploy/orion-backend | grep "Started MicroCRMApplication"
```

Spring affiche cette ligne avec la durée de démarrage.

## 5. Base de données

HyperSQL est embarquée en mode mémoire (contrainte du projet). Conséquences :

- les accès sont dans le processus, donc très rapides pour le volume de données du projet ;
- **aucun test de concurrence** ni de volume réaliste n'a été fait ;
- il ne faut pas extrapoler ces résultats pour dimensionner une vraie base PostgreSQL.

## 6. Optimisations en place

- **Image multi-stage** : les stages `back-build` et `back` sont séparés. L'image finale ne contient que le jar et le JRE, sans Gradle ni cache de dépendances, ce qui réduit sa taille et le temps de téléchargement.
- **Scan Trivy avant publication** : une image avec une faille critique n'est jamais publiée.
- **Version de Tomcat fixée** (`10.1.59`) pour corriger des failles critiques : c'est un correctif de sécurité, je n'ai pas mesuré d'effet sur les performances.

## 7. Supervision : la limite majeure

**Le backend n'expose aucune métrique applicative.** Sans `spring-boot-starter-actuator` :

- il n'y a pas d'endpoint `/actuator/prometheus` ni `/actuator/metrics` ;
- les probes ne vérifient que l'ouverture du port (`tcpSocket`), pas un endpoint de santé ;
- Prometheus ne collecte que des métriques d'infrastructure (CPU et mémoire des pods et du nœud), pas de temps de réponse, de taux d'erreur ni de pool de connexions.

C'est la principale amélioration à apporter.

## 8. Indicateurs disponibles : métriques DORA

Seul le **processus de livraison** est mesuré, pas le runtime applicatif :

- fréquence de livraison ;
- délai de livraison (*lead time for changes*) ;
- taux d'échec des changements ;
- délai de rétablissement.

`scripts/dora_metrics.py` les calcule depuis l'API GitHub Actions et les envoie au Pushgateway, d'où Prometheus les collecte pour le dashboard Grafana « Orion - DORA Metrics ». Deux limites : l'événement mesuré est la **publication des images** (`publish-images`), puisque le déploiement dépend d'une approbation manuelle ; et l'API ne renvoie que la dernière tentative de chaque exécution, donc un échec suivi d'un *Re-run* n'apparaît pas. Pour le détail du mécanisme, voir [`../ARCHITECTURE.md`](../ARCHITECTURE.md).

## 9. Pistes d'amélioration

| Piste | Bénéfice attendu |
|---|---|
| Ajouter `spring-boot-starter-actuator` avec Micrometer et le registre Prometheus | Exposer des métriques applicatives (latence, erreurs, JVM) |
| Passer les probes de `tcpSocket` à `httpGet` sur `/actuator/health` | Détecter les pannes applicatives, pas seulement un port fermé |
| Faire un test de charge (k6 ou JMeter) pour valider les valeurs CPU et mémoire | Remplacer un dimensionnement empirique par une mesure |
| Régler explicitement le tas de la JVM (par exemple `-XX:MaxRAMPercentage`) | Contrôler la mémoire réellement utilisée sous charge |
| Supprimer les exécutions de tests redondantes (`-x test` dans `build-back` et le `Dockerfile`) | Réduire la durée du pipeline |
| Ajouter un autoscaler horizontal (HPA) | Absorber les pics de charge, utile seulement avec plusieurs nœuds |
