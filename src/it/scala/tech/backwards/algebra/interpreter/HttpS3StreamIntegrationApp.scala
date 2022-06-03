package tech.backwards.algebra.interpreter

import cats.InjectK
import cats.data.EitherK
import cats.effect.{IO, IOApp, Resource}
import cats.free.Free
import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import software.amazon.awssdk.services.s3.model.Bucket
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3._
import tech.backwards.aws.s3.interpreter.S3IOInterpreter
import tech.backwards.docker.aws.WithAwsContainer
import tech.backwards.fp.free.FreeOps.syntax._
import tech.backwards.http.Http
import tech.backwards.http.Http.Get._
import tech.backwards.http.Http._
import tech.backwards.http.SttpBackendOps.syntax.SttpBackendExtension
import tech.backwards.http.interpreter.SttpInterpreter
import tech.backwards.json.JsonOps.syntax._
import tech.backwards.json.Jsonl
import tech.backwards.serialisation.{Deserialiser, Serialiser}

/**
 * Interact with a real (though dummy) Http API and a LocalStack via Stream.
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
object HttpS3StreamIntegrationApp extends IOApp.Simple with WithAwsContainer {
  type Algebras[A] = EitherK[Http, S3, A]

  implicit class GetOps(get: Get[Json])(implicit D: Deserialiser[Json], IH: InjectK[Http, Algebras], S: Serialiser[Jsonl], IS: InjectK[S3, Algebras]) {
    val maxPages: Int = 5

    def paginate(bucket: Bucket, key: String): Free[Algebras, Unit] = {
      def go(get: Get[Json], page: Int): Free[Algebras, Unit] = {
        for {
          json  <- paramsL[Json].modify(_ + ("page" -> page))(get)
          data  <- Jsonl((json \ "data").flatMap(_.asArray)).toFree[Algebras]
          _     <- when(data.value.nonEmpty, PutStream(bucket, key, data), unit[Algebras])
          //_   <- (if ("1" == "1") throw new Exception("whoops") else ()).liftFree[Algebras] // TODO - Remove test and put in actual test
          pages <- (json \ "meta" \ "pagination" \ "pages").flatMap(_.as[Int].toOption).getOrElse(0).toFree[Algebras]
          _     <- if (page < pages && page < maxPages) go(get, page + 1) else unit[Algebras]
        } yield ()
      }

      go(get, page = 1).as(CompletePutStream(bucket, key))
    }
  }

  def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
    for {
      bucket    <- bucket("my-bucket").toFree[Algebras]
      _         <- CreateBucket(createBucketRequest(bucket))
      _         <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate(bucket, "foo")
      response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
    } yield response

  // TODO Could try RetryingBackend (and maybe Rate Limit): https://sttp.softwaremill.com/en/latest/backends/wrappers/custom.html
  def run: IO[Unit] =
    Resource.both(AsyncHttpClientCatsBackend.resource[IO](), S3IOInterpreter.resource(s3Client)).use { case (backend, s3Interpreter) =>
      program.foldMap(SttpInterpreter(backend.logging) or s3Interpreter)
    } map (response => scribe.info(response.show))
}