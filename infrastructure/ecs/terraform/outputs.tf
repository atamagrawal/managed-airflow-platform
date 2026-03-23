output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "subnet_ids" {
  description = "Subnet IDs"
  value       = aws_subnet.public[*].id
}

output "ecs_security_group_id" {
  description = "ECS Security Group ID"
  value       = aws_security_group.ecs.id
}

output "database_endpoint" {
  description = "RDS PostgreSQL Endpoint"
  value       = aws_db_instance.airflow.endpoint
}

output "redis_endpoint" {
  description = "Redis Endpoint"
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}

output "task_execution_role_arn" {
  description = "ECS Task Execution Role ARN"
  value       = aws_iam_role.ecs_task_execution_role.arn
}

output "task_role_arn" {
  description = "Airflow Task Role ARN"
  value       = aws_iam_role.airflow_task_role.arn
}

output "configuration_for_application_yml" {
  description = "Configuration values to use in application.yml"
  value = {
    aws_region                  = var.aws_region
    ecs_cluster_prefix          = var.environment_name
    task_execution_role_arn     = aws_iam_role.ecs_task_execution_role.arn
    task_role_arn               = aws_iam_role.airflow_task_role.arn
    postgres_host               = aws_db_instance.airflow.address
    redis_host                  = aws_elasticache_cluster.redis.cache_nodes[0].address
    subnet_ids                  = aws_subnet.public[*].id
    security_group_ids          = [aws_security_group.ecs.id]
  }
}
