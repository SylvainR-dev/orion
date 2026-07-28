variable "aws_region" {
  description = "Région AWS de déploiement"
  type        = string
  default     = "eu-west-3"
}

variable "cluster_name" {
  description = "Nom du cluster EKS"
  type        = string
  default     = "orion-eks"
}

variable "cluster_version" {
  description = "Version de Kubernetes pour le control plane EKS"
  type        = string
  default     = "1.30"
}

variable "vpc_cidr" {
  description = "Bloc CIDR du VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "node_instance_type" {
  description = "Type d'instance EC2 pour les nœuds du cluster (choisi pour rester dans un budget raisonnable)"
  type        = string
  default     = "t3.small"
}

variable "node_desired_size" {
  description = "Nombre de nœuds souhaité"
  type        = number
  default     = 3
}

variable "node_min_size" {
  description = "Nombre minimum de nœuds"
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Nombre maximum de nœuds"
  type        = number
  default     = 3
}