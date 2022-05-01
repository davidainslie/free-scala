package tech.backwards.algebra.interpreter

import scala.util.chaining.scalaUtilChainingOps
import cats.InjectK
import cats.data.EitherK
import cats.effect.{IO, IOApp, Resource}
import cats.free.Free
import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.Bucket
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3._
import tech.backwards.aws.s3.interpreter.S3IOInterpreter
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
object HttpS3StreamApp extends IOApp.Simple {
  type Algebras[A] = EitherK[Http, S3, A]

  implicit class GetOps(get: Get[Json])(implicit D: Deserialiser[Json], IH: InjectK[Http, Algebras], S: Serialiser[Jsonl], IS: InjectK[S3, Algebras]) {
    val maxPages: Int = 5

    def paginate(bucket: Bucket, key: String): Free[Algebras, Unit] = {
      def go(get: Get[Json], page: Int): Free[Algebras, Unit] = {
        for {
          json  <- paramsL[Json].modify(_ + ("page" -> page))(get)
          data  <- Jsonl((json \ "data").flatMap(_.asArray)).liftFree[Algebras]
          _     <- when(data.value.nonEmpty, PutStream(bucket, key, data), unit[Algebras])
          //_   <- (if (true) throw new Exception("whoops") else ()).liftFree[Algebras] // TODO - Remove test and put in actual test
          pages <- (json \ "meta" \ "pagination" \ "pages").flatMap(_.as[Int].toOption).getOrElse(0).liftFree[Algebras]
          _     <- if (page < pages && page < maxPages) go(get, page + 1) else unit[Algebras]
        } yield ()
      }

      go(get, page = 1).as(CompletePutStream(bucket, key))
    }
  }

  def program(bucket: Bucket)(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
    for {
      _         <- CreateBucket(createBucketRequest(bucket))
      path      = "foo.txt"
      _         <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate(bucket, path)
      response  <- GetObject[Jsonl](getObjectRequest(bucket, path))
    } yield response

  def run: IO[Unit] = (
    for {
      awsCredentials  <- Resource.eval(awsCredentials[IO])
      s3Client        <- S3Client.resource[IO](awsCredentials, Region.of(sys.env("AWS_REGION")))
      s3Interpreter   <- S3IOInterpreter.resource(s3Client)
      backend         <- AsyncHttpClientCatsBackend.resource[IO]()
    } yield (backend, s3Interpreter)
  ).use { case (backend, s3Interpreter) => program(bucket(sys.env("AWS_BUCKET"))).foldMap(SttpInterpreter(backend.logging) or s3Interpreter) }.attempt.flatMap {
    case Left(t)  => scribe.error(t).pipe(_ => IO.raiseError(t))
    case Right(r) => scribe.info(r.show).pure[IO]
  }
}