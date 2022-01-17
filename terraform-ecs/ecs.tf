resource "aws_vpc" "free-scala-aws-vpc" {
  cidr_block = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    name = "free-scala-aws-vpc"
    source = "Terraform"
  }
}

resource "aws_subnet" "free-scala-public-aws-subnet" {
  vpc_id = aws_vpc.free-scala-aws-vpc.id
  cidr_block = "10.0.0.0/20"
}

resource "aws_internet_gateway" "free-scala-aws-internet-gateway" {
  vpc_id = aws_vpc.free-scala-aws-vpc.id

  tags = {
    Name = "free-scala-aws-internet-gateway"
  }
}

resource "aws_route" "free-scala-aws-route" {
  route_table_id = aws_vpc.free-scala-aws-vpc.main_route_table_id
  gateway_id = aws_internet_gateway.free-scala-aws-internet-gateway.id
  destination_cidr_block = "0.0.0.0/0"
}

resource "aws_security_group" "free-scala-aws-security-group" {
  name  = "free-scala-aws-security-group"
  vpc_id = aws_vpc.free-scala-aws-vpc.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    protocol  = -1
    self      = true
    from_port = 0
    to_port   = 0
    description = ""
  }

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    self        = "false"
    cidr_blocks = ["0.0.0.0/0"]
    description = "Port 80"
  }

  tags = {
    Name = "free-scala-aws-security-group"
  }
}

resource "aws_iam_role" "free-scala-ecs-task-execution-iam-role" {
  name = "free-scala-ecs-task-execution-iam-role"

  assume_role_policy = <<-EOF
  {
   "Version": "2012-10-17",
   "Statement": [
     {
       "Action": "sts:AssumeRole",
       "Principal": {
         "Service": "ecs-tasks.amazonaws.com"
       },
       "Effect": "Allow",
       "Sid": ""
     }
   ]
  }
  EOF
}

resource "aws_iam_role_policy_attachment" "ecs-task-execution-role-policy-attachment" {
  role = aws_iam_role.free-scala-ecs-task-execution-iam-role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_ecs_cluster" "free-scala-ecs-cluster" {
  name = "free-scala-ecs-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_ecs_task_definition" "free-scala-ecs-task-definition" {
  depends_on = [ null_resource.docker-push ]

  family = "free-scala-ecs-task-definition"
  cpu = 256
  memory = 512
  requires_compatibilities = ["FARGATE"]
  network_mode = "awsvpc"
  execution_role_arn       = aws_iam_role.free-scala-ecs-task-execution-iam-role.arn

  container_definitions = jsonencode([{
    name = "free-scala-ecs-container-definitions"
    image = local.docker-image
    essential = true
    cpu = 256
    memory = 512
    environment = [{
      name = "AWS_ACCESS_KEY_ID"
      value = local.aws-access-key-id
    }, {
      name = "AWS_SECRET_ACCESS_KEY"
      value = local.aws-secret-access-key
    }, {
      name = "AWS_SESSION_TOKEN"
      value = local.aws-session-token
    }, {
      name = "AWS_REGION"
      value = var.aws-region
    }, {
      name = "AWS_BUCKET"
      value = var.s3-bucket
    }]

    /*
    "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-region": ${var.aws-region},
          "awslogs-group": "/ecs/blah",
          "awslogs-stream-prefix": "ecs"
        }
      }
    */
  }])
}

resource "aws_ecs_service" "free-scala-ecs-service" {
  name = "free-scala-ecs-service"
  cluster = aws_ecs_cluster.free-scala-ecs-cluster.id
  task_definition = aws_ecs_task_definition.free-scala-ecs-task-definition.id
  desired_count = 1
  launch_type = "FARGATE"
  platform_version = "LATEST"

  network_configuration {
    assign_public_ip  = true
    security_groups   = [aws_security_group.free-scala-aws-security-group.id]
    subnets           = [aws_subnet.free-scala-public-aws-subnet.id]
  }

  lifecycle {
    ignore_changes = [task_definition]
  }
}