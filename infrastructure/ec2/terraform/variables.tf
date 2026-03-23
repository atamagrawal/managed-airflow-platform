variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "environment_name" {
  description = "Environment name prefix"
  type        = string
  default     = "managed-airflow-ec2"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.1.0.0/16"
}

variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t3.medium"
}

variable "create_key_pair" {
  description = "Whether to create a new key pair"
  type        = bool
  default     = false
}

variable "public_key" {
  description = "Public key for SSH access (required if create_key_pair is true)"
  type        = string
  default     = ""
}

variable "key_pair_name" {
  description = "Name of existing key pair to use (if not creating new one)"
  type        = string
  default     = ""
}
