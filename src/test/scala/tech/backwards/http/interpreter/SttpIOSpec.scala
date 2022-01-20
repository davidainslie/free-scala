package tech.backwards.http.interpreter

import scala.concurrent.duration._
import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.free.Free
import cats.implicits._
import cats.{InjectK, ~>}
import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import io.circe.Json
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client3.{HttpError, SttpBackend}
import sttp.model.Method._
import sttp.model.StatusCode
import tech.backwards.http.Serialiser
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import tech.backwards.auth.{Credentials, Password, User}
import tech.backwards.http.CredentialsSerialiser.serialiserCredentialsByPassword
import tech.backwards.http.Http._
import tech.backwards.http.SttpBackendStubOps.syntax._
import tech.backwards.http._
import tech.backwards.util.EitherOps.syntax.EitherExtension

class SttpIOSpec extends AsyncWordSpec with AsyncIOSpec with Matchers {
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
          tech.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit I: InjectK[Http, Http]): Free[Http, (Auth, String)] =
        for {
          auth <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          _    <- Post[Data, Unit](uri("https://backwards.com/api/post"), body = Data("blah", 2).some)
          _    <- Put[Nothing, Unit](uri("https://backwards.com/api/put"))
          data <- Get[String](uri("https://backwards.com/api/execute"))
        } yield (auth, data)

      program.foldMap(SttpInterpreter()).attempt.map(_.rightValue).map { case (auth, data) =>
        auth mustEqual SttpInterpreter.bearer
        data mustEqual SttpInterpreter.data
      }
    }

    "be applied against an async interpreter where exceptions are captured via MonadError" in {
      object SttpInterpreter {
        val backend: SttpBackend[IO, Any] =
          AsyncHttpClientCatsBackend.stub[IO]
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenRespondServerError()

        def apply(): Http ~> IO =
          tech.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit I: InjectK[Http, Http]): Free[Http, (Auth, String)] =
        for {
          auth <- Post[Credentials, Auth](uri("https://backwards.com/api/oauth2/access_token"), body = Credentials(User("user"), Password("password")).some)
          data <- Get[String](uri("https://backwards.com/api/execute"))
        } yield (auth, data)

      program.foldMap(SttpInterpreter()).attempt.map(_.leftValue).map { case HttpError(_, statusCode) =>
        statusCode mustEqual StatusCode.InternalServerError
      }
    }
  }
}

final case class Data(one: String, two: Int)

object Data {
  implicit val serialiserData: Serialiser[Data] =
    new Serialiser[Data] {
      val contentType: ContentType =
        ContentType("application/json")

      def serialise(data: Data): Array[Byte] =
        Json.obj(
          "data" -> Json.obj(
            "one" -> Json.fromString(data.one),
            "two" -> Json.fromInt(data.two)
          )
        ).spaces2.getBytes
    }
}