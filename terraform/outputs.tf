output "cluster_name" {
  description = "Nom du cluster EKS"
  value       = module.eks.cluster_name
}

output "cluster_endpoint" {
  description = "Endpoint de l'API Kubernetes"
  value       = module.eks.cluster_endpoint
}

output "cluster_certificate_authority_data" {
  description = "Certificat CA du cluster (nécessaire pour kubectl)"
  value       = module.eks.cluster_certificate_authority_data
  sensitive   = true
}

output "vpc_id" {
  description = "ID du VPC créé"
  value       = module.vpc.vpc_id
}

output "region" {
  description = "Région AWS"
  value       = var.aws_region
}

output "configure_kubectl" {
  description = "Commande à lancer pour configurer kubectl"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}"
}

output "ebs_csi_role_arn" {
  description = "ARN du rôle IAM (IRSA) utilisé par le driver CSI EBS pour la persistance des volumes (stack PLG)"
  value       = module.ebs_csi_irsa_role.iam_role_arn
}