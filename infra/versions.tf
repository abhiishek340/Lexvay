terraform {
  required_version = ">= 1.17.2, < 1.18.0"

  # Partial configuration: every value comes from backend.hcl (copy backend.hcl.example), see docs/OPERATIONS.md.
  backend "s3" {}

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
    tags = local.default_tags
  }
}

# CloudFront web ACLs and their log group exist only in us-east-1, whatever region the rest of the stack uses.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"

  default_tags {
    tags = local.default_tags
  }
}

data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

locals {
  name = "${var.project}-${var.environment}"

  default_tags = {
    Project     = var.project
    Environment = var.environment
    ManagedBy   = "terraform"
  }

  # Role names created by db/initdb/01-roles.sh: the owner runs Flyway, the app role cannot bypass row-level security.
  db_owner_user = "lexvay_owner"
  db_app_user   = "lexvay_app"
  db_name       = "lexvay"
}
