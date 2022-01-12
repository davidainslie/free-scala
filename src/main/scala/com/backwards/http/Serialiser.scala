package com.backwards.http

import cats.implicits.catsSyntaxOptionId
import io.circe.Json

trait Serialiser[A] extends com.backwards.serialisation.Serialiser[A] {
  val contentType: ContentType
}

object Serialiser {
  def apply[A: Serialiser]: Serialiser[A] =
    implicitly

  implicit val serialiserNothing: Serialiser[Nothing] = new Serialiser[Nothing] {
    val contentType: ContentType =
      ContentType.empty // Some folks believe this should be "application/octet-stream"

    def serialise(data: Nothing): Array[Byte] =
      Array.emptyByteArray
  }

  implicit val serialiserJson: Serialiser[Json] = new Serialiser[Json] {
    val contentType: ContentType =
      ContentType("application/json")

    def serialise(data: Json): Array[Byte] =
      data.noSpaces.getBytes
  }
}