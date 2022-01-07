package com.backwards.algebra.interpreter

import scala.concurrent.duration._
import cats.data.EitherK
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.free.Free
import cats.implicits._
import cats.{InjectK, ~>}
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import io.circe.literal.JsonStringContext
import io.circe.parser._
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.{GetObjectResponse, NoSuchKeyException}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{HttpError, SttpBackend}
import sttp.model.Method._
import sttp.model.StatusCode
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers.{ForAllTestContainer, LocalStackContainer}
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3._
import com.backwards.aws.s3.interpreter.S3IOInterpreter
import com.backwards.docker.aws.AwsContainer
import com.backwards.fp.free.FreeOps.syntax._
import com.backwards.http.Http.Get._
import com.backwards.http.Http._
import com.backwards.http.SttpBackendStubOps.syntax._
import com.backwards.http.{Auth, Bearer, Http}
import com.backwards.json.JsonOps.syntax._
import com.backwards.util.EitherOps.syntax._
import com.backwards.{aws, http}

class CoproductIOInterpreterIT extends AnyWordSpec with Matchers with ForAllTestContainer with AwsContainer {
  override val container: LocalStackContainer =
    LocalStackContainer(services = List(Service.S3))

  "Coproduct Algebras (in this case of Http and S3)" should {
    "be applied against async interpreters" in withS3(container) { s3Client =>
      import com.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword

      type Algebras[A] = EitherK[Http, S3, A]

      // Example of paginating a Http Get
      implicit class GetOps[F[_]: InjectK[Http, *[_]]](get: Get[Json])(implicit D: http.Deserialiser[Json]) {
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
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
        for {
          bucket    <- Bucket("my-bucket").liftFree[Algebras]
          _         <- CreateBucket(CreateBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](uri("https://backwards.com/api/execute")).paginate
          _         <- PutObject(PutObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
          response  <- GetObject(GetObjectRequest(bucket, "foo"))
        } yield response

      S3IOInterpreter.resource(s3Client).use(s3Interpreter => program.foldMap(SttpInterpreter() or s3Interpreter))
        .map(response =>
          new String(response.readAllBytes).split("\n").map(parse(_).rightValue) must contain allOf (SttpInterpreter.dataEntry1, SttpInterpreter.dataEntry2)
      ).unsafeRunSync()
    }

    "be applied against async interpreters where Http exceptions are captured via MonadError" in withS3(container) { s3Client =>
      import com.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword

      type Algebras[A] = EitherK[Http, S3, A]

      object SttpInterpreter {
        val backend: SttpBackend[IO, Any] =
          AsyncHttpClientCatsBackend.stub[IO]
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenRespondServerError()
            .logging

        def apply(): Http ~> IO =
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
        for {
          bucket    <- Bucket("my-bucket").liftFree[Algebras]
          _         <- CreateBucket(aws.s3.CreateBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          _         <- Get[Json](uri("https://backwards.com/api/execute"))
          _         <- PutObject(aws.s3.PutObjectRequest(bucket, "foo"), RequestBody.fromString("Won't reach here"))
          response  <- GetObject(aws.s3.GetObjectRequest(bucket, "foo"))
        } yield response

      S3IOInterpreter.resource(s3Client).use(s3Interpreter => program.foldMap(SttpInterpreter() or s3Interpreter)).attempt.map(_.leftValue)
        .map { case HttpError(_, statusCode) => statusCode mustEqual StatusCode.InternalServerError }.unsafeRunSync()
    }

    "be applied against async interpreters where S3 exceptions are captured via MonadError" in withS3(container) { s3Client =>
      import com.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword

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
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
        for {
          bucket    <- Bucket("my-bucket").liftFree[Algebras]
          _         <- CreateBucket(aws.s3.CreateBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](uri("https://backwards.com/api/execute"))
          _         <- PutObject(aws.s3.PutObjectRequest(bucket, "foo"), RequestBody.fromString((data \ "data").flatMap(_.asArray).combineAll.map(_.noSpaces).mkString("\n")))
          response  <- GetObject(aws.s3.GetObjectRequest(bucket, "WHOOPS"))
        } yield response

      S3IOInterpreter.resource(s3Client).use(s3Interpreter => program.foldMap(SttpInterpreter() or s3Interpreter))
        .attempt.map(_.leftValue).map(_ mustBe a [NoSuchKeyException]).unsafeRunSync()
    }
  }
}