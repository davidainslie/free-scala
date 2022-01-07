import sbt._

object Dependencies {
  def apply(): Seq[ModuleID] =
    List(
      scalaMeta, scalatest, specs2, munit, scalatestContainers,
      log4Cats, scribe, pprint,
      cats, catsEffect, catsEffectTesting, catsRetry, kittens, catnip, mouse,
      simulacrum, refined, monocle, shapeless,
      jaxb, circe, fs2, betterFiles, sttp, awsJava, awsJavaLegacy, s3StreamUpload
    ).flatten

  lazy val scalaMeta: Seq[ModuleID] =
    List("org.scalameta" %% "scalameta" % "4.4.31")

  lazy val scalatest: Seq[ModuleID] =
    List("org.scalatest" %% "scalatest" % "3.2.10" % "test, it" withSources() withJavadoc())

  lazy val specs2: Seq[ModuleID] = {
    val group = "org.specs2"
    val version = "4.13.1"

    List(
      "specs2-core", "specs2-scalacheck", "specs2-matcher-extra", "specs2-cats", "specs2-shapeless"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val munit: Seq[ModuleID] =
    List("org.scalameta" %% "munit" % "0.7.29" % "test, it" withSources() withJavadoc())

  lazy val scalatestContainers: Seq[ModuleID] = {
    val group = "com.dimafeng"
    val version = "0.39.12"

    List(
      "testcontainers-scala-scalatest", "testcontainers-scala-munit",
      "testcontainers-scala-localstack", "testcontainers-scala-localstack-v2",
      "testcontainers-scala-mockserver"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc()) ++ List(
      "io.chrisdavenport" %% "testcontainers-specs2" % "0.2.0-M3" % "test, it"
    )
  }

  lazy val log4Cats: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "2.1.1"

    List(
      "log4cats-core", "log4cats-slf4j"
    ).map(group %% _ % version)
  }

  lazy val scribe: Seq[ModuleID] =
    List("com.outr" %% "scribe" % "3.6.4" withSources() withJavadoc())

  lazy val pprint: Seq[ModuleID] =
    List("com.lihaoyi" %% "pprint" % "0.7.1")

  lazy val cats: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "2.7.0"

    List(
      "cats-core", "cats-free"
    ).map(group %% _ % version withSources() withJavadoc()) ++ List(
      "cats-laws", "cats-testkit"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val catsEffect: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "3.3.1"

    List(
      "cats-effect"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val catsEffectTesting: Seq[ModuleID] = {
    val group = "org.typelevel"
    val version = "1.4.0"

    List(
      "cats-effect-testing-scalatest"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val catsRetry: Seq[ModuleID] =
    List("com.github.cb372" %% "cats-retry" % "3.1.0")

  lazy val kittens: Seq[ModuleID] =
    List("org.typelevel" %% "kittens" % "2.3.2")

  lazy val catnip: Seq[ModuleID] =
    List("io.scalaland" %% "catnip" % "1.1.2")

  lazy val mouse: Seq[ModuleID] =
    List("org.typelevel" %% "mouse" % "1.0.8" withSources() withJavadoc())

  lazy val simulacrum: Seq[ModuleID] =
    List("org.typelevel" %% "simulacrum" % "1.0.1" withSources() withJavadoc())

  lazy val refined: Seq[ModuleID] = {
    val group = "eu.timepit"
    val version = "0.9.28"

    List(
      "refined", "refined-cats"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val monocle: Seq[ModuleID] = {
    val group = "com.github.julien-truffaut"
    val version = "2.1.0"

    List(
      "monocle-core", "monocle-macro", "monocle-generic"
    ).map(group %% _ % version withSources() withJavadoc()) ++ List(
      "monocle-law"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val shapeless: Seq[ModuleID] =
    List("com.chuusai" %% "shapeless" % "2.3.7")

  lazy val jaxb: Seq[ModuleID] =
    List("javax.xml.bind" % "jaxb-api" % "2.4.0-b180830.0359")

  lazy val circe: Seq[ModuleID] = {
    val group = "io.circe"
    val version = "0.14.1"

    List(
      "circe-core", "circe-generic", "circe-generic-extras", "circe-parser", "circe-refined", "circe-literal"
    ).map(group %% _ % version withSources() withJavadoc()) ++ List(
      "circe-testing"
    ).map(group %% _ % version % "test, it" withSources() withJavadoc())
  }

  lazy val fs2: Seq[ModuleID] = {
    val group = "co.fs2"
    val version = "3.2.4"

    List(
      "fs2-core", "fs2-io", "fs2-reactive-streams"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val betterFiles: Seq[ModuleID] =
    List("com.github.pathikrit" %% "better-files" % "3.9.1" withSources() withJavadoc())

  lazy val sttp: Seq[ModuleID] = {
    val group = "com.softwaremill.sttp.client3"
    val version = "3.3.18"

    List(
      "core", "circe", "scribe-backend",
      "okhttp-backend", "async-http-client-backend-future",
      "async-http-client-backend-cats", "async-http-client-backend-fs2"
    ).map(group %% _ % version withSources() withJavadoc())
  }

  lazy val awsJava: Seq[ModuleID] = {
    val group = "software.amazon.awssdk"
    val version = "2.17.102"

    List(
      "aws-core", "sdk-core", "regions", "auth", "utils", "s3"
    ).map(group % _ % version withSources() withJavadoc())
  }

  lazy val awsJavaLegacy: Seq[ModuleID] = {
    val group = "com.amazonaws"
    val version = "1.12.131"

    List(
      "aws-java-sdk-core"
    ).map(group % _ % version withSources() withJavadoc())
  }

  lazy val s3StreamUpload: Seq[ModuleID] =
    List("com.github.alexmojaki" % "s3-stream-upload" % "2.2.4")
}