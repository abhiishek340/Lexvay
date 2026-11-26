output "cloudfront_domain_name" {
  description = "Address of the web application and API (HTTPS)."
  value       = aws_cloudfront_distribution.main.domain_name
}

output "alb_dns_name" {
  description = "Point api_domain_name at this name (alias or CNAME) so CloudFront can reach the load balancer."
  value       = aws_lb.backend.dns_name
}

output "web_bucket" {
  description = "Upload the Angular build (frontend/dist) here."
  value       = aws_s3_bucket.web.bucket
}

output "files_bucket" {
  description = "Bucket of uploaded documents (LEXVAY_S3_BUCKET)."
  value       = aws_s3_bucket.files.bucket
}

output "database_endpoint" {
  description = "Private address of the PostgreSQL instance."
  value       = aws_db_instance.main.address
}

output "database_admin_secret_arn" {
  description = "RDS-managed administrator credentials, for creating the application roles (db/initdb/01-roles.sh)."
  value       = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "database_secret_arn" {
  description = "Passwords of the application database roles; create the roles with these values."
  value       = aws_secretsmanager_secret.database.arn
}

output "anthropic_secret_arn" {
  description = "Store the Anthropic API key in this secret before the first deployment."
  value       = aws_secretsmanager_secret.anthropic.arn
}

output "ecs_cluster_name" {
  description = "ECS cluster that runs the backend."
  value       = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  description = "ECS service of the backend."
  value       = aws_ecs_service.backend.name
}
