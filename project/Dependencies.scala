import scala.collection.LinearSeq
import sbt._

object Dependencies {
  def apply(): Seq[ModuleID] =
    LinearSeq(
      scalaMeta, scalatest, specs2, munit, scalatestContainers,
      log4Cats, scribe, pprint,
      cats, catsEffect, catsEffectTesting, catsRetry, kittens, catnip, mouse,
      simulacrum, refined, monocle, shapeless, newtype,
      jaxb, circe,
      fs2, betterFiles, sttp, awsJava, awsJavaLegacy, s3StreamUpload, doobie, postgresql, flyway
    ).flatten

  lazy val scalaMeta: Seq[ModuleID] =
    LinearSeq("org.scalameta" %% "scalameta" % "4.5.2")

  lazy val scalatest: Seq[ModuleID] =
    LinearSeq("org.scalatest" %% "scalatest" % "3.2.11" % "test, it" withSources() withJavadoc())

  lazy val specs2: Seq[ModuleID] = {
    val group = "org.specs2"
    val version = "4.15.0"

    LinearSeq(
      "specs2-core", "specs2-scalacheck", "specs2-matcher-extra", "specs2-cats", "specs2-shapeless"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val munit: Seq[ModuleID] =
    LinearSeq("org.scalameta" %% "munit" % "0.7.29" % "test, it" withSources() withJavadoc())

  lazy val scalatestContainers: Seq[ModuleID] = {
    val group = "com.dimafeng"
    val version = "0.40.4"

    LinearSeq(
      "testcontainers-scala-scalatest", "testcontainers-scala-munit",
      "testcontainers-scala-localstack", "testcontainers-scala-localstack-v2",
      "testcontainers-scala-mockserver",
      "testcontainers-scala-postgresql"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc()) ++ LinearSeq(
      "io.chrisdavenport" %% "testcontainers-specs2" % "0.2.0-M3" % "test, it"
    )
  }

  lazy val log4Cats: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "2.2.0"

    LinearSeq(
      "log4cats-core", "log4cats-slf4j"
    ).map(group %% _ % version)
  }

  lazy val scribe: Seq[ModuleID] =
    LinearSeq("com.outr" %% "scribe" % "3.8.2" withSources() withJavadoc())

  lazy val pprint: Seq[ModuleID] =
    LinearSeq("com.lihaoyi" %% "pprint" % "0.7.3")

  lazy val cats: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "2.7.0"

    LinearSeq(
      "cats-core", "cats-free"
    ).map(group %% _ % version withSources() withJavadoc()) ++ LinearSeq(
      "cats-laws", "cats-testkit"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val catsEffect: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "3.3.9"

    LinearSeq(
      "cats-effect"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val catsEffectTesting: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "1.4.0"

    LinearSeq(
      "cats-effect-testing-scalatest"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val catsRetry: Seq[ModuleID] =
    LinearSeq("com.github.cb372" %% "cats-retry" % "3.1.0")

  lazy val kittens: Seq[ModuleID] =
    LinearSeq("org.typelevel" %% "kittens" % "2.3.2")

  lazy val catnip: Seq[ModuleID] =
    LinearSeq("io.scalaland" %% "catnip" % "1.1.2")

  lazy val mouse: Seq[ModuleID] =
    LinearSeq("org.typelevel" %% "mouse" % "1.0.10" withSources() withJavadoc())

  lazy val simulacrum: Seq[ModuleID] =
    LinearSeq("org.typelevel" %% "simulacrum" % "1.0.1" withSources() withJavadoc())

  lazy val refined: Seq[ModuleID] = {
    val group = "eu.timepit"
    val version = "0.9.28"

    LinearSeq(
      "refined", "refined-cats"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val monocle: Seq[ModuleID] = {
    val group = "dev.optics"
    val version = "3.1.0"

    LinearSeq(
      "monocle-core", "monocle-macro", "monocle-generic"
    ).map(group %% _ % version withSources() withJavadoc()) ++ LinearSeq(
      "monocle-law"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val shapeless: Seq[ModuleID] =
    LinearSeq("com.chuusai" %% "shapeless" % "2.3.9")

  lazy val newtype: Seq[ModuleID] =
    LinearSeq("io.estatico" %% "newtype" % "0.4.4")

  lazy val jaxb: Seq[ModuleID] =
    LinearSeq("javax.xml.bind" % "jaxb-api" % "2.4.0-b180830.0359")

  lazy val circe: Seq[ModuleID] = {
    val group = "io.circe"
    val version = "0.14.1"

    LinearSeq(
      "circe-core", "circe-generic", "circe-generic-extras", "circe-parser", "circe-refined", "circe-literal"
    ).map(group %% _ % version withSources() withJavadoc()) ++ List(
      "circe-testing"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val fs2: Seq[ModuleID] = {
    val group = "co.fs2"
    val version = "3.2.7"

    LinearSeq(
      "fs2-core", "fs2-io", "fs2-reactive-streams"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val betterFiles: Seq[ModuleID] =
    LinearSeq("com.github.pathikrit" %% "better-files" % "3.9.1" withSources() withJavadoc())

  lazy val sttp: Seq[ModuleID] = {
    val group = "com.softwaremill.sttp.client3"
    val version = "3.5.1"

    LinearSeq(
      "core", "circe", "scribe-backend",
      "okhttp-backend", "async-http-client-backend-future",
      "async-http-client-backend-cats", "async-http-client-backend-fs2"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val awsJava: Seq[ModuleID] = {
    val group = "software.amazon.awssdk"
    val version = "2.17.162"

    LinearSeq(
      "aws-core", "sdk-core", "regions", "auth", "utils", "s3"
    ).map(group % _ % version withSources() withJavadoc())
  }

  lazy val awsJavaLegacy: Seq[ModuleID] = {
    val group = "com.amazonaws"
    val version = "1.12.191"

    LinearSeq(
      "aws-java-sdk-core"
    ).map(group % _ % version withSources() withJavadoc())
  }

  lazy val s3StreamUpload: Seq[ModuleID] =
    List("com.github.alexmojaki" % "s3-stream-upload" % "2.2.4")

  lazy val doobie: Seq[ModuleID] = {
    val group = "org.tpolecat"
    val version = "1.0.0-RC1"

    LinearSeq(
      "doobie-core", "doobie-h2", "doobie-postgres", "doobie-hikari", "doobie-quill", "doobie-free"
    ).map(group %% _ % version withSources() withJavadoc()) ++ LinearSeq(
      "doobie-specs2", "doobie-scalatest"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val postgresql: Seq[ModuleID] =
    LinearSeq("org.postgresql" % "postgresql" % "42.3.3")

  lazy val flyway: Seq[ModuleID] =
    LinearSeq("com.github.geirolz" %% "fly4s-core" % "0.0.12")
}