package com.backwards.algebra.interpreter

import java.net.URI
import scala.concurrent.duration._
import scala.util.chaining.scalaUtilChainingOps
import cats.data.EitherK
import cats.free.Free
import cats.implicits._
import cats.{Id, InjectK, ~>}
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.literal.JsonStringContext
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import sttp.client3.SttpBackend
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method._
import org.scalatest.Inspectors
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers.{ForAllTestContainer, LocalStackContainer}
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.aws.s3.{Bucket, CreateBucketRequest, GetObjectRequest, PutObjectRequest, S3}
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3.interpreter.S3Interpreter
import com.backwards.docker.aws.AwsContainer
import com.backwards.fp.implicits.monadErrorId
import com.backwards.fp.free.FreeOps.syntax._
import com.backwards.http.Http.Get._
import com.backwards.http.Http._
import com.backwards.http.SttpBackendStubOps.syntax._
import com.backwards.http._
import com.backwards.io.Deserialiser
import com.backwards.json.JsonOps.syntax._
import com.backwards.io.URIOps.syntax._

class CoproductInterpreterIT extends AnyWordSpec with Matchers with Inspectors with ForAllTestContainer with AwsContainer {
  override val container: LocalStackContainer =
    LocalStackContainer(services = List(Service.S3))

  "Coproduct Algebras (in this case of Http and S3)" should {
    "be applied against sync interpreters" in withS3(container) { s3 =>
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

        val backend: SttpBackend[Id, Any] =
          SttpBackendStub[Id, Any](IdMonad)
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenJsonRespond(bearer)
            .whenRequestMatchesAll(_.method == GET, _.uri.path.startsWith(List("api", "execute")))
            .thenJsonRespond(data)
            .logging

        def apply(): Http ~> Id =
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
        for {
          bucket    <- Bucket("my-bucket").liftFree[Algebras]
          _         <- CreateBucket(CreateBucketRequest(bucket))
          _         <- GrantByPassword(URI.create("https://backwards.com/api/oauth2/access_token"), Credentials(User("user"), Password("password")))
          data      <- Get[Json](URI.create("https://backwards.com/api/execute")).paginate
          _         <- PutObject(PutObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
          response  <- GetObject(GetObjectRequest(bucket, "foo"))
        } yield response

      val response: Id[ResponseInputStream[GetObjectResponse]] =
        program.foldMap(SttpInterpreter() or S3Interpreter(s3))

      new String(response.readAllBytes).pipe(data =>
        forAll(List(SttpInterpreter.dataEntry1, SttpInterpreter.dataEntry2))(dataEntry =>
          data must include (dataEntry.noSpaces)
        )
      )
    }
  }
}