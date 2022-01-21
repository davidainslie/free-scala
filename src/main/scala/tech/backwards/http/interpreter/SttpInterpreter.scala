package tech.backwards.http.interpreter

import cats.implicits._
import cats.{MonadError, ~>}
import eu.timepit.refined.auto._
import sttp.client3._
import sttp.model.Uri
import tech.backwards.auth.{Credentials, Password, User}
import tech.backwards.fp.FunctionOps.syntax._
import tech.backwards.http.Http._
import tech.backwards.http._
import tech.backwards.serialisation.Deserialiser

object SttpInterpreter {
  def apply[F[_]: MonadError[*[_], Throwable]](backend: SttpBackend[F, Any]): Http ~> F =
    new (Http ~> F) {
      override def apply[A](fa: Http[A]): F[A] =
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

      def send[A](headers: Headers, auth: Option[Auth], deserialiser: Deserialiser[A], request: Request[Either[String, String], Any]): F[A] =
        request
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