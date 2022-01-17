# Deploy

We'll use Terraform - Either deploy to [EC2](../terraform-ec2) or [ECS](../terraform-ecs) running the following within the relevant directory:

```shell
terraform init

terraform plan

terraform apply -var-file="secrets.tfvars"
# OR
terraform apply -var-file="secrets.tfvars" -auto-approve

terraform destroy -var-file="secrets.tfvars"
# OR
terraform destroy -var-file="secrets.tfvars" -auto-approve
```