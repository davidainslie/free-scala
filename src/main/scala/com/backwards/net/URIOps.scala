package com.backwards.net

import java.net.URI
import scala.util.Try
import io.circe.{Decoder, Encoder}

object URIOps {
  object syntax {
    implicit class URIExtension(self: URI) {
      def addParam(keyValue: (String, Any)): URI = {
        val (key, value) = keyValue

        val uri: String =
          self.toString

        if (uri.contains("?")) {
          URI.create(s"$uri&$key=$value")
        } else {
          URI.create(s"$uri?$key=$value")
        }
      }
    }
  }

  /**
   * A trait to avoid Intellij think that an import is unused.
   */
  trait Codec {
    implicit val decoderUri: Decoder[URI] =
      Decoder.decodeString.emapTry(s => Try(URI.create(s)))

    implicit val encoderUri: Encoder[URI] =
      Encoder.encodeString.contramap[URI](_.toString)
  }
}