package com.backwards.json

import io.circe.syntax._
import io.circe.{Json, JsonObject}
import monocle.{Optional, Traversal}

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
}