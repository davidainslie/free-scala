import sbt.url

ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organizationName := "davidainslie"
ThisBuild / organizationHomepage := Some(url("https://github.com/davidainslie/free-scala"))
ThisBuild / homepage := Some(url("https://github.com/davidainslie/free-scala"))
ThisBuild / licenses := List("The Unlicense" -> new URL("https://unlicense.org/"))
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true
ThisBuild / sonatypeRepository := "https://s01.oss.sonatype.org/service/local"
ThisBuild / sonatypeCredentialHost := "s01.oss.sonatype.org"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/davidainslie/free-scala"),
    "scm:git@github.davidainslie/free-scala.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id    = "davidainslie",
    name  = "David Ainslie",
    email = "dainslie@gmail.com",
    url   = url("https://github.com/davidainslie/free-scala")
  )
)

// TODO - Check I'm correctly removing this
ThisBuild / publishTo := sonatypePublishToBundle.value

/*ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}*/

// To sync with Maven central, supply the following information:
Global / pomExtra := {
  <url>https://github.com/davidainslie/free-scala</url>
    <licenses>
      <license>
        <name>Apache License Version 2.0</name>
        <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>

    <scm>
      <connection>scm:git:github.com/davidainslie/free-scala</connection>
      <developerConnection>scm:git:git@github.com:davidainslie/free-scala</developerConnection>
      <url>https://github.com/davidainslie/free-scala</url>
    </scm>

    <developers>
      <developer>
        <id>davidainslie</id>
        <name>David Ainslie</name>
        <url>https://github.com/davidainslie/</url>
      </developer>
    </developers>
}