# Build

## Environment

Any environment variables that should not be saved in **Git** should be in a **.env** file - a hidden file.
If necessary, you may need other **environments** such as **.env-local** and **.env-dev** etc.

AWS Credentials are aquired via the Java SDK `credentials provider chain` e.g. check system properties, then environment variables and so on, for:
- AWS_PROFILE
- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY

TODO - Must expand on the above

## SBT

Unit test:
```shell
sbt test
```

Integration test:
```shell
sbt it:test
```

Run IT apps (of multiple Algebra) which connect to a test Http API and LocalStack:
```shell
sbt "it:runMain tech.backwards.algebra.interpreter.AlgebrasIOInterpreterITApp"

sbt "it:runMain tech.backwards.algebra.interpreter.AlgebrasIOStreamInterpreterITApp"
```

Run real world demo application (of multiple Algebra) connecting to test Http API and AWS:
```shell
sbt "runMain tech.backwards.algebra.interpreter.AlgebrasIOStreamInterpreterApp"
```

## Docker

```shell
sbt docker:publishLocal
```

```shell
docker run -it --rm \
	-e AWS_ACCESS_KEY_ID= \
	-e AWS_SECRET_ACCESS_KEY= \
	-e AWS_REGION=eu-west-2 \
	-e AWS_BUCKET= \
	free-scala:latest
```

Quick note on the above commands usage in Terraform.
We use `local` variables generated from running a local script that accesses AWS credentials based on `profile`.
This [link](https://www.cloudwalker.io/2021/10/09/terraform-external-data-source/) explains the idea.