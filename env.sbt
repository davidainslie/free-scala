import java.util.Properties
import scala.collection.JavaConverters._
import scala.util.chaining.scalaUtilChainingOps
import sbt._

/**
 * If you are using Intellij, use the following plugin to pick up the environment variables upon application execution:
 * https://plugins.jetbrains.com/plugin/7861-envfile
 */
val env: SettingKey[Map[String, String]] =
  settingKey[Map[String, String]]("Application environment variables")

// TODO - Different environments
env := {
  val env: Properties =
    new Properties()

  IO.load(env, new File(".env")).pipe(_ => env.asScala.toMap)
}

envVars := env.value