package tech.backwards.algebra.interpreter

import java.net.URI
import cats.InjectK
import cats.data.EitherK
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.free._
import cats.implicits._
import doobie.ConnectionIO
import doobie.implicits._
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import software.amazon.awssdk.core.sync.RequestBody
import tech.backwards.auth.{Credentials, Password, User}
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3._
import tech.backwards.aws.s3.interpreter.S3IOInterpreter
import tech.backwards.db.doobie.interpreter.DoobieInterpreter
import tech.backwards.db.doobie.{Actor, DriverConfig}
import tech.backwards.docker.aws.scalatest.AwsContainer
import tech.backwards.fp.free.FreeOps.syntax._
import tech.backwards.json.Jsonl
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers._

class DoobieS3IntegrationSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with ForAllTestContainer with AwsContainer {
  lazy val postgreSqlContainer: PostgreSQLContainer =
    PostgreSQLContainer.Def(commonJdbcParams = JdbcDatabaseContainer.CommonParams(initScriptPath = "db/sql/schema-test-container.sql".some)).createContainer()

  lazy val s3Container: LocalStackContainer =
    LocalStackContainer.Def(services = List(Service.S3)).createContainer()

  val container: MultipleContainers =
    MultipleContainers(postgreSqlContainer, s3Container)

  "Coproduct Algebras (in this case PostgreSql and S3)" should {
    "read database and upload to S3" in withS3(s3Container) { s3Client =>
      import postgreSqlContainer._

      type Algebras[A] = EitherK[ConnectionIO, S3, A]

      def program(implicit C: InjectK[ConnectionIO, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
        for {
          bucket  <- bucket("my-bucket").toFree[Algebras]
          _       <- CreateBucket(createBucketRequest(bucket))
          actors  <- sql"select id, name from actors".query[Actor].to[List].injectFree[Algebras]
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString(actors.map(_.asJson.noSpaces).mkString("\n")))
          response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
        } yield
          response

      val driverConfig: DriverConfig[org.postgresql.Driver] =
        DriverConfig[org.postgresql.Driver](URI.create(jdbcUrl), Credentials(User(username), Password(password)))

      DoobieInterpreter[IO].resource(driverConfig).both(S3IOInterpreter.resource(s3Client)) use {
        case (doobieInterpreter, s3Interpreter) => program.foldMap(doobieInterpreter or s3Interpreter)
      } map {
        _.value.map(_.as[Actor]).collect {
          case Right(actor) => actor.name
        } must contain allOf ("Henry Cavill", "Gal Godot", "Ezra Miller", "Ben Affleck", "Ray Fisher", "Jason Momoa")
      }
    }

    "read database and stream to S3" in withS3(s3Container) { s3Client =>
      import postgreSqlContainer._

      type Algebras[A] = EitherK[ConnectionIO, S3, A]

      def program(implicit C: InjectK[ConnectionIO, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
        for {
          bucket    <- bucket("my-bucket").toFree[Algebras]
          _         <- CreateBucket(createBucketRequest(bucket))
          key       = "foo"
          _         <- sql"select id, name from actors".query[Actor].stream.map(actor =>
                          PutStream(bucket, key, actor.asJson)
                       ).as(CompletePutStream(bucket, key)).compile.drain.injectFree[Algebras]
          response  <- GetObject[Jsonl](getObjectRequest(bucket, key))
        } yield {
          pprint.pprintln(response)
          response
        }

      val driverConfig: DriverConfig[org.postgresql.Driver] =
        DriverConfig[org.postgresql.Driver](URI.create(jdbcUrl), Credentials(User(username), Password(password)))

      DoobieInterpreter[IO].resource(driverConfig).both(S3IOInterpreter.resource(s3Client)) use {
        case (doobieInterpreter, s3Interpreter) => program.foldMap(doobieInterpreter or s3Interpreter)
      } map {
        _.value.map(_.as[Actor]).collect {
          case Right(actor) => actor.name
        } must contain allOf ("Henry Cavill", "Gal Godot", "Ezra Miller", "Ben Affleck", "Ray Fisher", "Jason Momoa")
      }
    }
  }
}