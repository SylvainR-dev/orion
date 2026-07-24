data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

# --- Réseau ---
# NAT gateway unique (pas un par AZ) pour limiter les coûts : suffisant
# pour ce projet, à revoir si besoin de haute disponibilité renforcée.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name = "${var.cluster_name}-vpc"
  cidr = var.vpc_cidr

  azs             = local.azs
  private_subnets = [for i, az in local.azs : cidrsubnet(var.vpc_cidr, 8, i)]
  public_subnets  = [for i, az in local.azs : cidrsubnet(var.vpc_cidr, 8, i + 100)]

  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true

  # Tags requis par EKS pour que le contrôleur de load balancer AWS
  # sache où placer les Ingress/Services de type LoadBalancer
  public_subnet_tags = {
    "kubernetes.io/role/elb"                    = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
  private_subnet_tags = {
    "kubernetes.io/role/internal-elb"           = "1"
    "kubernetes.io/cluster/${var.cluster_name}" = "shared"
  }
}

# --- Cluster EKS ---
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = var.cluster_name
  cluster_version = var.cluster_version

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  # Accès public à l'API du cluster activé pour simplifier (pas de VPN/bastion
  # à mettre en place pour ce projet). À restreindre par IP ou désactiver
  # en environnement réellement en production.
  cluster_endpoint_public_access = true

  # Nécessaire pour l'IRSA (IAM Roles for Service Accounts), utilisé ci-dessous
  # par le rôle du driver CSI EBS (persistance des volumes pour la stack PLG).
  enable_irsa = true

  eks_managed_node_groups = {
    default = {
      instance_types = [var.node_instance_type]
      capacity_type  = "ON_DEMAND"

      min_size     = var.node_min_size
      max_size     = var.node_max_size
      desired_size = var.node_desired_size
    }
  }

  # Addon EKS gérant la création des volumes EBS pour les PersistentVolumeClaims
  # (nécessaire pour la persistance de Prometheus/Loki/Grafana - stack PLG).
  cluster_addons = {
    aws-ebs-csi-driver = {
      most_recent              = true
      service_account_role_arn = module.ebs_csi_irsa_role.iam_role_arn
    }
  }

  # Donne à l'utilisateur/rôle qui exécute Terraform les droits admin sur le cluster
  enable_cluster_creator_admin_permissions = true

  tags = {
    Project = "orion"
    Managed = "terraform"
  }
}

# --- Rôle IAM pour le driver CSI EBS (IRSA) ---
# Distinct du rôle orion-terraform (celui-ci sert au runtime du cluster pour
# créer/attacher des volumes EBS, pas au provisioning de l'infrastructure).
module "ebs_csi_irsa_role" {
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.0"

  role_name             = "orion-ebs-csi-driver"
  attach_ebs_csi_policy = true

  oidc_providers = {
    main = {
      provider_arn               = module.eks.oidc_provider_arn
      namespace_service_accounts = ["kube-system:ebs-csi-controller-sa"]
    }
  }

  tags = {
    Project = "orion"
    Managed = "terraform"
  }
}