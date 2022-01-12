package com.backwards.json

import io.circe.generic.extras.codec.UnwrappedCodec
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, JsonObject}
import monocle.{Optional, Traversal}
import shapeless.{::, Generic, HNil, Lazy}

object JsonOps {
  object syntax {
    implicit class JsonExtension(self: Json) {
      def \(key: String): Option[Json] =
        self.asObject.flatMap(_(key))
    }

    implicit class JsonOptionExtension(self: Option[Json]) {
      def \(key: String): Option[Json] =
        self.flatMap(_.asObject).flatMap(_(key))
    }

    implicit class JsonObjectExtension(self: JsonObject) {
      def \(key: String): Option[Json] =
        self(key)
    }

    implicit class TraversalExtension(self: Traversal[Json, Json]) {
      def getAll(json: JsonObject): List[Json] =
        self.getAll(json.asJson)
    }

    implicit class OptionalExtension(self: Optional[Json, Json]) {
      def getOption(json: JsonObject): Option[Json] =
        self.getOption(json.asJson)
    }
  }

  trait Codec {
    /**
     * Value Class decoding/encoding:
     *
     * Copied from io.circe.generic.extras.codec.UnwrappedCodec so can be used without Intellij thinking the following desired import is unused:
     * {{{
     *   import io.circe.generic.extras.codec.UnwrappedCoded._
     * }}}
     */
    implicit def codecForUnwrapped[A, R](
      implicit gen: Lazy[Generic.Aux[A, R :: HNil]],
      decodeR: Decoder[R],
      encodeR: Encoder[R]
    ): UnwrappedCodec[A] =
      UnwrappedCodec.codecForUnwrapped[A, R]
  }
}