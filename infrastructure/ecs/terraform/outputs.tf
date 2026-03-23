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

output "efs_file_system_id" {
  description = "EFS File System ID"
  value       = aws_efs_file_system.airflow.id
}

output "efs_access_point_id" {
  description = "EFS Access Point ID for PostgreSQL"
  value       = aws_efs_access_point.postgres.id
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
    aws_region              = var.aws_region
    ecs_cluster_prefix      = var.environment_name
    task_execution_role_arn = aws_iam_role.ecs_task_execution_role.arn
    task_role_arn           = aws_iam_role.airflow_task_role.arn
    efs_file_system_id      = aws_efs_file_system.airflow.id
    efs_access_point_id     = aws_efs_access_point.postgres.id
    subnet_ids              = aws_subnet.public[*].id
    security_group_ids      = [aws_security_group.ecs.id]
  }
}
