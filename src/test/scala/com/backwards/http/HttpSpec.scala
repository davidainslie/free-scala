package com.backwards.http

import java.net.URI
import cats.free.Free
import cats.implicits._
import cats.{Id, InjectK, ~>}
import eu.timepit.refined.auto._
import io.circe.Json
import io.circe.literal.JsonStringContext
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.fp.implicits._
import com.backwards.http.Http.Get._
import com.backwards.http.Http._
import com.backwards.net.URIOps.syntax._

class HttpSpec extends AnyWordSpec with Matchers {
  "Http Algebra" should {
    "be applied against a stubbed interpreter for the simplest implementation" in {
      def program(implicit I: InjectK[Http, Http]): Free[Http, (Auth, Json)] =
        for {
          auth <- GrantByPassword(URI.create("https://backwards.com/api/oauth2/access_token"), Credentials(User("user"), Password("password")))
          data <- Get[Json](URI.create("https://backwards.com/api/execute"))
        } yield (auth, data)

      val (auth: Auth, data: Json) =
        program.foldMap(StubHttpInterpreter)

      auth mustEqual StubHttpInterpreter.basicToken
      data mustEqual StubHttpInterpreter.data
    }
  }

  "Http Get" should {
    "add query params" in {
      val get: Get[String] =
        Get[String](URI.create("http://backwards.com/api"))

      val updatedGet: Get[String] =
        (uriL[String].modify(_.addParam("key1" -> "value1")) andThen uriL[String].modify(_.addParam("key2" -> "value2")))(get)

      updatedGet.uri.toString mustEqual "http://backwards.com/api?key1=value1&key2=value2"
    }
  }
}

object StubHttpInterpreter extends (Http ~> Id) {
  val basicToken: BasicToken =
    BasicToken("token")

  val dataEntry1 =
    json"""{ "id": "1" }"""

  val dataEntry2 =
    json"""{ "id": "2" }"""

  val data: Json =
    Json.obj("data" -> Json.arr(dataEntry1, dataEntry2))

  override def apply[A](fa: Http[A]): Id[A] =
    fa match {
      case GrantByPassword(uri, credentials) =>
        basicToken.asInstanceOf[A]

      case Get(uri, headers, params, auth, deserialiser) =>
        data.asInstanceOf[A]

      case notImplemented =>
        throw new NotImplementedError(s"Test failure because of unexpected: $notImplemented")
    }
}