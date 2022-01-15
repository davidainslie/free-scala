variable "app" {
  type = string
  default = "free-scala"
}

variable "app-version" {
  type = string
  default = "latest" # TODO
}

variable "aws-profile" {
  description = "AWS profile"
  type        = string
  default     = "david"
}

variable "aws-region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-2"
}

variable "s3-bucket" {
  description = "S3 bucket"
  type        = string
  sensitive   = true
}

variable "ec2-key" {
  default = "david" # TODO
}

variable "ec2-key-path" {
  default = "~/aws/credentials/david.pem" # TODO
}

data "external" "aws-credentials" {
  program = ["bash", "scripts/aws-credentials.sh", var.aws-profile]
}

locals {
  aws-access-key-id = data.external.aws-credentials.result.aws-access-key-id
  aws-secret-access-key = data.external.aws-credentials.result.aws-secret-access-key
  aws-session-token = data.external.aws-credentials.result.aws-session-token
}