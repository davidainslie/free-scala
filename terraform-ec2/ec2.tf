resource "tls_private_key" "private-key" {
  algorithm = "RSA"
  rsa_bits = 4096
}

resource "aws_key_pair" "key-pair" {
  key_name = "key-pair"
  public_key = tls_private_key.private-key.public_key_openssh
}

resource "local_file" "pem-file" {
  filename = pathexpand("./${aws_key_pair.key-pair.key_name}.pem")
  file_permission = "600"
  sensitive_content = tls_private_key.private-key.private_key_pem
}

resource "aws_vpc" "free-scala-aws-vpc" {
  cidr_block = "10.0.0.0/16"

  tags = {
    Name = "free-scala-aws-vpc"
  }
}

resource "aws_subnet" "free-scala-public-aws-subnet" {
  vpc_id = aws_vpc.free-scala-aws-vpc.id
  cidr_block = "10.0.1.0/24"
  availability_zone = "${var.aws-region}a"
  map_public_ip_on_launch = "true"

  tags = {
    Name = "free-scala-public-aws-subnet"
  }
}

resource "aws_internet_gateway" "free-scala-aws-internet-gateway" {
  vpc_id = aws_vpc.free-scala-aws-vpc.id

  tags = {
    Name = "free-scala-aws-internet-gateway"
  }
}

resource "aws_route_table" "free-scala-aws-route-table" {
  vpc_id = aws_vpc.free-scala-aws-vpc.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.free-scala-aws-internet-gateway.id
  }

  tags = {
    Name = "free-scala-aws-route-table"
  }
}

resource "aws_route_table_association" "free-scala-aws-route-table-association" {
  route_table_id = aws_route_table.free-scala-aws-route-table.id
  subnet_id = aws_subnet.free-scala-public-aws-subnet.id
}

resource "aws_security_group" "free-scala-aws-security-group" {
  vpc_id = aws_vpc.free-scala-aws-vpc.id
  name = "free-scala-aws-security-group"

  ingress {
    from_port = 22
    to_port = 22
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    from_port = 3000
    to_port = 3000
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "free-scala-aws-security-group"
  }
}

resource "aws_instance" "free-scala-ec2" {
  depends_on = [ null_resource.docker-push ]

  instance_type = "t2.micro"
  ami = "ami-0d37e07bd4ff37148" # amazon_linux
  subnet_id = aws_subnet.free-scala-public-aws-subnet.id
  key_name = aws_key_pair.key-pair.key_name
  security_groups = [aws_security_group.free-scala-aws-security-group.id]

  provisioner "file" {
    source = "scripts/install-docker.sh"
    destination = "/home/ec2-user/install-docker.sh"

    connection {
      host = aws_instance.free-scala-ec2.public_ip
      type = "ssh"
      user = "ec2-user"
      private_key = tls_private_key.private-key.private_key_pem
    }
  }

  /*provisioner "file" {
    source = "."
    destination = "/home/ec2-user/"

    connection {
      host = "${aws_instance.ec2.public_ip}"
      type = "ssh"
      user = "ec2-user"
      private_key = file(var.ec2_key_path)
    }
  }*/

  provisioner "remote-exec" {
    inline = [
      "cd /home/ec2-user/",
      "sudo chmod 777 install-docker.sh",
      "./install-docker.sh",
      "echo ${data.aws_ecr_authorization_token.free-scala-ecr-token.password} | sudo docker login --username ${data.aws_ecr_authorization_token.free-scala-ecr-token.user_name} --password-stdin ${data.aws_ecr_authorization_token.free-scala-ecr-token.proxy_endpoint}",
      "sudo docker run -t -d -e AWS_ACCESS_KEY_ID=${local.aws-access-key-id} -e AWS_SECRET_ACCESS_KEY=${local.aws-secret-access-key} -e AWS_SESSION_TOKEN=${local.aws-session-token} -e AWS_REGION=${var.aws-region} -e AWS_BUCKET=${var.s3-bucket} ${local.docker-image}"
    ]

    connection {
      host = aws_instance.free-scala-ec2.public_ip
      type = "ssh"
      user = "ec2-user"
      private_key = tls_private_key.private-key.private_key_pem
    }
  }

  tags = {
    Name = "free-scala-ec2"
  }
}

resource "null_resource" "ssh-command" {
  triggers = {
    always_run = timestamp()
  }

  provisioner "local-exec" {
    command = <<-EOT
      echo SSH = ssh -i ${local_file.pem-file.filename} ec2-user@${aws_instance.free-scala-ec2.public_ip}
    EOT
  }
}

output "ec2-ip" {
  value = aws_instance.free-scala-ec2.public_ip
}