package tech.backwards.http

import io.circe.syntax._
import io.circe.{Encoder, Json}
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{Request, SttpBackend}
import sttp.model.StatusCode

object SttpBackendStubOps {
  object syntax {
    implicit class SttpBackendStubExtension[F[_]](protected val stub: SttpBackendStub[F, Any]) {
      def logging: SttpBackend[F, Any] =
        new SttpBackendOps.syntax.SttpBackendExtension(stub).logging

      def whenRequestMatchesAll(ps: (Request[Any, Any] => Boolean)*): stub.WhenRequest =
        stub.whenRequestMatches(request => ps.forall(_(request.asInstanceOf[Request[Any, Any]])))
    }

    implicit class WhenRequestExtension[F[_]](stub: SttpBackendStub[F, Any]#WhenRequest) {
      def thenJsonRespond[A: Encoder](a: A): SttpBackendStub[F, Any] =
        thenJsonRespond(a.asJson)

      def thenJsonRespond(json: Json): SttpBackendStub[F, Any] =
        stub.thenRespond(json.noSpaces)

      def thenJsonRespond[A: Encoder](a: A, statusCode: StatusCode): SttpBackendStub[F, Any] =
        thenJsonRespond(a.asJson, statusCode)

      def thenJsonRespond(json: Json, statusCode: StatusCode): SttpBackendStub[F, Any] =
        stub.thenRespond(json.noSpaces, statusCode)
    }
  }
}