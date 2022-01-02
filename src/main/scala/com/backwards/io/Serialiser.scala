package com.backwards.io

import io.circe.Json

trait Serialiser[A] {
  def serialise(data: A): Array[Byte]
}

object Serialiser {
  def apply[A: Serialiser]: Serialiser[A] =
    implicitly

  implicit val serialiserVectorJson: Serialiser[Vector[Json]] =
    _.map(_.noSpaces).mkString("\n").getBytes
}