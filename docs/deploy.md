# Deploy

We'll use Terraform:

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