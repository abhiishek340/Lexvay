terraform {
  required_version = ">= 1.17.2, < 1.18.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "6.74.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "3.9.1"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  name = "${var.project}-${var.environment}"

  # Role names created by db/initdb/01-roles.sh: the owner runs Flyway, the app role cannot bypass row-level security.
  db_owner_user = "lexvay_owner"
  db_app_user   = "lexvay_app"
  db_name       = "lexvay"
}
