package com.backwards.algebra.interpreter

import java.net.URI
import scala.concurrent.duration._
import scala.util.chaining.scalaUtilChainingOps
import cats.data.EitherK
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.free.Free
import cats.implicits._
import cats.{InjectK, ~>}
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.literal.JsonStringContext
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.{GetObjectResponse, NoSuchKeyException}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{HttpError, SttpBackend}
import sttp.model.Method._
import sttp.model.StatusCode
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{EitherValues, Inspectors}
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
import com.backwards.{aws, http}

class CoproductIOInterpreterIT extends AnyWordSpec with Matchers with EitherValues with Inspectors with ForAllTestContainer with AwsContainer {
  override val container: LocalStackContainer =
    LocalStackContainer(services = List(Service.S3))

  "Coproduct Algebras (in this case of Http and S3)" should {
    "be applied against async interpreters" in withS3(container) { s3Client =>
      import com.backwards.http.CredentialsSerialiser.ByPassword._

      type Algebras[A] = EitherK[Http, S3, A]

      // Example of paginating a Http Get
      implicit class GetOps[F[_]: InjectK[Http, *[_]]](get: Get[Json])(implicit D: http.Deserialiser[Json]) {
        // TODO - Make tail recursive
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
          auth      <- Post[Credentials, Auth](URI.create("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](URI.create("https://backwards.com/api/execute")).paginate
          _         <- PutObject(PutObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
          response  <- GetObject(GetObjectRequest(bucket, "foo"))
        } yield response

      val response: IO[ResponseInputStream[GetObjectResponse]] =
        program.foldMap(SttpInterpreter() or S3IOInterpreter(s3Client))

      val Right(responseAttempt: ResponseInputStream[GetObjectResponse]) =
        response.attempt.unsafeRunSync()

      new String(responseAttempt.readAllBytes).pipe(data =>
        forAll(List(SttpInterpreter.dataEntry1, SttpInterpreter.dataEntry2))(dataEntry =>
          data must include (dataEntry.noSpaces)
        )
      )
    }

    "be applied against async interpreters where Http exceptions are captured via MonadError" in withS3(container) { s3Client =>
      import com.backwards.http.CredentialsSerialiser.ByPassword._

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
          auth      <- Post[Credentials, Auth](URI.create("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          _         <- Get[Json](URI.create("https://backwards.com/api/execute"))
          _         <- PutObject(aws.s3.PutObjectRequest(bucket, "foo"), RequestBody.fromString("Won't reach here"))
          response  <- GetObject(aws.s3.GetObjectRequest(bucket, "foo"))
        } yield response

      val response: IO[ResponseInputStream[GetObjectResponse]] =
        program.foldMap(SttpInterpreter() or S3IOInterpreter(s3Client))

      val Left(error: HttpError[_]) =
        response.attempt.unsafeRunSync()

      error.statusCode mustEqual StatusCode.InternalServerError
    }

    "be applied against async interpreters where S3 exceptions are captured via MonadError" in withS3(container) { s3Client =>
      import com.backwards.http.CredentialsSerialiser.ByClientCredentials._

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
          auth      <- Post[Credentials, Auth](URI.create("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](URI.create("https://backwards.com/api/execute"))
          _         <- PutObject(aws.s3.PutObjectRequest(bucket, "foo"), RequestBody.fromString((data \ "data").flatMap(_.asArray).combineAll.map(_.noSpaces).mkString("\n")))
          response  <- GetObject(aws.s3.GetObjectRequest(bucket, "WHOOPS"))
        } yield response

      val response: IO[ResponseInputStream[GetObjectResponse]] =
        program.foldMap(SttpInterpreter() or S3IOInterpreter(s3Client))

      // Our program fails when accessing the wrong key
      response.attempt.unsafeRunSync().left.value mustBe a [NoSuchKeyException]
    }
  }
}