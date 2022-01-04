package com.backwards.http.interpreter

import cats.implicits._
import cats.{MonadError, ~>}
import eu.timepit.refined.auto._
import sttp.client3._
import sttp.model.Uri
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.fp.FunctionOps.syntax._
import com.backwards.http.Http._
import com.backwards.http._

// TODO - Factor out repetition
object SttpInterpreter {
  def apply[F[_]: MonadError[*[_], Throwable]](backend: SttpBackend[F, Any]): Http ~> F =
    new (Http ~> F) {
      override def apply[A](fa: Http[A]): F[A] =
        fa match {
          case Post(uri, headers, params, auth, body, serialiser, deserialiser) =>
            basicRequest
              .post(Uri(uri).addParams(params.value))
              .optional(serialiser.contentType)(_.contentType)
              .headers(headers.value)
              .optional(auth)(applyAuth)
              .optional(body)(request => body => request.body(serialiser.serialise(body)))
              .response(asByteArray.mapRight(deserialiser.deserialise))
              .send(backend)
              .flatMap(response =>
                response.body.fold(
                  errorMsg => MonadError[F, Throwable].raiseError(HttpError(errorMsg, response.code)),
                  _.fold(MonadError[F, Throwable].raiseError, a => MonadError[F, Throwable].pure(a))
                )
              )

          case Put(uri, headers, params, auth, body, serialiser, deserialiser) =>
            basicRequest
              .put(Uri(uri).addParams(params.value))
              .optional(serialiser.contentType)(_.contentType)
              .headers(headers.value)
              .optional(auth)(applyAuth)
              .optional(body)(request => body => request.body(serialiser.serialise(body)))
              .response(asByteArray.mapRight(deserialiser.deserialise))
              .send(backend)
              .flatMap(response =>
                response.body.fold(
                  errorMsg => MonadError[F, Throwable].raiseError(HttpError(errorMsg, response.code)),
                  _.fold(MonadError[F, Throwable].raiseError, a => MonadError[F, Throwable].pure(a))
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
                  errorMsg => MonadError[F, Throwable].raiseError(HttpError(errorMsg, response.code)),
                  _.fold(MonadError[F, Throwable].raiseError, a => MonadError[F, Throwable].pure(a))
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