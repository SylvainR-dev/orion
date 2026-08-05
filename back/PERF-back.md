# Performance — Backend (Spring Boot / EKS)

Ce document décrit l'approche performance retenue pour le backend MicroCRM (Orion) : dimensionnement des ressources, monitoring en place, limites connues et pistes d'amélioration. Contrairement au testing, **aucun test de charge automatisé** (JMeter, k6, Gatling…) n'a été mis en place à ce stade du projet — ce document reflète une approche de performance par **dimensionnement et observation**, pas par benchmark.

## 1. Vue d'ensemble

| Aspect | État actuel |
|---|---|
| Test de charge / stress | ❌ Non implémenté |
| Dimensionnement ressources K8s | ✅ Défini (requests/limits) |
| Métriques applicatives (JVM, HTTP) | ❌ Non exposées (absence de Spring Boot Actuator) |
| Métriques infrastructure (CPU/RAM cluster) | ✅ Collectées (Prometheus / kube-prometheus-stack) |
| Indicateurs de performance du pipeline | ✅ Métriques DORA automatisées |

## 2. Contrainte de plateforme

Le cluster EKS repose exclusivement sur des nœuds **t3.small** (2 vCPU / 2 Go RAM), imposés par le compte AWS en Free Tier. Toute décision de dimensionnement applicatif doit composer avec cette contrainte matérielle, partagée entre le backend, le frontend et l'ensemble de la stack de supervision (Prometheus, Loki, Grafana, Pushgateway).

## 3. Dimensionnement des ressources (Kubernetes)

```yaml
resources:
  requests:
    memory: "512Mi"
  limits:
    memory: "768Mi"
```

Ce dimensionnement a été fixé empiriquement (démarrage stable observé du conteneur Spring Boot par défaut, sans tuning JVM particulier) plutôt que par une campagne de mesure de charge. Aucune limite CPU explicite n'a été fixée — point à documenter comme axe d'amélioration (risque de contention CPU non maîtrisée sous charge concurrente sur un nœud t3.small).

**JVM :** aucun réglage explicite du heap (`-Xmx`/`-Xms`) n'a été appliqué ; l'application tourne avec les valeurs par défaut de la JVM embarquée dans l'image, calculées automatiquement en fonction des limites de conteneur (JDK container-aware depuis Java 10+). Non validé par un test de montée en charge.

## 4. Démarrage et disponibilité

Spring Boot met plusieurs secondes à démarrer complètement (chargement du contexte, initialisation JPA/HyperSQL). Cette latence de démarrage est prise en compte dans le mécanisme de rollback Ansible :

- health check HTTP avec **10 tentatives**, **3 secondes d'intervalle** entre chaque tentative ;
- un test immédiat et unique aurait produit de faux échecs de déploiement.

Ce paramétrage constitue la seule mesure de performance de démarrage formalisée à ce jour — aucune mesure précise du temps de démarrage moyen n'a été effectuée.

## 5. Base de données

HyperSQL est utilisée en mode embarqué (contrainte du projet — pas de PostgreSQL en production). Conséquences en matière de performance :

- Les accès base sont in-process, donc très rapides à l'échelle du volume de données du projet (démonstration, pas de charge réelle) ;
- **Aucun test de montée en charge** n'a été réalisé pour évaluer un comportement sous concurrence ou sur un volume de données réaliste ;
- Ce choix n'est pas représentatif d'un environnement de production et ne doit pas être extrapolé pour dimensionner une base PostgreSQL/RDS réelle.

## 6. Optimisations déjà en place

- **Image Docker multi-stage** : séparation des stages `back-build` et `back`, l'image finale ne contient que l'artefact compilé et le JRE, sans les outils de build (Gradle, cache de dépendances) — réduit la taille de l'image et le temps de pull.
- **Version de Tomcat surchargée** (`ext['tomcat.version']` → `10.1.55`) pour corriger des CVE critiques détectées par Trivy — un correctif de sécurité, mais qui a aussi permis de bénéficier des correctifs de performance inclus dans les versions plus récentes de Tomcat.
- **Scan Trivy positionné en amont du pipeline** (juste après le build, avant publication) — évite de déployer une image dont les vulnérabilités pourraient impacter la stabilité et donc la performance perçue du service.

## 7. Monitoring et observabilité — limite majeure connue

**Aucune métrique applicative n'est actuellement exposée par le backend.** L'absence de dépendance `spring-boot-starter-actuator` dans `build.gradle` signifie que :

- il n'existe pas d'endpoint `/actuator/prometheus` ou `/actuator/metrics` ;
- les probes Kubernetes sont en `tcpSocket` (simple test d'ouverture du port), et non en `httpGet` sur un endpoint de santé applicatif ;
- Prometheus ne collecte **que des métriques d'infrastructure** (CPU/RAM des pods et nœuds, via `kube-state-metrics` et `node-exporter`), pas de métriques applicatives (temps de réponse HTTP, taux d'erreur, latence JPA, pool de connexions, etc.).

Cette limite est documentée comme dette technique du projet et constitue le principal axe d'amélioration en matière de performance.

## 8. Indicateur de performance disponible : métriques DORA

En l'absence de métriques applicatives fines, le seul indicateur de performance actuellement mesuré et automatisé porte sur le **processus de livraison**, pas sur le runtime applicatif :

- Fréquence de déploiement
- Délai moyen de mise en production (lead time for changes)
- Taux d'échec des changements (change failure rate)
- Délai de rétablissement du service (time to restore service)

Ces indicateurs sont calculés via `scripts/dora_metrics.py` (API GitLab), poussés vers Prometheus Pushgateway, et visualisés dans le dashboard Grafana « Orion - DORA Metrics ». Voir `testing-back.md` et la documentation CI/CD pour le détail du mécanisme.

## 9. Recommandations / axes d'amélioration

| Recommandation | Bénéfice attendu |
|---|---|
| Ajouter `spring-boot-starter-actuator` + Micrometer + registre Prometheus | Exposer des métriques applicatives réelles (latence HTTP, taux d'erreur, JVM) |
| Basculer les probes `tcpSocket` vers `httpGet` sur `/actuator/health` | Détection plus fine des pannes applicatives (vs simple ouverture de port) |
| Fixer une limite CPU explicite dans les manifests K8s | Éviter la contention CPU non maîtrisée sur nœud partagé t3.small |
| Mettre en place un test de charge (k6 ou JMeter) dans le pipeline CI | Valider le dimensionnement des ressources sous charge réaliste |
| Ajouter un Horizontal Pod Autoscaler (HPA) basé sur métriques CPU/mémoire | Absorber les pics de charge sans intervention manuelle |
| Tuning explicite du heap JVM (`-Xmx`) adapté aux limites de conteneur | Réduire le risque d'OOMKill sous charge |
