import sbt._

lazy val thisScalaVersion: String =
  "2.13.8"

lazy val supportedScalaVersions: List[String] =
  List(thisScalaVersion, "2.12.15")

lazy val root: Project =
  Project("free-scala", file("."))
    .enablePlugins(JavaAppPackaging, DockerPlugin)
    .settings(
      scalaVersion := thisScalaVersion,
      organization := "tech.backwards",
      description := "Scala Free Monads",
      // crossScalaVersions := supportedScalaVersions,
      evictionErrorLevel := Level.Info,
      versionScheme := Some("early-semver"),
      addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
      addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
      scalacOptions ++= Seq(
        "-encoding", "utf8",
        "-deprecation",
        "-unchecked",
        "-language:implicitConversions",
        "-language:higherKinds",
        "-language:existentials",
        "-language:postfixOps",
        "-Ymacro-annotations",
        "-Yrangepos",
        "-P:kind-projector:underscore-placeholders" // Can use _ instead of * when defining anonymous type lambdas
        //"-Xfatal-warnings"
        //"-Ywarn-value-discard"
      ),
      libraryDependencies ++= Dependencies(),
      dependencyCheckAssemblyAnalyzerEnabled := Some(false),
      exportJars := true,
      fork := true,
      Test / publishArtifact := true,
      IntegrationTest / publishArtifact := true,
      Compile / mainClass := Some("tech.backwards.algebra.interpreter.AlgebrasIOStreamInterpreterApp"),
      addArtifact(IntegrationTest / packageBin / artifact, IntegrationTest / packageBin).settings,
    )
    .configs(IntegrationTest extend Test)
    .settings(inConfig(IntegrationTest extend Test)(Defaults.testSettings): _*)
    .settings(Defaults.itSettings: _*)
    .settings(
      dockerBaseImage := "eclipse-temurin:17.0.1_12-jre-focal",
      Docker / maintainer := "Backwards",
      Docker / packageName := packageName.value,
      Docker / version := version.value,
      dockerUpdateLatest := true,
      Docker / aggregate := false,
      dockerExposedPorts ++= Seq(9000, 9001)
      // Docker / dockerEnvVars := envVars.value
      // Docker / dockerRepository
      // Docker / dockerUsername
    )