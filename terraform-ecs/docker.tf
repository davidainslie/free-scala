locals {
  docker-image = "${aws_ecr_repository.free-scala-ecr.repository_url}:${var.app-version}"
}

resource "null_resource" "docker-login" {
  depends_on = [ aws_ecr_repository.free-scala-ecr ]

  triggers = {
    token_expired = data.aws_ecr_authorization_token.free-scala-ecr-token.expires_at
  }

  provisioner "local-exec" {
    # Viz. aws --profile <profile> ecr get-login-password --region <region> | docker login --username AWS --password-stdin <url>
    command = <<-EOT
      echo ${data.aws_ecr_authorization_token.free-scala-ecr-token.password} | \
      docker login --username ${data.aws_ecr_authorization_token.free-scala-ecr-token.user_name} --password-stdin ${data.aws_ecr_authorization_token.free-scala-ecr-token.proxy_endpoint}
    EOT
  }
}

resource "null_resource" "docker-build" {
  depends_on = [ null_resource.docker-login ]

  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    working_dir = "../"

    command = <<-EOT
      sbt clean docker:publishLocal
      docker tag ${var.app}:${var.app-version} ${local.docker-image}
    EOT
  }
}

resource "null_resource" "docker-push" {
  depends_on = [ null_resource.docker-build ]

  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    command = "docker push ${local.docker-image}"
  }
}