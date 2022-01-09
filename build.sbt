import sbt._

lazy val root: Project =
  project("free-scala", file("."))

lazy val thisScalaVersion: String =
  "2.13.7"

lazy val supportedScalaVersions: List[String] =
  List(thisScalaVersion, "2.12.15")

ThisBuild / evictionErrorLevel := Level.Info
ThisBuild / versionScheme := Some("early-semver")

def project(id: String, base: File): Project =
  Project(id, base)
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .configs(IntegrationTest extend Test)
    .settings(inConfig(IntegrationTest extend Test)(Defaults.testSettings))
    .settings(Defaults.itSettings)
    .settings(
      resolvers ++= Seq(
        Resolver sonatypeRepo "releases",
        Resolver sonatypeRepo "snapshots",
        "jitpack" at "https://jitpack.io",
        Resolver githubPackages "davidainslie"
      ),
      scalaVersion := thisScalaVersion,
      organization := "com.backwards",
      name := id,
      githubOwner := "davidainslie",
      githubRepository := "free-scala",
      githubTokenSource := TokenSource.Or(TokenSource.Environment("GITHUB_TOKEN"), TokenSource.GitConfig("github.token")),
      description := "Scala Free Monads by Backwards",
      crossScalaVersions := supportedScalaVersions,
      addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
      addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      libraryDependencies ++= Dependencies(),
      exportJars := true,
      scalacOptions ++= Seq(
        "-encoding", "utf8",
        "-deprecation",
        "-unchecked",
        "-language:implicitConversions",
        "-language:higherKinds",
        "-language:existentials",
        "-language:postfixOps",
        // "-Ymacro-annotations",
        "-Yrangepos",
        "-P:kind-projector:underscore-placeholders" // Can use _ instead of * when defining anonymous type lambdas
        // "-Xfatal-warnings"
        // "-Ywarn-value-discard"
      ),
      fork := true,
      Test / publishArtifact := true,
      IntegrationTest / publishArtifact := true,
      Compile / mainClass := Some("com.backwards.algebra.interpreter.CoproductIOStreamInterpreterApp"),
      addArtifact(IntegrationTest / packageBin / artifact, IntegrationTest / packageBin).settings,
      dockerBaseImage := "eclipse-temurin:17.0.1_12-jre-focal",
      Docker / maintainer := "Backwards",
      Docker / packageName := packageName.value,
      Docker / version := "latest" /*version.value*/, // TODO - Sort out hack for Terraform
      Docker / aggregate := false,
      dockerExposedPorts ++= Seq(9000, 9001)
      //Docker / dockerEnvVars := envVars.value
      // Docker / dockerRepository
      // Docker / dockerUsername
    )