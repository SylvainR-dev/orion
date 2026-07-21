terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Backend local pour l'instant (state en clair sur la machine).
  # À migrer vers un backend S3 + verrouillage DynamoDB avant tout travail en équipe,
  # pour éviter des états concurrents/écrasés entre plusieurs personnes.
  # backend "s3" {
  #   bucket = "orion-terraform-state"
  #   key    = "eks/terraform.tfstate"
  #   region = "eu-west-3"
  # }
}

provider "aws" {
  region  = var.aws_region
  profile = "orion"
}