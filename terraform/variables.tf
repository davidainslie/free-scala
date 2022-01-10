variable "app" {
  type = string
  default = "free-scala"
}

variable "app-version" {
  type = string
  default = "latest" # TODO
}

variable "profile" {
  description = "AWS profile"
  type        = string
  default     = "david"
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "eu-west-2"
}

variable "ec2-key" {
  default = "david"
}

variable "ec2-key-path" {
  default = "/Users/davidainslie/aws/credentials/david.pem"
}