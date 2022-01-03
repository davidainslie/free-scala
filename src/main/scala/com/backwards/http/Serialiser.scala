package com.backwards.http

import cats.implicits.catsSyntaxOptionId
import io.circe.Json

trait Serialiser[A] {
  val contentType: Option[String]

  def serialise(data: A): Array[Byte]
}

object Serialiser {
  def apply[A: Serialiser]: Serialiser[A] =
    implicitly

  implicit val serialiserNothing: Serialiser[Nothing] = new Serialiser[Nothing] {
    val contentType: Option[String] =
      None

    def serialise(data: Nothing): Array[Byte] =
      Array.emptyByteArray
  }

  implicit val serialiserJson: Serialiser[Json] = new Serialiser[Json] {
    val contentType: Option[String] =
      "application/json".some

    def serialise(data: Json): Array[Byte] =
      data.noSpaces.getBytes
  }
}