package com.backwards.http.interpreter

import cats.implicits._
import cats.{MonadError, ~>}
import eu.timepit.refined.auto._
import sttp.client3._
import sttp.client3.circe._
import sttp.model.Uri
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.http.Http._
import com.backwards.http.{Auth, Basic, BasicToken, Bearer, Digest, Http}
import com.backwards.fp.FunctionOps.syntax._

// TODO - Horrible repetition
object SttpInterpreter {
  def apply[F[_]: MonadError[*[_], Throwable]](backend: SttpBackend[F, Any]): Http ~> F =
    new (Http ~> F) {
      override def apply[A](fa: Http[A]): F[A] =
        fa match {
          case GrantByPassword(uri, credentials) =>
            basicRequest
              .body(
                "grant_type" -> "password",
                "username" -> credentials.user.value,
                "password" -> credentials.password.value
              )
              .post(Uri(uri))
              .response(asJson[Auth])
              .send(backend)
              .flatMap(_.body.fold(MonadError[F, Throwable].raiseError, MonadError[F, Throwable].pure(_)))

          case GrantByClientCredentials(uri, credentials) =>
            basicRequest
              .body(
                "grant_type" -> "client_credentials",
                "client_id" -> credentials.user.value,
                "client_secret" -> credentials.password.value
              )
              .post(Uri(uri))
              .response(asJson[Auth])
              .send(backend)
              .flatMap(_.body.fold(MonadError[F, Throwable].raiseError, MonadError[F, Throwable].pure(_)))

          case Post(uri, headers, params, auth, deserialiser) =>
            basicRequest
              .post(Uri(uri).addParams(params.value))
              .headers(headers.value)
              .optional(auth)(applyAuth)
              .response(asByteArrayAlways.map(deserialiser.deserialise))
              .send(backend)
              .flatMap(_.body.fold(MonadError[F, Throwable].raiseError, a => MonadError[F, Throwable].pure(a)))

          case Put(uri, headers, params, auth, deserialiser) =>
            basicRequest
              .put(Uri(uri).addParams(params.value))
              .headers(headers.value)
              .optional(auth)(applyAuth)
              .response(asByteArrayAlways.map(deserialiser.deserialise))
              .send(backend)
              .flatMap(_.body.fold(MonadError[F, Throwable].raiseError, a => MonadError[F, Throwable].pure(a)))

          case Get(uri, headers, params, auth, deserialiser) =>
            basicRequest
              .get(Uri(uri).addParams(params.value))
              .headers(headers.value)
              .optional(auth)(applyAuth)
              .response(asByteArrayAlways.map(deserialiser.deserialise))
              .send(backend)
              .flatMap(_.body.fold(MonadError[F, Throwable].raiseError, a => MonadError[F, Throwable].pure(a)))
        }
    }

  def applyAuth(request: RequestT[Identity, Either[String, String], Any]): Auth => RequestT[Identity, Either[String, String], Any] = {
    case Basic(Credentials(User(user), Password(password))) => request.auth.basic(user, password)
    case BasicToken(token) => request.auth.basicToken(token)
    case Digest(Credentials(User(user), Password(password))) => request.auth.digest(user, password)
    case Bearer(token, _) => request.auth.bearer(token)
  }
}