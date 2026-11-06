# Stratégie de sauvegarde — MicroCRM (Orion)

## Historique et évolution

Au début du projet, l'état Terraform était un simple fichier sur mon poste, sans historique. C'était fragile : un disque perdu, ou un second poste de travail, et j'aurais dû repartir d'un état vide, avec le risque de recréer une infrastructure qui existait déjà. Quand j'ai voulu lancer Terraform depuis un job de CI, j'ai migré l'état vers un bucket S3 avec versioning et verrouillage natif. Puis j'ai ajouté un script qui crée des copies horodatées, parce que retrouver une version précise dans l'historique de S3 est pénible.

Le passage d'EKS à une instance k3s a changé la nature de ce que j'ai à protéger. Il n'y a plus de volumes EBS gérés par Kubernetes : les données de la supervision sont sur des volumes locaux (`local-path`), donc sur le disque de l'instance. Et la base de l'application est restée une HyperSQL embarquée, qui ne persiste rien.

Un autre changement tient à ma façon de travailler : j'éteins maintenant l'instance après chaque session. Arrêter une instance conserve son disque, donc ce cycle ne perd rien. Ce qui détruit des données, c'est de supprimer l'instance (`terraform destroy`). Avant une opération destructive sur une instance, je prends un snapshot de son disque : je l'ai fait avant de retirer k3s d'une autre instance, et je l'ai supprimé une semaine plus tard, une fois sûr que tout fonctionnait.

Enfin, le job qui exportait automatiquement mes dashboards Grafana vers le dépôt dépendait de GitLab CI. Je l'ai supprimé avec lui : cette sauvegarde est donc redevenue manuelle (voir la section 3).

## 1. Vue d'ensemble

| Élément | Sauvegardé ? | Mécanisme |
|---|---|---|
| État Terraform | ✅ Oui | Versioning S3 natif, plus un snapshot horodaté avec un script |
| Dashboard Grafana personnalisé (DORA) | ✅ Oui | Fichier JSON versionné dans le dépôt |
| Données de supervision (Prometheus, Loki, Grafana) | ❌ Non, choix assumé | Voir la section 4 |
| Données applicatives (HyperSQL) | ❌ Non, choix assumé | Voir la section 5 |
| Images Docker | ✅ Oui, de fait | Conservées sur GitHub Container Registry, taguées par version |
| Code, chaînes de CI et de déploiement | ✅ Oui | Dépôt GitHub, toutes les branches et les tags |
| Disque de l'instance | ⚠️ Ponctuellement | Snapshot EBS manuel avant une opération risquée, pas de planification |

## 2. Sauvegarde de l'état Terraform

### 2.1 Mécanisme natif : le versioning S3

Le bucket de l'état (`orion-terraform-state-<identifiant-de-compte>`) a le **versioning activé**. Chaque écriture de l'état (à chaque `terraform apply` ou `destroy`) crée une nouvelle version, conservée par S3 : après une corruption ou une mauvaise manipulation, la version précédente reste accessible.

L'état d'Orion est stocké sous la clé `orion-k3s/terraform.tfstate`. Mes autres instances ont leur propre clé dans le même bucket, pour qu'une opération sur Orion ne puisse jamais toucher l'état d'une autre machine.

### 2.2 Copie horodatée explicite

Retrouver une version précise dans l'historique de S3 est long, et les entrées n'ont pas de repère de date lisible. J'ai donc écrit un script qui crée des points de restauration datés :

```bash
./scripts/backup-terraform-state.sh                      # état d'Orion (clé par défaut)
./scripts/backup-terraform-state.sh <clé-du-state>       # un autre état du même bucket
```

Il copie l'état vers `s3://<bucket>/orion-k3s/backups/terraform-state-<horodatage>.tfstate`, par une copie côté serveur (rien n'est téléchargé en local). Il ne modifie jamais l'original. Sans argument, il sauvegarde l'état d'Orion.

**Lister les copies :**
```bash
aws s3 ls s3://<bucket>/orion-k3s/backups/
```

