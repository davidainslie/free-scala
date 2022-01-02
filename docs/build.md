# Build

## Environment

Any environment variables that should not be saved in **Git** should be in a **.env** file - a hidden file.
If necessary, you may need other **environments** such as **.env-local** and **.env-dev** etc.

## SBT

Unit test:

```shell
$ sbt test
```

Integration test:

```shell
$ sbt it:test
```