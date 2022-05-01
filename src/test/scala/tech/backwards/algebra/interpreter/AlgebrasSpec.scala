package tech.backwards.algebra.interpreter

import cats.InjectK
import cats.data.EitherK
import cats.free.Free
import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import software.amazon.awssdk.core.sync.RequestBody
import tech.backwards.auth.{Credentials, Password, User}
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3._
import tech.backwards.fp.free.FreeOps.syntax._
import tech.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword
import tech.backwards.http.Http.Get._
import tech.backwards.http.Http._
import tech.backwards.http.{Auth, Http, StubHttpInterpreter}
import tech.backwards.json.JsonOps.syntax._
import tech.backwards.json.Jsonl
import tech.backwards.serialisation.Deserialiser
import org.scalatest.Inspectors
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AlgebrasSpec extends AnyWordSpec with Matchers with Inspectors {
  "Coproduct Algebras (in this case of Http and S3)" should {
    "be applied against stubbed interpreters" in {
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

      def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
        for {
          bucket    <- bucket("my-bucket").toFree[Algebras]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](uri("https://backwards.com/api/execute")).paginate
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
          response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
        } yield response

      val response: Jsonl =
        program.foldMap(StubHttpInterpreter or S3StubInterpreter)

      response.value must contain allOf (StubHttpInterpreter.dataEntry1, StubHttpInterpreter.dataEntry2)
    }

    "context bound alternative - be applied against stubbed interpreters" in {
      type Algebras[A] = EitherK[Http, S3, A]

      type `Http~>Algebras`[_] = InjectK[Http, Algebras]

      type `S3~>Algebras`[_] = InjectK[S3, Algebras]

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

      def program[F: `Http~>Algebras`: `S3~>Algebras`]: Free[Algebras, Jsonl] =
        for {
          bucket    <- bucket("my-bucket").toFree[Algebras]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data      <- Get[Json](uri("https://backwards.com/api/execute")).paginate
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
          response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
        } yield response

      val response: Jsonl =
        program.foldMap(StubHttpInterpreter or S3StubInterpreter)

      response.value must contain allOf (StubHttpInterpreter.dataEntry1, StubHttpInterpreter.dataEntry2)
    }
  }
}