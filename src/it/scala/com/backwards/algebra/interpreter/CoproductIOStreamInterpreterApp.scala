package com.backwards.algebra.interpreter

import scala.util.Try
import cats.InjectK
import cats.data.{EitherK, EitherT}
import cats.effect.{IO, IOApp}
import cats.free.Free
import cats.implicits._
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.services.s3.model.{Bucket, GetObjectResponse}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.backwards.aws.s3
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3._
import com.backwards.aws.s3.interpreter.S3IOInterpreter
import com.backwards.docker.aws.WithAwsContainer
import com.backwards.fp.free.FreeOps.syntax._
import com.backwards.http
import com.backwards.http.Http
import com.backwards.http.Http.Get._
import com.backwards.http.Http._
import com.backwards.http.SttpBackendOps.syntax._
import com.backwards.http.interpreter.SttpInterpreter
import com.backwards.json.JsonOps.syntax._

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
object CoproductIOStreamInterpreterApp extends IOApp.Simple with WithAwsContainer {
  type Algebras[A] = EitherK[Http, S3, A]

  implicit class GetOps(get: Get[Json])(implicit D: http.Deserialiser[Json], IH: InjectK[Http, Algebras], S: s3.Serialiser[Vector[Json]], IS: InjectK[S3, Algebras]) {
    // def paginate(putStreamHandle: PutStreamHandle): Free[Algebras, Unit] = {
    def paginate(bucket: Bucket, key: String): Free[Algebras, Unit] = {
      def go(get: Get[Json], page: Int): Free[Algebras, Unit] = {
        for {
          json  <- paramsL[Json].modify(_ + ("page" -> page))(get)
          data  <- (json \ "data").flatMap(_.asArray).toVector.flatten.liftFree[Algebras]
          // _ <- PutStream(bucket, key, data).liftFree[Algebras].when(data.nonEmpty, ().liftFree[Algebras])
          _ <- if (data.nonEmpty) S3.s3ToFree(PutStream(bucket, key, data)) else ().liftFree[Algebras]
          _ <-  (if ("1" == "1") throw new Exception("whoops") else ()).liftFree[Algebras]
          pages <- (json \ "meta" \ "pagination" \ "pages").flatMap(_.as[Int].toOption).getOrElse(0).liftFree[Algebras]
          _     <- if (page < pages) go(get, page + 1) else ().liftFree[Algebras]
        } yield ()
      }

      go(get, page = 1).flatMap(_ => CompletePutStream(bucket, key))
    }
  }

  def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
    for {
      bucket    <- com.backwards.aws.s3.Bucket("my-bucket").liftFree[Algebras] // TODO - Sort out S3 proxies
      _         <- CreateBucket(CreateBucketRequest(bucket))
      //_         <- PutStream(bucket, "foo").use(Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate)
      _         <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate(bucket, "foo")
      response  <- GetObject(GetObjectRequest(bucket, "foo"))
    } yield response

  // TODO A RetryingBackend (and maybe Rate Limit): https://sttp.softwaremill.com/en/latest/backends/wrappers/custom.html
  def run: IO[Unit] =
    AsyncHttpClientCatsBackend[IO]().flatMap(backend =>
      EitherT(program.foldMap(SttpInterpreter(backend.logging) or S3IOInterpreter(s3Client)).attempt)
        .subflatMap(response => Try(new String(response.readAllBytes)).toEither)
        //.fold(scribe.error(_), scribe.info(_)) >> backend.close()
        .fold(scribe.error(_), x => scribe.info(x.substring(0, 10))) >> backend.close()
    )
}