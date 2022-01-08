package com.backwards.algebra.interpreter

import scala.util.Try
import cats.InjectK
import cats.data.EitherK
import cats.effect.{IO, IOApp, Resource}
import cats.free.Free
import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3.interpreter.S3IOInterpreter
import com.backwards.aws.s3.{Deserialiser, _}
import com.backwards.docker.aws.WithAwsContainer
import com.backwards.fp.free.FreeOps.syntax._
import com.backwards.http.Http.Get._
import com.backwards.http.Http._
import com.backwards.http.SttpBackendOps.syntax._
import com.backwards.http._
import com.backwards.http.interpreter.SttpInterpreter
import com.backwards.json.JsonOps.syntax._

/**
 * Interact with a real (though dummy) Http API and a LocalStack.
 *
 * Http API: https://gorest.co.in
 *
 * E.g. Get users, which by default gives the first page:
 *  - curl -i -H "Accept:application/json" -H "Content-Type:application/json" -XGET "https://gorest.co.in/public/v1/users"
 *
 * Or even better, if you have httpie (brew install httpie):
 *  - http https://gorest.co.in/public/v1/users
 *
 * Result example:
 * {{{
 *   {
 *    "data": [{
 *      "email": "sharma_amaresh@jacobi.org",
 *      "gender": "female",
 *      "id": 3,
 *      "name": "Amaresh Sharma",
 *      "status": "active"
 *    }, {
 *      "email": "lavanya_singh@fadel.name",
 *      "gender": "female",
 *      "id": 4,
 *      "name": "Lavanya Singh",
 *      "status": "inactive"
 *    }],
 *    "meta": {
 *      "pagination": {
 *        "limit": 20,
 *        "links": {
 *          "current": "https://gorest.co.in/public/v1/users?page=1",
 *          "next": "https://gorest.co.in/public/v1/users?page=2",
 *          "previous": null
 *        },
 *        "page": 1,
 *        "pages": 419,
 *        "total": 8375
 *      }
 *    }
 *   }
 * }}}
 */
object CoproductIOInterpreterApp extends IOApp.Simple with WithAwsContainer {
  type Algebras[A] = EitherK[Http, S3, A]

  implicit class GetOps[F[_]: InjectK[Http, *[_]]](get: Get[Json])(implicit D: Deserialiser[Json]) {
    def paginate: Free[F, Vector[Json]] = {
      def accumulate(acc: Vector[Json], json: Json): Vector[Json] =
        (json \ "data").flatMap(_.asArray).fold(acc)(acc ++ _)

      def go(get: Get[Json], acc: Vector[Json], page: Int): Free[F, Vector[Json]] =
        for {
          content <- paramsL[Json].modify(_ + ("page" -> page))(get)
          pages   = (content \ "meta" \ "pagination" \ "pages").flatMap(_.as[Int].toOption).getOrElse(0)
          data    <- if (page < pages) go(get, accumulate(acc, content), page + 1) else Free.pure[F, Vector[Json]](accumulate(acc, content))
        } yield data

      go(get, acc = Vector.empty, page = 1)
    }
  }

  def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
    for {
      bucket    <- bucket("my-bucket").liftFree[Algebras]
      _         <- CreateBucket(createBucketRequest(bucket))
      data      <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate
      _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
      response  <- GetObject(getObjectRequest(bucket, "foo"))
    } yield response

  // TODO Could try RetryingBackend (and maybe Rate Limit): https://sttp.softwaremill.com/en/latest/backends/wrappers/custom.html
  def run: IO[Unit] =
    Resource.both(AsyncHttpClientCatsBackend.resource[IO](), S3IOInterpreter.resource(s3Client)).use { case (backend, s3Interpreter) =>
      program.foldMap(SttpInterpreter(backend.logging) or s3Interpreter)
    } >>= (response => IO.fromEither(Try(new String(response.readAllBytes)).toEither)) >>= (result => IO(scribe.info(result)))
}