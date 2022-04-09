package tech.backwards.algebra.interpreter

import scala.concurrent.duration._
import cats.data.EitherK
import cats.free.Free
import cats.implicits._
import cats.{Id, InjectK, ~>}
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import io.circe.literal.JsonStringContext
import software.amazon.awssdk.core.sync.RequestBody
import sttp.client3.SttpBackend
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method._
import org.scalatest.Inspectors
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers.{ForAllTestContainer, LocalStackContainer}
import tech.backwards.auth.{Credentials, Password, User}
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3._
import tech.backwards.aws.s3.interpreter.S3Interpreter
import tech.backwards.docker.aws.scalatest.AwsContainer
import tech.backwards.fp.free.FreeOps.syntax._
import tech.backwards.fp.implicits.monadCancelId
import tech.backwards.http.CredentialsSerialiser.serialiserCredentialsByClientCredentials
import tech.backwards.http.Http.Get._
import tech.backwards.http.Http._
import tech.backwards.http.SttpBackendStubOps.syntax._
import tech.backwards.http.{Auth, Bearer, Http}
import tech.backwards.json.JsonOps.syntax._
import tech.backwards.json.Jsonl
import tech.backwards.serialisation.Deserialiser

class AlgebrasInterpreterIT extends AnyWordSpec with Matchers with Inspectors with ForAllTestContainer with AwsContainer {
  override val container: LocalStackContainer =
    LocalStackContainer(services = List(Service.S3))

  "Coproduct Algebras (in this case of Http and S3)" should {
    "be applied against sync interpreters" in withS3[Id](container) { s3Client =>
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
          tech.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
        for {
          bucket    <- bucket("my-bucket").liftFree[Algebras]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](uri("https://backwards.com/api/execute")).paginate
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
          response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
        } yield response

      val response: Jsonl =
        S3Interpreter.resource(s3Client).use(s3Interpreter => program.foldMap(SttpInterpreter() or s3Interpreter))

      response.value must contain allOf (SttpInterpreter.dataEntry1, SttpInterpreter.dataEntry2)
    }
  }
}