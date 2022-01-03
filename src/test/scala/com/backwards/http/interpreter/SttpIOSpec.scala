package com.backwards.http.interpreter

import java.net.URI
import scala.concurrent.duration._
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.free.Free
import cats.implicits._
import cats.{InjectK, ~>}
import eu.timepit.refined.auto._
import io.circe.Json
import sttp.client3.{ByteArrayBody, HttpError, SttpBackend}
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.model.Method._
import sttp.model.StatusCode
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.http.Http._
import com.backwards.http.SttpBackendStubOps.syntax._
import com.backwards.http._

class SttpIOSpec extends AnyWordSpec with Matchers {
  "Http Algebra" should {
    "be applied against an async interpreter" in {
      object SttpInterpreter {
        val bearer: Bearer =
          Bearer("token", 1 hour)

        val data: String =
          "Blah blah"

        val backend: SttpBackend[IO, Any] =
          AsyncHttpClientCatsBackend.stub[IO]
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenJsonRespond(bearer)
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "post")))
            .thenRespondOk()
            .whenRequestMatchesAll(_.method == PUT, _.uri.path.startsWith(List("api", "put")))
            .thenRespondOk()
            .whenRequestMatchesAll(_.method == GET, _.uri.path.startsWith(List("api", "execute")))
            .thenRespond(data)
            .logging

        def apply(): Http ~> IO =
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit I: InjectK[Http, Http]): Free[Http, (Auth, String)] =
        for {
          auth <- GrantByPassword(URI.create("https://backwards.com/api/oauth2/access_token"), Credentials(User("user"), Password("password")))
          _    <- Post[Data, Unit](URI.create("https://backwards.com/api/post"), body = Data("blah", 2).some)
          _    <- Put[Nothing, Unit](URI.create("https://backwards.com/api/put"))
          data <- Get[String](URI.create("https://backwards.com/api/execute"))
        } yield (auth, data)

      val response: IO[(Auth, String)] =
        program.foldMap(SttpInterpreter())

      val Right((auth: Auth, data: String)) =
        response.attempt.unsafeRunSync()

      auth mustEqual SttpInterpreter.bearer
      data mustEqual SttpInterpreter.data
    }

    "be applied against an async interpreter where exceptions are captured via MonadError" in {
      object SttpInterpreter {
        val backend: SttpBackend[IO, Any] =
          AsyncHttpClientCatsBackend.stub[IO]
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenRespondServerError()

        def apply(): Http ~> IO =
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit I: InjectK[Http, Http]): Free[Http, (Auth, String)] =
        for {
          auth <- GrantByPassword(URI.create("https://backwards.com/api/oauth2/access_token"), Credentials(User("user"), Password("password")))
          data <- Get[String](URI.create("https://backwards.com/api/execute"))
        } yield (auth, data)

      val response: IO[(Auth, String)] =
        program.foldMap(SttpInterpreter())

      val Left(error: HttpError[_]) =
        response.attempt.unsafeRunSync()

      error.statusCode mustEqual StatusCode.InternalServerError
    }
  }
}

final case class Data(one: String, two: Int)

object Data {
  implicit val serialiserData: Serialiser[Data] = new Serialiser[Data] {
    val contentType: Option[String] =
      "application/json".some

    def serialise(data: Data): Array[Byte] =
      Json.obj(
        "data" -> Json.obj(
          "one" -> Json.fromString(data.one),
          "two" -> Json.fromInt(data.two)
        )
      ).spaces2.getBytes
  }
}