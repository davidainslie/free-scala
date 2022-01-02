package com.backwards.algebra.interpreter

import java.net.URI
import cats.InjectK
import cats.data.EitherK
import cats.effect.{IO, IOApp}
import cats.free.Free
import cats.implicits._
import io.circe.Json
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import com.backwards.aws.s3.S3.{CreateBucket, PutStream}
import com.backwards.aws.s3.{Bucket, CreateBucketRequest, PutStreamHandle, S3}
import com.backwards.aws.s3.interpreter.S3IOInterpreter
import com.backwards.docker.aws.WithAwsContainer
import com.backwards.fp.free.FreeOps.syntax._
import com.backwards.http.Http.Get._
import com.backwards.http.Http._
import com.backwards.http.SttpBackendOps.syntax._
import com.backwards.http._
import com.backwards.http.interpreter.SttpInterpreter
import com.backwards.io.{Deserialiser, Serialiser}
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

  implicit class GetOps(get: Get[Json])(implicit D: Deserialiser[Json], IH: InjectK[Http, Algebras], S: Serialiser[Vector[Json]], IS: InjectK[S3, Algebras]) {
    // TODO - Make tail recursive
    def paginate(putStreamHandle: PutStreamHandle): Free[Algebras, Unit] = {
      def go(get: Get[Json], page: Int): Free[Algebras, Unit] =
        paramsL[Json].modify(_ + ("page" -> page))(get).flatMap { json =>
          val data: Vector[Json] =
            (json \ "data").flatMap(_.asArray).toVector.flatten

          if (data.nonEmpty) putStreamHandle.write(data)

          val pages: Int =
            (json \ "meta" \ "pagination" \ "pages").flatMap(_.as[Int].toOption).getOrElse(0)

          if (page < pages) go(get, page + 1)
          else ().liftFree[Algebras]
        }

      go(get, page = 1)
    }
  }

  def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Unit] =
    for {
      bucket <- Bucket("my-bucket").liftFree[Algebras]
      _      <- CreateBucket(CreateBucketRequest(bucket))
      handle <- PutStream(bucket, "foo") // TODO - Program must not forget to call putStreamHandle.complete() - Better to have some sort of Resource like Cats
      _      <- Get[Json](URI.create("https://gorest.co.in/public/v1/users")).paginate(handle).as(handle.complete())
      // response  <- GetObject(GetObjectRequest(bucket, "foo"))
    } yield ()

  // TODO A RetryingBackend (and maybe Rate Limit): https://sttp.softwaremill.com/en/latest/backends/wrappers/custom.html
  def run: IO[Unit] =
    AsyncHttpClientCatsBackend[IO]().flatMap(backend =>
      program.foldMap(SttpInterpreter(backend.logging) or S3IOInterpreter(s3)) >> backend.close()
    )
}