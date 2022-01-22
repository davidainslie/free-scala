# Release

[Here](https://dev.to/awwsmm/publish-your-scala-project-to-maven-in-5-minutes-with-sonatype-326l) there are some things to watch our for.
And the [official guide](https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html).

We are using the following two plugins (along with `sbt-release`) to publish to [Sonatype](https://issues.sonatype.org/secure/Dashboard.jspa) at [Nexus](https://s01.oss.sonatype.org):
- addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.10")
- addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")

## Key

```shell
gpg --version

# Generate key
gpg --gen-key

# List keys
gpg --list-keys

# Distribute the key
gpg --keyserver keyserver.ubuntu.com --send-keys xxxxx
```

## Credentials

```shell
touch ~/.sbt/1.0/sonatype.sbt
code ~/.sbt/1.0/sonatype.sbt # Or whatever editor

# Add:
credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials")

touch ~/.sbt/sonatype_credentials
code ~/.sbt/sonatype_credentials

# Add:
realm=Sonatype Nexus Repository Manager
host=s01.oss.sonatype.org
user=davidainslie
password=xxxxx
```

Then configure by taking a look at [sonatype.sbt](../sonatype.sbt).

## Release and Publish

```shell
sbt "release with-defaults"

# Or to include "cross"
sbt "release cross with-defaults"
```

Under the hood, `release` will call:
```shell
sbt publishSigned
sbt sonatypeBundleRelease
```

View the release at:
https://s01.oss.sonatype.org/content/repositories/releases/tech/backwards/free-scala_2.13/

## Notes for New Project

To publish a new project to Sonatype, raise a ticket at https://issues.sonatype.org.

Example of filling in details along with validating your domain i.e. `group ID`:

| Type        | New Project                                    |
| ----------- | ---------------------------------------------- |
| Group Id    | tech.backwards                                 |
| Project URL | https://github.com/davidainslie/free-scala     |
| SCM url     | https://github.com/davidainslie/free-scala.git |
| Username    | davidainslie                                   |

To validate your domain:

- Add DNS TEXT record with text from the raised ticket

- To check the record was added correctly:

  - ```shell
    dig -t txt backwards.tech
    ```