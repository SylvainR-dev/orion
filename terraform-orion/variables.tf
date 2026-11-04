variable "aws_region" {
  description = "Region AWS"
  type        = string
  default     = "eu-west-3"
}

variable "instance_type" {
  description = "Type d'instance EC2 pour k3s + Orion + monitoring"
  type        = string
  default     = "t3.medium"
}

variable "admin_ip_cidr" {
  description = "CIDR autorise en SSH (ex: 1.2.3.4/32). Ne JAMAIS mettre 0.0.0.0/0."
  type        = string
}

variable "key_name" {
  description = "Cle SSH AWS deja existante (partagee avec l'instance lcv)"
  type        = string
  default     = "orion-ec2-key"
}
