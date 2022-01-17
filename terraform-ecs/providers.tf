terraform {
  required_version = ">= 1.0.11"

  required_providers {
    null = {
      source  = "hashicorp/null"
      version = "3.1.0"
    }

    random = {
      source  = "hashicorp/random"
      version = "~> 3.1.0"
    }

    local = {
      source = "hashicorp/local"
      version = "~> 2.0"
    }

    archive = {
      source = "hashicorp/archive"
      version = "~> 2.0"
    }

    cloudinit = {
      source  = "hashicorp/cloudinit"
      version = "~> 2.2.0"
    }

    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.68.0"
    }
  }
}

provider "aws" {
  profile = var.aws-profile
  region  = var.aws-region
}