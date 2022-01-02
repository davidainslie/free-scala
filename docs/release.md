# Release

Latest release:
```shell
sbt release
```

Cross release:
```shell
sbt release cross
```

Release with defaults (as the above ask release questions):
```shell
sbt "release cross with-defaults"
```

## Publish

The build includes the [Daniel Spiewak plugin](https://dev.to/gjuoun/publish-your-scala-library-to-github-packages-4p80) to publish to GitHub Packages.

We can directly issue a `publish` command, where the above release process includes this:
```shell
sbt +publish
```