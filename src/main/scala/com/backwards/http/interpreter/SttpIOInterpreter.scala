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

// TODO - Factor out repetition
object SttpIOInterpreter {
  def apply(backend: SttpBackend[IO, Any]): Http ~> IO =
    new (Http ~> IO) {
      override def apply[A](fa: Http[A]): IO[A] =
        fa match {
          case Post(uri, headers, params, auth, body, serialiser, deserialiser) =>
            basicRequest
              .post(Uri(uri).addParams(params.value))
              .optional(serialiser.contentType.value)(_.contentType)
              .headers(headers.value)
              .optional(auth)(applyAuth)
              .optional(body)(request => body => request.body(serialiser.serialise(body)))
              .response(asByteArray.mapRight(deserialiser.deserialise))
              .send(backend)
              .flatMap(response =>
                response.body.fold(
                  errorMsg => IO.raiseError(HttpError(errorMsg, response.code)),
                  _.fold(IO.raiseError, IO.pure)
                )
              )

          case Put(uri, headers, params, auth, body, serialiser, deserialiser) =>
            basicRequest
              .put(Uri(uri).addParams(params.value))
              .optional(serialiser.contentType.value)(_.contentType)
              .headers(headers.value)
              .optional(auth)(applyAuth)
              .optional(body)(request => body => request.body(serialiser.serialise(body)))
              .response(asByteArray.mapRight(deserialiser.deserialise))
              .send(backend)
              .flatMap(response =>
                response.body.fold(
                  errorMsg => IO.raiseError(HttpError(errorMsg, response.code)),
                  _.fold(IO.raiseError, IO.pure)
                )
              )

          case Get(uri, headers, params, auth, deserialiser) =>
            basicRequest
              .get(Uri(uri).addParams(params.value))
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

  def applyAuth(request: RequestT[Identity, Either[String, String], Any]): Auth => RequestT[Identity, Either[String, String], Any] = {
    case Basic(Credentials(User(user), Password(password))) => request.auth.basic(user, password)
    case BasicToken(token) => request.auth.basicToken(token)
    case Digest(Credentials(User(user), Password(password))) => request.auth.digest(user, password)
    case Bearer(token, _) => request.auth.bearer(token)
  }
}