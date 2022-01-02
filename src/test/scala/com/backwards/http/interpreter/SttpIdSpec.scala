package com.backwards.http.interpreter

import java.net.URI
import scala.concurrent.duration._
import cats.free.Free
import cats.implicits._
import cats.{Id, InjectK, ~>}
import eu.timepit.refined.auto._
import sttp.client3.{HttpError, SttpBackend}
import sttp.client3.monad.IdMonad
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method._
import sttp.model.StatusCode
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.fp.implicits.monadErrorId
import com.backwards.http.Http._
import com.backwards.http.SttpBackendStubOps.syntax._
import com.backwards.http._

class SttpIdSpec extends AnyWordSpec with Matchers {
  "Http Algebra" should {
    "be applied against an async interpreter" in {
      object SttpInterpreter {
        val bearer: Bearer =
          Bearer("token", 1 hour)

        val data: String =
          "Blah blah"

        val backend: SttpBackend[Id, Any] =
          SttpBackendStub[Id, Any](IdMonad)
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenJsonRespond(bearer)
            .whenRequestMatchesAll(_.method == GET, _.uri.path.startsWith(List("api", "execute")))
            .thenRespond(data)
            .logging

        def apply(): Http ~> Id =
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit I: InjectK[Http, Http]): Free[Http, (Auth, String)] =
        for {
          auth <- GrantByPassword(URI.create("https://backwards.com/api/oauth2/access_token"), Credentials(User("user"), Password("password")))
          data <- Get[String](URI.create("https://backwards.com/api/execute"))
        } yield (auth, data)

      val (auth: Auth, data: String) =
        program.foldMap(SttpInterpreter())

      auth mustEqual SttpInterpreter.bearer
      data mustEqual SttpInterpreter.data
    }

    "be applied against an async interpreter where exceptions are simply thrown" in {
      object SttpInterpreter {
        val backend: SttpBackend[Id, Any] =
          SttpBackendStub[Id, Any](IdMonad)
            .whenRequestMatchesAll(_.method == POST, _.uri.path.startsWith(List("api", "oauth2", "access_token")))
            .thenRespondServerError()

        def apply(): Http ~> Id =
          com.backwards.http.interpreter.SttpInterpreter(backend)
      }

      def program(implicit I: InjectK[Http, Http]): Free[Http, (Auth, String)] =
        for {
          auth <- GrantByPassword(URI.create("https://backwards.com/api/oauth2/access_token"), Credentials(User("user"), Password("password")))
          data <- Get[String](URI.create("https://backwards.com/api/execute"))
        } yield (auth, data)

      val httpError: HttpError[String] =
        the [HttpError[String]] thrownBy program.foldMap(SttpInterpreter())

      httpError.statusCode mustEqual StatusCode.InternalServerError
    }
  }
}