**Restaurer une copie** (écrase l'état actif, à faire avec précaution) :
```bash
aws s3 cp s3://<bucket>/orion-k3s/backups/terraform-state-<horodatage>.tfstate \
  s3://<bucket>/orion-k3s/terraform.tfstate
```

Après une restauration, je vérifie toujours la cohérence avant d'agir :
```bash
cd terraform-orion
terraform plan
```

### 2.3 Quand l'exécuter

Le script n'est pas dans la CI : Terraform tourne depuis mon poste, et c'est là que je l'exécute, **avant toute opération risquée** (changement de version majeure du fournisseur, modification d'une ressource IAM ou de l'instance). Je l'ai fait avant d'ajouter les rôles du déploiement continu.

## 3. Dashboards Grafana

Le dépôt contient `helm/monitoring/dashboards/*.json`, mais **un seul de ces fichiers est à moi** : `orion-dora-metrics.json`. Les autres sont d'anciens exports de dashboards que le chart `kube-prometheus-stack` fournit déjà, et je ne les recharge pas pour éviter les doublons.

Mon dashboard est donc sauvegardé par le dépôt. **Si je le modifie dans Grafana, je dois exporter le JSON à la main** (menu *Share → Export*) et le commiter : l'export automatique n'existe plus. Pour le recharger après une perte :

```bash
kubectl create configmap grafana-dashboard-orion-dora-metrics \
  --from-file=dashboard.json=helm/monitoring/dashboards/orion-dora-metrics.json \
  -n monitoring --dry-run=client -o yaml \
  | kubectl label --local -f - grafana_dashboard=1 -o yaml | kubectl apply -f -
```

Le sidecar de Grafana le reprend en quelques secondes.

## 4. Ce que je ne sauvegarde pas : la supervision

Les données de Prometheus et de Loki sont sur le disque de l'instance, avec une rétention de **deux jours**, et je ne les sauvegarde pas. Ce sont des métriques de laboratoire sans valeur à long terme. Les perdre ne coûte qu'un historique court, et le reste de la supervision se redéploie avec Helm depuis le dépôt (voir `README.md`). Les valeurs des métriques DORA, elles, sont recalculées depuis l'API GitHub par `make dora`.

## 5. Ce que je ne sauvegarde pas : les données applicatives

**Aucune sauvegarde n'existe pour les données métier (personnes, organisations).** C'est un choix délibéré :

- **HyperSQL est embarquée dans le conteneur du backend**, sans volume persistant. Les données disparaissent à chaque redémarrage du pod ou redéploiement, que je les sauvegarde ou non.
- **L'application est un environnement de démonstration** : `InitialDataFixture` réinjecte un jeu de données de test à chaque démarrage. Il n'y a pas de données réelles à protéger.

### Ce qu'il faudrait pour une cible de production

1. Une base persistante (PostgreSQL ou RDS), déjà notée dans la dette de `SECURITY.md`.
2. Ou au minimum HyperSQL en mode fichier, avec un volume persistant (la StorageClass `local-path` de k3s suffirait).
3. Un job de sauvegarde vers S3, sur le modèle du script de l'état Terraform.

## 6. Reconstruire après une perte de l'instance

Si je supprime l'instance (par erreur ou volontairement avec `terraform destroy`), son disque disparaît avec elle : k3s, les volumes de la supervision, et tout ce qui était déployé. Tout se reconstruit à partir du dépôt et de l'état Terraform, avec la procédure du `README.md` :

1. `terraform apply` dans `terraform-orion/` (instance, groupe de sécurité, rôles IAM).
2. `make install` puis `make cd-tools` (k3s, agent SSM, Helm, git).
3. Déploiement de la supervision avec Helm, puis rechargement de mon dashboard.
4. Une release sur `main`, ou `make deploy`, pour redéployer l'application. Les images sont déjà sur GHCR.

## 7. Limites et pistes d'amélioration

- Pas de snapshot EBS planifié du disque de l'instance. Je le fais à la main avant une opération risquée, et j'oublie de le planifier.
- Pas de sauvegarde automatique du dashboard DORA : je dois exporter le JSON après toute modification.
- Aucune règle de nettoyage des anciennes versions d'images sur GHCR.
- Le script de sauvegarde de l'état n'est lancé qu'à la main.
