# Build

## Environment

Any environment variables that should not be saved in **Git** should be in a **.env** file - a hidden file.
If necessary, you may need other **environments** such as **.env-local** and **.env-dev** etc.

## SBT

Unit test:
```shell
sbt test
```

Integration test:
```shell
sbt it:test
```

Run IT apps which connect to a test Http API and LocalStack:
```shell
sbt "it:runMain com.backwards.algebra.interpreter.CoproductIOInterpreterITApp"

sbt "it:runMain com.backwards.algebra.interpreter.CoproductIOStreamInterpreterITApp"
```

Run real world demo application connecting to test Http API and AWS:
```shell
sbt "runMain com.backwards.algebra.interpreter.CoproductIOStreamInterpreterApp"
```