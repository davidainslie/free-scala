package tech.backwards.docker.db

import scala.util.Try
import cats.effect.{IO, Resource}
import cats.implicits._
import doobie.ExecutionContexts
import doobie.hikari.HikariTransactor
import com.dimafeng.testcontainers.{JdbcDatabaseContainer, PostgreSQLContainer}

abstract class WithPostgresContainer(initScriptPath: Option[String] = None) {
  val container: PostgreSQLContainer = {
    val container: PostgreSQLContainer =
      PostgreSQLContainer.Def(commonJdbcParams = JdbcDatabaseContainer.CommonParams(initScriptPath = initScriptPath)).createContainer()

    import container._

    start()

    scribe.info(s"Booted PostgreSQLContainer: $containerId")
    scribe.info(s"$jdbcUrl\n$databaseName\n$username:$password")

    sys addShutdownHook (Try(scribe.info(s"Stopping PostgreSQLContainer: $containerId")) *> Try(stop())).fold(throw _, identity)

    container
  }

  lazy val postgresResource: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        container.jdbcUrl,
        container.username,
        container.password,
        ce
      )
    } yield xa
}