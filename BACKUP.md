# Stratégie de sauvegarde — MicroCRM (Orion)

Ce document décrit ce qui est sauvegardé sur le projet, comment, et pourquoi certains éléments en sont volontairement exclus. Il complète `ARCHITECTURE.md` (section rollback) et `SECURITY.md`.

## 1. Vue d'ensemble

| Élément | Sauvegardé ? | Mécanisme |
|---|---|---|
| State Terraform | ✅ Oui | Versioning S3 natif + snapshot horodaté explicite (script dédié) |
| Dashboards Grafana | ✅ Oui | Export automatique CI/CD vers le dépôt Git |
| Données applicatives (HyperSQL) | ❌ Non, choix assumé | Voir section 4 |
| Images Docker | ✅ Oui (de fait) | Conservées indéfiniment dans le GitLab Container Registry, taguées par version sémantique |

## 2. Sauvegarde du state Terraform

### 2.1 Mécanisme natif — versioning S3

Le bucket `orion-terraform-state-476646938402` (backend distant Terraform) a le **versioning S3 activé** depuis sa création (`aws s3api put-bucket-versioning`). Chaque écriture du state (à chaque `terraform apply`/`destroy`) crée automatiquement une nouvelle version, conservée indéfiniment par S3 — aucune perte possible en cas de corruption ou de mauvaise manipulation, la version précédente reste accessible.

### 2.2 Snapshot explicite complémentaire

Le versioning S3 natif fonctionne, mais retrouver une version précise nécessite de parcourir l'historique des versions de l'objet (souvent long, sans repère temporel lisible au premier coup d'œil). Un script dédié crée en complément des points de restauration explicites et datés :

```bash
./scripts/backup-terraform-state.sh
```

Ce script copie le state actuel vers `s3://orion-terraform-state-476646938402/eks/backups/terraform-state-<horodatage>.tfstate`, sans téléchargement local (copie côté serveur S3).

**Lister les snapshots existants :**
```bash
aws s3 ls s3://orion-terraform-state-476646938402/eks/backups/
```

**Restaurer un snapshot** (à faire avec précaution — écrase le state actif) :
```bash
aws s3 cp s3://orion-terraform-state-476646938402/eks/backups/terraform-state-<horodatage>.tfstate \
  s3://orion-terraform-state-476646938402/eks/terraform.tfstate
```

Après restauration, toujours vérifier la cohérence avant toute action :
```bash
cd terraform
terraform plan
```

### 2.3 Quand l'exécuter

Ce script n'est pas (encore) intégré au pipeline CI/CD de façon automatique — à exécuter manuellement avant toute opération Terraform jugée risquée (changement de version majeure, modification structurelle du VPC/EKS). Automatisation en amont d'un `terraform-apply` identifiée comme amélioration future possible.

## 3. Sauvegarde des dashboards Grafana

Mécanisme déjà en place et automatisé : le job CI `export-dashboards` (manuel, sur `main`) exporte l'intégralité des dashboards Grafana actifs vers `helm/monitoring/dashboards/*.json`, committés automatiquement sur le dépôt Git (`chore(monitoring): sauvegarde automatique des dashboards Grafana [skip ci]`).

En cas de perte du cluster (destroy accidentel, corruption), ces fichiers JSON peuvent être réinjectés via des ConfigMaps labellisés (`grafana_dashboard=1`), repris automatiquement par le sidecar Grafana au prochain déploiement de `monitoring-deploy`.

## 4. Exclusion volontaire des données applicatives (HyperSQL)

**Aucun mécanisme de sauvegarde n'est mis en place pour les données métier de l'application (personnes, organisations).** Ce choix est délibéré, pas un oubli, pour les raisons suivantes :

- **HyperSQL tourne en mode embarqué mémoire** (contrainte du projet — pas de PostgreSQL en production, voir `PERF-back.md`), sans volume persistant attaché au pod backend. Les données n'existent que dans la RAM du conteneur.
- **Conséquence directe** : à chaque redéploiement (`helm upgrade`), redémarrage de pod, ou cycle `terraform destroy`/`apply`, les données sont perdues **de toute façon**, qu'un mécanisme de sauvegarde existe ou non — sauvegarder une base qui s'efface systématiquement à chaque cycle n'aurait pas de valeur opérationnelle réelle dans l'état actuel de l'architecture.
- **Cohérent avec le périmètre du projet** : l'application est un environnement de démonstration/pédagogique (`InitialDataFixture` réinjecte un jeu de données de test à chaque démarrage), pas un service en production avec des données réelles à préserver.

### Ce qu'il faudrait pour rendre une sauvegarde pertinente

Si le projet évoluait vers une cible de production réelle, la sauvegarde de données applicatives nécessiterait au préalable :
1. Migration vers une base persistante (PostgreSQL/RDS, déjà identifiée comme dette technique dans `SECURITY.md` et `PERF.md`).
2. Ou, a minima, bascule de HyperSQL du mode mémoire vers le mode fichier (`jdbc:hsqldb:file:...`) avec un volume persistant (StorageClass EBS déjà disponible sur le cluster, réutilisable).
3. Un job de sauvegarde du fichier/dump vers S3, sur le même modèle que le script `backup-terraform-state.sh`.

Ce point est documenté comme axe d'amélioration future plutôt que traité dans le cadre actuel du projet.