variable "project" {
  description = "Short lowercase name used as the prefix of every resource."
  type        = string
  default     = "lexvay"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,20}$", var.project))
    error_message = "project must start with a letter and contain 2 to 21 lowercase letters, digits or hyphens."
  }
}

variable "environment" {
  description = "Deployment environment. Production defaults (Multi-AZ database, deletion protection) apply to every value."
  type        = string

  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "environment must be one of dev, staging or prod."
  }
}

variable "region" {
  description = "AWS region for every regional resource."
  type        = string

  validation {
    condition     = can(regex("^[a-z]{2}(-[a-z]+)+-[0-9]$", var.region))
    error_message = "region must look like us-west-2."
  }
}

variable "vpc_cidr" {
  description = "IPv4 range of the VPC. It is split into one public and one private subnet per availability zone."
  type        = string
  default     = "10.20.0.0/16"

  validation {
    condition     = can(cidrhost(var.vpc_cidr, 0)) && tonumber(split("/", var.vpc_cidr)[1]) <= 20
    error_message = "vpc_cidr must be a valid IPv4 CIDR block of /20 or larger."
  }
}

variable "az_count" {
  description = "Number of availability zones to spread subnets across."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count must be 2 or 3."
  }
}

variable "nat_gateway_per_az" {
  description = "Run one NAT gateway per availability zone. When false a single NAT gateway serves every private subnet: cheaper, but outbound traffic (model API, image pulls) stops if its zone fails."
  type        = bool
  default     = false
}

variable "api_domain_name" {
  description = "DNS name that CloudFront uses to reach the load balancer. It must be covered by certificate_arn and be pointed (alias or CNAME) at the load balancer DNS name from the alb_dns_name output."
  type        = string

  validation {
    condition     = can(regex("^([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}$", var.api_domain_name))
    error_message = "api_domain_name must be a lowercase fully qualified domain name."
  }
}

variable "certificate_arn" {
  description = "ARN of an ACM certificate, in the same region, for api_domain_name. The load balancer only serves HTTPS."
  type        = string

  validation {
    condition     = can(regex("^arn:aws[a-z-]*:acm:[a-z0-9-]+:[0-9]{12}:certificate/[a-f0-9-]+$", var.certificate_arn))
    error_message = "certificate_arn must be an ACM certificate ARN."
  }
}

variable "backend_image" {
  description = "Container image of the backend, including a tag or digest, for example an ECR repository URL with a version tag."
  type        = string

  validation {
    condition     = length(trimspace(var.backend_image)) > 0 && !endswith(var.backend_image, ":latest")
    error_message = "backend_image must be set and must not use the mutable :latest tag."
  }
}

variable "backend_port" {
  description = "Port the backend listens on inside the container (LEXVAY_PORT)."
  type        = number
  default     = 8080

  validation {
    condition     = var.backend_port >= 1024 && var.backend_port <= 65535
    error_message = "backend_port must be between 1024 and 65535."
  }
}

variable "backend_cpu" {
  description = "Fargate CPU units per backend task."
  type        = number
  default     = 1024

  validation {
    condition     = contains([256, 512, 1024, 2048, 4096], var.backend_cpu)
    error_message = "backend_cpu must be one of 256, 512, 1024, 2048 or 4096."
  }
}

variable "backend_memory" {
  description = "Fargate memory in MiB per backend task. Must be a combination Fargate accepts for backend_cpu."
  type        = number
  default     = 2048

  validation {
    condition     = var.backend_memory >= 512 && var.backend_memory <= 30720
    error_message = "backend_memory must be between 512 and 30720 MiB."
  }
}

variable "backend_cpu_architecture" {
  description = "CPU architecture the backend image was built for."
  type        = string
  default     = "X86_64"

  validation {
    condition     = contains(["X86_64", "ARM64"], var.backend_cpu_architecture)
    error_message = "backend_cpu_architecture must be X86_64 or ARM64."
  }
}

