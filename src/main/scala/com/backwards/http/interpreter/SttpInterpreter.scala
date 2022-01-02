package com.backwards.http.interpreter

import cats.implicits._
import cats.{MonadError, ~>}
import eu.timepit.refined.auto._
import sttp.client3._
import sttp.client3.circe._
import sttp.model.Uri
import com.backwards.http.Http._
import com.backwards.http.{Auth, Http}

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
            ???

          case Post(uri) =>
            println("Post called")
            ???

          case Put() =>
            println("PUT called")
            ???

          case Get.WithDeserialiser(uri, headers, params, deserialiser) =>
            basicRequest
              .get(Uri(uri).addParams(params.value))
              .headers(headers.value)
              .response(asByteArrayAlways.map(deserialiser.deserialise))
              .send(backend)
              .flatMap(_.body.fold(MonadError[F, Throwable].raiseError, a => MonadError[F, Throwable].pure(a)))
        }
    }
}