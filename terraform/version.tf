terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket       = "orion-terraform-state-476646938402"
    key          = "eks/terraform.tfstate"
    region       = "eu-west-3"
    profile      = "orion"
    use_lockfile = true
  }
}

provider "aws" {
  region  = var.aws_region
  profile = "orion"
}