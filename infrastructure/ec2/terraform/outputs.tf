output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.main.id
}

output "subnet_id" {
  description = "Public Subnet ID"
  value       = aws_subnet.public.id
}

output "security_group_id" {
  description = "EC2 Security Group ID"
  value       = aws_security_group.ec2.id
}

output "instance_profile_name" {
  description = "EC2 Instance Profile Name"
  value       = aws_iam_instance_profile.ec2_profile.name
}

output "instance_profile_arn" {
  description = "EC2 Instance Profile ARN"
  value       = aws_iam_instance_profile.ec2_profile.arn
}

output "control_plane_role_arn" {
  description = "Control Plane IAM Role ARN"
  value       = aws_iam_role.control_plane_role.arn
}

output "latest_ami_id" {
  description = "Latest Amazon Linux 2 AMI ID"
  value       = data.aws_ami.amazon_linux_2.id
}

output "key_pair_name" {
  description = "Key Pair Name"
  value       = var.create_key_pair ? aws_key_pair.airflow[0].key_name : var.key_pair_name
}

output "configuration_for_application_yml" {
  description = "Configuration values to use in application.yml"
  value = {
    aws_region              = var.aws_region
    ami_id                  = data.aws_ami.amazon_linux_2.id
    instance_type           = var.instance_type
    key_name                = var.create_key_pair ? aws_key_pair.airflow[0].key_name : var.key_pair_name
    subnet_id               = aws_subnet.public.id
    security_group_id       = aws_security_group.ec2.id
    iam_instance_profile    = aws_iam_instance_profile.ec2_profile.name
  }
}
