variable "aws_region" {
  description = "Région AWS"
  type        = string
  default     = "eu-west-3"
}

variable "instance_type" {
  description = "Type d'instance EC2. t3.small confirmé fonctionnel sur ce compte (t3.medium a déjà été bloqué en contexte EKS, à retester ici prudemment si besoin de plus de RAM)."
  type        = string
  default     = "t3.small"
}

variable "admin_ip_cidr" {
  description = "CIDR autorisé pour SSH / API k3s / accès applicatif (ex: \"1.2.3.4/32\"). Ne JAMAIS mettre 0.0.0.0/0 ici."
  type        = string
}

variable "ssh_public_key_path" {
  description = "Chemin local vers la clé publique SSH à injecter dans l'instance"
  type        = string
  default     = "~/.ssh/orion-ec2-key.pub"
}