terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Etat Terraform separe de celui de l'instance lcv (cle differente)
  backend "s3" {
    bucket       = "orion-terraform-state-476646938402"
    key          = "orion-k3s/terraform.tfstate"
    region       = "eu-west-3"
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region
}
