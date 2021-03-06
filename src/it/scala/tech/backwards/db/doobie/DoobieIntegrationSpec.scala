package tech.backwards.db.doobie

import cats.effect._
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.implicits._
import doobie._
import org.flywaydb.core.Flyway
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.testcontainers.utility.DockerImageName
import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}

class DoobieIntegrationSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with ForAllTestContainer {
  override val container: PostgreSQLContainer =
    PostgreSQLContainer(DockerImageName.parse("postgres:14-alpine3.15"))

  // TODO - WIP
  "Blah" should {
    "blah" in {
      IO {
        Flyway
          .configure()
          .dataSource(container.jdbcUrl, container.username, container.password)
          .locations("classpath:db/migrations")
          .load()
          .migrate()

        println(container.jdbcUrl)
        println(container.portBindings)

        // jdbc:postgresql://localhost:59307/test?loggerLevel=OFF
        // databaseName = "test"; username = "test"; password = "test"; initScriptPath =
        println(container.jdbcUrl)

        val transactor: Transactor[IO] =
          Transactor.fromDriverManager[IO](
            container.driverClassName,
            container.jdbcUrl, // "jdbc:postgresql://localhost:5432/world-db",
            "world",
            "world123"
          )

        1 mustBe 1
      }
    }
  }
}