variable "backend_desired_count" {
  description = "Number of backend tasks. Two or more keeps the service up during a deployment."
  type        = number
  default     = 2

  validation {
    condition     = var.backend_desired_count >= 1 && var.backend_desired_count <= 20
    error_message = "backend_desired_count must be between 1 and 20."
  }
}

variable "llm_model" {
  description = "Model id passed to the backend as LEXVAY_LLM_MODEL."
  type        = string
  default     = "claude-sonnet-5-5"

  validation {
    condition     = length(trimspace(var.llm_model)) > 0
    error_message = "llm_model must not be empty."
  }
}

variable "llm_effort" {
  description = "Reasoning effort passed to the backend as LEXVAY_LLM_EFFORT."
  type        = string
  default     = "medium"

  validation {
    condition     = contains(["low", "medium", "high", "xhigh", "max"], var.llm_effort)
    error_message = "llm_effort must be one of low, medium, high, xhigh or max."
  }
}

variable "llm_price_input_per_mtok" {
  description = "USD per million input tokens, used only by the cost report (LEXVAY_LLM_PRICE_INPUT_PER_MTOK)."
  type        = number
  default     = 2.0

  validation {
    condition     = var.llm_price_input_per_mtok >= 0
    error_message = "llm_price_input_per_mtok must not be negative."
  }
}

variable "llm_price_output_per_mtok" {
  description = "USD per million output tokens, used only by the cost report (LEXVAY_LLM_PRICE_OUTPUT_PER_MTOK)."
  type        = number
  default     = 10.0

  validation {
    condition     = var.llm_price_output_per_mtok >= 0
    error_message = "llm_price_output_per_mtok must not be negative."
  }
}

variable "db_engine_version" {
  description = "PostgreSQL engine version. A major version alone (18) lets RDS pick the current minor release."
  type        = string
  default     = "18"

  validation {
    condition     = can(regex("^18(\\.[0-9]+)?$", var.db_engine_version))
    error_message = "db_engine_version must be PostgreSQL 18, optionally with a minor version such as 18.1."
  }
}

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
  default     = "db.t4g.medium"

  validation {
    condition     = can(regex("^db\\.[a-z0-9]+\\.[a-z0-9]+$", var.db_instance_class))
    error_message = "db_instance_class must look like db.t4g.medium."
  }
}

variable "db_allocated_storage" {
  description = "Initial database storage in GiB."
  type        = number
  default     = 50

  validation {
    condition     = var.db_allocated_storage >= 20 && var.db_allocated_storage <= 65536
    error_message = "db_allocated_storage must be between 20 and 65536 GiB."
  }
}

variable "db_max_allocated_storage" {
  description = "Upper limit in GiB for storage autoscaling. Must be at least db_allocated_storage."
  type        = number
  default     = 200

  validation {
    condition     = var.db_max_allocated_storage >= var.db_allocated_storage && var.db_max_allocated_storage <= 65536
    error_message = "db_max_allocated_storage must be at least db_allocated_storage and at most 65536 GiB."
  }
}

variable "db_multi_az" {
  description = "Keep a synchronous standby in a second availability zone."
  type        = bool
  default     = true
}

variable "db_backup_retention_days" {
  description = "Days to keep automated backups. Signed determinations live in this database, so zero is not allowed."
  type        = number
  default     = 14

  validation {
    condition     = var.db_backup_retention_days >= 1 && var.db_backup_retention_days <= 35
    error_message = "db_backup_retention_days must be between 1 and 35."
  }
}

variable "deletion_protection" {
  description = "Protect the database and the load balancer from deletion, and keep a final database snapshot."
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "Days to keep backend logs in CloudWatch."
  type        = number
  default     = 90

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "log_retention_days must be a retention period CloudWatch Logs accepts."
  }
}

variable "cloudfront_price_class" {
  description = "CloudFront price class: which edge locations serve the web application."
  type        = string
  default     = "PriceClass_100"

  validation {
    condition     = contains(["PriceClass_100", "PriceClass_200", "PriceClass_All"], var.cloudfront_price_class)
    error_message = "cloudfront_price_class must be PriceClass_100, PriceClass_200 or PriceClass_All."
  }
}
