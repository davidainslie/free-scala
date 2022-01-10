resource "aws_ecr_repository" "free-scala-ecr" {
  name = "free-scala-ecr"
}

resource "aws_ecr_repository_policy" "free-scala-ecr-policy" {
  repository = aws_ecr_repository.free-scala-ecr.name

  policy = <<-EOT
    {
      "Version": "2008-10-17",
      "Statement": [
        {
          "Sid": "Adds full ECR access to repository",
          "Effect": "Allow",
          "Principal": "*",
          "Action": [
            "ecr:BatchCheckLayerAvailability",
            "ecr:BatchGetImage",
            "ecr:CompleteLayerUpload",
            "ecr:GetDownloadUrlForLayer",
            "ecr:GetLifecyclePolicy",
            "ecr:InitiateLayerUpload",
            "ecr:PutImage",
            "ecr:UploadLayerPart"
          ]
        }
      ]
    }
    EOT
}

# Viz. aws ecr get-login
data "aws_ecr_authorization_token" "free-scala-ecr-token" {}