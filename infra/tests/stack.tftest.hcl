# Offline checks of the stack. Both providers are mocked, so no credentials are used and no resource is created:
# these runs evaluate variable validation and the expressions in the configuration, nothing more.

mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["zone-a", "zone-b", "zone-c"]
    }
  }

  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "000000000000"
    }
  }

  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

mock_provider "random" {}

variables {
  environment     = "prod"
  region          = "us-test-1"
  api_domain_name = "api.example.org"
  certificate_arn = "arn:aws:acm:us-test-1:000000000000:certificate/00000000-0000-0000-0000-000000000000"
  backend_image   = "registry.example.org/lexvay/backend:1.0.0"
}

run "defaults_are_private_encrypted_and_versioned" {
  command = plan

  assert {
    condition     = length(aws_subnet.private) == 2 && length(aws_subnet.public) == 2
    error_message = "two availability zones should give two public and two private subnets"
  }

  assert {
    condition     = length(aws_nat_gateway.main) == 1
    error_message = "a single NAT gateway is the default"
  }

  assert {
    condition     = aws_db_instance.main.publicly_accessible == false && aws_db_instance.main.storage_encrypted == true
    error_message = "the database must be private and encrypted"
  }

  assert {
    condition     = aws_db_instance.main.engine == "postgres" && startswith(aws_db_instance.main.engine_version, "18")
    error_message = "the database must run PostgreSQL 18"
  }

  assert {
    condition     = aws_s3_bucket_versioning.files.versioning_configuration[0].status == "Enabled" && aws_s3_bucket_versioning.web.versioning_configuration[0].status == "Enabled"
    error_message = "both buckets must be versioned"
  }

  assert {
    condition = alltrue([
      aws_s3_bucket_public_access_block.files.block_public_acls,
      aws_s3_bucket_public_access_block.files.block_public_policy,
      aws_s3_bucket_public_access_block.files.ignore_public_acls,
      aws_s3_bucket_public_access_block.files.restrict_public_buckets,
      aws_s3_bucket_public_access_block.web.block_public_acls,
      aws_s3_bucket_public_access_block.web.block_public_policy,
      aws_s3_bucket_public_access_block.web.ignore_public_acls,
      aws_s3_bucket_public_access_block.web.restrict_public_buckets,
    ])
    error_message = "public access must be blocked on both buckets"
  }

  assert {
    condition     = aws_ecs_service.backend.network_configuration[0].assign_public_ip == false
    error_message = "backend tasks must not get public addresses"
  }
}

run "one_nat_gateway_per_zone_when_asked" {
  command = plan

  variables {
    az_count           = 3
    nat_gateway_per_az = true
  }

  assert {
    condition     = length(aws_nat_gateway.main) == 3 && length(aws_route_table.private) == 3
    error_message = "each zone should have its own NAT gateway and private route table"
  }
}

run "rejects_unknown_environment" {
  command = plan

  variables {
    environment = "test"
  }

  expect_failures = [var.environment]
}

run "rejects_the_latest_image_tag" {
  command = plan

  variables {
    backend_image = "registry.example.org/lexvay/backend:latest"
  }

  expect_failures = [var.backend_image]
}

run "rejects_a_certificate_that_is_not_acm" {
  command = plan

  variables {
    certificate_arn = "arn:aws:iam::000000000000:server-certificate/example"
  }

  expect_failures = [var.certificate_arn]
}

run "rejects_a_database_that_is_not_postgresql_18" {
  command = plan

  variables {
    db_engine_version = "17.5"
  }

  expect_failures = [var.db_engine_version]
}

run "rejects_a_storage_limit_below_the_initial_size" {
  command = plan

  variables {
    db_allocated_storage     = 100
    db_max_allocated_storage = 50
  }

  expect_failures = [var.db_max_allocated_storage]
}

run "rejects_a_vpc_too_small_to_split" {
  command = plan

  variables {
    vpc_cidr = "10.20.0.0/24"
  }

  expect_failures = [var.vpc_cidr]
}
