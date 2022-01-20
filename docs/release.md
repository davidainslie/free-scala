# Release

[Sonatype](https://issues.sonatype.org/secure/Dashboard.jspa)

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
sbt "release with-defaults"
OR
sbt "release cross with-defaults"
```

## Publish

The build includes the [Daniel Spiewak plugin](https://dev.to/gjuoun/publish-your-scala-library-to-github-packages-4p80) to publish to GitHub Packages.

We can directly issue a `publish` command, where the above release process includes this:
```shell
sbt +publish


sbt publishSigned

brew install pinentry-mac

Added to profile:
GPG_TTY=$(tty)
export GPG_TTY


If you do not own this domain, you may also choose a different Group Id that reflects your project hosting. io.github.davidainslie would be valid based on your Project URL.
To continue the registration process, please follow these steps:
Create a temporary, public repository called https://github.com/davidainslie/OSSRH-77460 to verify github account ownership.

https://central.sonatype.org/faq/how-to-set-txt-record/

To check TXT record added correctly:
dig -t txt backwards.tech
```