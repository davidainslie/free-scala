package com.backwards.http.interpreter

import cats.effect.IO
import cats.implicits._
import cats.~>
import eu.timepit.refined.auto._
import sttp.client3._
import sttp.model.Uri
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.fp.FunctionOps.syntax._
import com.backwards.http.Http._
import com.backwards.http._
import com.backwards.serialisation.Deserialiser

object SttpIOInterpreter {
  def apply(backend: SttpBackend[IO, Any]): Http ~> IO =
    new (Http ~> IO) {
      override def apply[A](fa: Http[A]): IO[A] =
        fa match {
          case Post(uri, headers, params, auth, body, serialiser, deserialiser) =>
            send(
              headers, auth, deserialiser,
              basicRequest
                .post(Uri(uri).addParams(params.value))
                .optional(serialiser.contentType.value)(_.contentType)
                .optional(body)(request => body => request.body(serialiser.serialise(body)))
            )

          case Put(uri, headers, params, auth, body, serialiser, deserialiser) =>
            send(
              headers, auth, deserialiser,
              basicRequest
                .put(Uri(uri).addParams(params.value))
                .optional(serialiser.contentType.value)(_.contentType)
                .optional(body)(request => body => request.body(serialiser.serialise(body)))
            )

          case Get(uri, headers, params, auth, deserialiser) =>
            send(headers, auth, deserialiser, basicRequest.get(Uri(uri).addParams(params.value)))
        }

      def applyAuth(request: RequestT[Identity, String Either String, Any]): Auth => RequestT[Identity, String Either String, Any] = {
        case Basic(Credentials(User(user), Password(password))) => request.auth.basic(user, password)
        case BasicToken(token) => request.auth.basicToken(token)
        case Digest(Credentials(User(user), Password(password))) => request.auth.digest(user, password)
        case Bearer(token, _) => request.auth.bearer(token)
      }

      def send[A](headers: Headers, auth: Option[Auth], deserialiser: Deserialiser[A], request: Request[Either[String, String], Any]): IO[A] =
        request
          .headers(headers.value)
          .optional(auth)(applyAuth)
          .response(asByteArray.mapRight(deserialiser.deserialise))
          .send(backend)
          .flatMap(response =>
            response.body.fold(
              errorMsg => IO.raiseError(HttpError(errorMsg, response.code)),
              _.fold(IO.raiseError, IO.pure)
            )
          )
    }
}