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

variable "aws-access-key-id" {
  description = "AWS_ACCESS_KEY_ID"
  type        = string
  sensitive   = true
}

variable "aws-secret-access-key" {
  description = "AWS_SECRET_ACCESS_KEY"
  type        = string
  sensitive   = true
}

variable "ec2-key" {
  default = "david" # TODO
}

variable "ec2-key-path" {
  default = "~/aws/credentials/david.pem" # TODO
}