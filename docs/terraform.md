# Terraform (CDK)

Did you install the Terraform CDK?
```shell
brew install cdktf
```

We'll end up with Scala, but for the time being we start off a demo in [Java](https://github.com/hashicorp/terraform-cdk/blob/main/docs/getting-started/java.md).

[Example](https://github.com/davidainslie/terraform-cdk-demo) from following [Quick Start Demo](https://learn.hashicorp.com/tutorials/terraform/cdktf-install?in=terraform/cdktf) as a standalone project, to get a feel for the CDK:
```shell
# We do the following if the project has not been cloned from GitHub:
mkdir terraform-cdk-demo
cd terraform-cdk-demo

cdktf init --template=java --local
```

If successfully initialised, you will see:
```shell
Your cdktf Java project is ready!

cat help                Prints this message

Compile:
mvn compile           Compiles your Java packages

Synthesize:
cdktf synth [stack]   Synthesize Terraform resources to cdktf.out/

Diff:
cdktf diff [stack]    Perform a diff (terraform plan) for the given stack

Deploy:
cdktf deploy [stack]  Deploy the given stack

Destroy:
cdktf destroy [stack] Destroy the given stack

Learn more about using modules and providers https://cdk.tf/modules-and-providers
```

Install the Docker provider for cdktf:
```shell
npm install @cdktf/provider-docker
```

Add required `providers` to `cdktf.json e.g.
```shell
{
  "language": "java",
  "app": "mvn -e -q compile exec:java",
  "projectId": "8aadb452-9b95-4818-a838-508dcd72715b",
  "terraformProviders": [
    "aws@~> 3.45",
    "kreuzwerker/docker@~> 2.13.0"
  ],
  "terraformModules": [],
  "context": {
    "excludeStackIdFromLogicalIds": "true",
    "allowSepCharsInLogicalIds": "true"
  }
}
```
and then (to download the plugins):
```shell
cdktf get
```

Finally:
```shell
cdktf deploy

Deploying Stack: terraform-cdk-demo
Resources
 ✔ DOCKER_CONTAINER     nginxContainer      docker_container.nginxContainer
 ✔ DOCKER_IMAGE         nginxImage 
```

```shell
http localhost:8000
```

Double check Docker:
```shell
docker container ls -a
                           
CONTAINER ID   IMAGE          COMMAND                  CREATED         STATUS         PORTS                  NAMES
7305036df96c   c919045c4c2b   "/docker-entrypoint.…"   3 minutes ago   Up 3 minutes   0.0.0.0:8000->80/tcp   tutorial
```

As usual, don't forget:
```shell
cdktf destroy
```