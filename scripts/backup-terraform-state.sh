#!/usr/bin/env bash
#
# backup-terraform-state.sh
#
# But : créer un snapshot horodaté explicite du state Terraform dans le bucket S3
# du backend distant, en complément du versioning natif S3 déjà activé sur ce bucket.
#
# Pourquoi ce script existe malgré le versioning S3 :
# le versioning S3 conserve automatiquement chaque écriture du state, mais retrouver
# une version précise nécessite de fouiller l'historique des versions S3 (souvent
# de nombreuses entrées, sans repère temporel lisible). Ce script crée en plus une
# copie explicite, nommée par date, dans un préfixe dédié "backups/" du même bucket,
# pour disposer de points de restauration clairs et faciles à lister.
#
# Usage :
#   ./scripts/backup-terraform-state.sh
#
# Prérequis :
#   - AWS CLI configuré avec des identifiants valides (profil par défaut ou variables
#     d'environnement AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY)
#   - Permissions S3 : s3:GetObject et s3:PutObject sur le bucket concerné
#     (déjà couvertes par la policy IAM orion-terraform-scoped)

set -euo pipefail

# --- Configuration ---
readonly BUCKET="orion-terraform-state-476646938402"
readonly STATE_KEY="eks/terraform.tfstate"
readonly BACKUP_PREFIX="backups"
readonly TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
readonly BACKUP_KEY="eks/${BACKUP_PREFIX}/terraform-state-${TIMESTAMP}.tfstate"

echo "== Sauvegarde du state Terraform =="
echo "Bucket source      : s3://${BUCKET}/${STATE_KEY}"
echo "Destination backup  : s3://${BUCKET}/${BACKUP_KEY}"
echo ""

# Vérifie que le state source existe avant de tenter la copie.
if ! aws s3api head-object --bucket "${BUCKET}" --key "${STATE_KEY}" >/dev/null 2>&1; then
  echo "Erreur : le state ${STATE_KEY} est introuvable dans le bucket ${BUCKET}." >&2
  echo "Vérifiez que l'infrastructure a déjà été provisionnée au moins une fois (terraform apply)." >&2
  exit 1
fi

# Copie côté serveur (pas de téléchargement/upload local, plus rapide et plus sûr).
aws s3api copy-object \
  --bucket "${BUCKET}" \
  --copy-source "${BUCKET}/${STATE_KEY}" \
  --key "${BACKUP_KEY}" \
  --metadata-directive REPLACE \
  --metadata "backup-source=automated-script,backup-date=${TIMESTAMP}"

echo ""
echo "Snapshot créé avec succès : s3://${BUCKET}/${BACKUP_KEY}"
echo ""
echo "Pour lister les snapshots existants :"
echo "  aws s3 ls s3://${BUCKET}/eks/${BACKUP_PREFIX}/"
echo ""
echo "Pour restaurer un snapshot (à exécuter avec précaution, écrase le state actif) :"
echo "  aws s3 cp s3://${BUCKET}/${BACKUP_KEY} s3://${BUCKET}/${STATE_KEY}"