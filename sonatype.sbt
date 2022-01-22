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
// publishTo := sonatypePublishToBundle.value

ThisBuild / publishTo := {
  val nexus = "https://s01.oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}



