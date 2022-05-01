package tech.backwards.algebra.interpreter

import scala.concurrent.duration._
import cats.data.EitherK
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.free.Free
import cats.implicits._
import cats.{InjectK, ~>}
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import io.circe.literal.JsonStringContext
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{HttpError, SttpBackend}
import sttp.model.Method._
import sttp.model.StatusCode
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers.{ForAllTestContainer, LocalStackContainer}
import tech.backwards.auth.{Credentials, Password, User}
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3._
import tech.backwards.aws.s3.interpreter.S3IOInterpreter
import tech.backwards.docker.aws.scalatest.AwsContainer
import tech.backwards.fp.free.FreeOps.syntax._
import tech.backwards.http.Http.Get._
import tech.backwards.http.Http._
import tech.backwards.http.SttpBackendStubOps.syntax._
import tech.backwards.http.{Auth, Bearer, Http}
import tech.backwards.json.JsonOps.syntax._
import tech.backwards.json.Jsonl
import tech.backwards.serialisation.Deserialiser
import tech.backwards.util.EitherOps.syntax._

class HttpS3IOIntegrationSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with ForAllTestContainer with AwsContainer {
  override val container: LocalStackContainer =
    LocalStackContainer(services = List(Service.S3))

  "Coproduct Algebras (in this case of Http and S3)" should {
    "be applied against async interpreters" in withS3(container) { s3Client =>
      import tech.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword

      type Algebras[A] = EitherK[Http, S3, A]

      // Example of paginating a Http Get
      implicit class GetOps[F[_]: InjectK[Http, *[_]]](get: Get[Json])(implicit D: Deserialiser[Json]) {
        def paginate: Free[F, Vector[Json]] = {
          def accumulate(acc: Vector[Json], json: Json): Vector[Json] =
            (json \ "data").flatMap(_.asArray).fold(acc)(acc ++ _)

          def go(get: Get[Json], acc: Vector[Json], skip: Int, limit: Int): Free[F, Vector[Json]] =
            for {
              content <- paramsL[Json].modify(_ + ("skip" -> skip) + ("limit" -> limit))(get)
              data    <- if (skip < 50) go(get, accumulate(acc, content), skip + 10, limit) else Free.pure[F, Vector[Json]](accumulate(acc, content))
            } yield data

          go(get, acc = Vector.empty, skip = 0, limit = 10)
        }
      }

      object SttpInterpreter {
        val bearer: Bearer =
          Bearer("token", 1 hour)

        val dataEntry1 =
          json"""{ "id": "1" }"""

        val dataEntry2 =
          json"""{ "id": "2" }"""

        val data: Json =
          Json.obj("data" -> Json.arr(dataEntry1, dataEntry2))

        val backend: SttpBackend[IO, Any] =
          AsyncHttpClientCatsBackend.stub[IO]
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenJsonRespond(bearer)
            .whenRequestMatchesAll(_.method == GET, _.uri.path.startsWith(List("api", "execute")))
            .thenJsonRespond(data)
            .logging

        def apply(): Http ~> IO =
          tech.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
        for {
          bucket    <- bucket("my-bucket").toFree[Algebras]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](uri("https://backwards.com/api/execute")).paginate
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
          response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
        } yield response

      S3IOInterpreter.resource(s3Client).use(s3Interpreter => program.foldMap(SttpInterpreter() or s3Interpreter)).map(
        _.value must contain allOf (SttpInterpreter.dataEntry1, SttpInterpreter.dataEntry2)
      )
    }

    "be applied against async interpreters where Http exceptions are captured via MonadError" in withS3(container) { s3Client =>
      import tech.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword

      type Algebras[A] = EitherK[Http, S3, A]

      object SttpInterpreter {
        val backend: SttpBackend[IO, Any] =
          AsyncHttpClientCatsBackend.stub[IO]
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenRespondServerError()
            .logging

        def apply(): Http ~> IO =
          tech.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Unit] =
        for {
          bucket    <- bucket("my-bucket").toFree[Algebras]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          _         <- Get[Json](uri("https://backwards.com/api/execute"))
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString("Won't reach here"))
          response  <- GetObject[Unit](getObjectRequest(bucket, "foo")) // As the test won't reach here, we don't care about the return
        } yield response

      S3IOInterpreter.resource(s3Client).use(s3Interpreter => program.foldMap(SttpInterpreter() or s3Interpreter)).attempt.map(_.leftValue).map {
        case HttpError(_, statusCode) => statusCode mustEqual StatusCode.InternalServerError
      }
    }

    "be applied against async interpreters where S3 exceptions are captured via MonadError" in withS3(container) { s3Client =>
      import tech.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword

      type Algebras[A] = EitherK[Http, S3, A]

      object SttpInterpreter {
        val bearer: Bearer =
          Bearer("token", 1 hour)

        val dataEntry1 =
          json"""{ "id": "1" }"""

        val dataEntry2 =
          json"""{ "id": "2" }"""

        val data: Json =
          Json.obj("data" -> Json.arr(dataEntry1, dataEntry2))

        val backend: SttpBackend[IO, Any] =
          AsyncHttpClientCatsBackend.stub[IO]
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenJsonRespond(bearer)
            .whenRequestMatchesAll(_.method == GET, _.uri.path.startsWith(List("api", "execute")))
            .thenJsonRespond(data)
            .logging

        def apply(): Http ~> IO =
          tech.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Unit] =
        for {
          bucket    <- bucket("my-bucket").toFree[Algebras]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](uri("https://backwards.com/api/execute"))
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString((data \ "data").flatMap(_.asArray).combineAll.map(_.noSpaces).mkString("\n")))
          response  <- GetObject[Unit](getObjectRequest(bucket, "WHOOPS")) // As the test won't reach here, we don't care about the return
        } yield response

      S3IOInterpreter.resource(s3Client).use(s3Interpreter => program.foldMap(SttpInterpreter() or s3Interpreter)).attempt.map(_.leftValue)
        .map(_ mustBe a [NoSuchKeyException])
    }
  }
}