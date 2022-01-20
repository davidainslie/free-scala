package tech.backwards.http

import sttp.client3.SttpBackend
import sttp.client3.logging.LogLevel
import sttp.client3.logging.scribe.ScribeLoggingBackend

object SttpBackendOps {
  object syntax {
    implicit class SttpBackendExtension[F[_]](protected val backend: SttpBackend[F, Any]) {
      def logging: SttpBackend[F, Any] =
        ScribeLoggingBackend(
          backend,
          beforeRequestSendLogLevel = LogLevel.Info,
          logRequestHeaders = true,
          logRequestBody = true,
          responseLogLevel = _ => LogLevel.Info,
          logResponseHeaders = true,
          logResponseBody = false
        )
    }
  }
}