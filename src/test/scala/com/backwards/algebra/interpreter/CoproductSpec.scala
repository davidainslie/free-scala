package com.backwards.algebra.interpreter

import java.net.URI
import scala.util.chaining.scalaUtilChainingOps
import cats.data.EitherK
import cats.free.Free
import cats.implicits._
import cats.{Id, InjectK}
import eu.timepit.refined.auto._
import io.circe.Json
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import org.scalatest.Inspectors
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3._
import com.backwards.fp.free.FreeOps.syntax._
import com.backwards.http.Http.Get._
import com.backwards.http.Http._
import com.backwards.http._
import com.backwards.io.Deserialiser
import com.backwards.json.JsonOps.syntax._

class CoproductSpec extends AnyWordSpec with Matchers with Inspectors {
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

      // TODO - There is: RequestBody.fromInputStream() but needs content length.
      //  There are possible workarounds, where a solution would allow S3 to fit in better with Http pagination.
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
        program.foldMap(StubHttpInterpreter or S3StubInterpreter)

      new String(response.readAllBytes).pipe(data =>
        forAll(List(StubHttpInterpreter.dataEntry1, StubHttpInterpreter.dataEntry2))(dataEntry =>
          data must include (dataEntry.noSpaces)
        )
      )
    }
  }
}