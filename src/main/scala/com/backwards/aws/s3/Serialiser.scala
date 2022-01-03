package com.backwards.aws.s3

import io.circe.Json

trait Serialiser[A] {
  def serialise(data: A): Array[Byte]
}

object Serialiser {
  def apply[A: Serialiser]: Serialiser[A] =
    implicitly

  implicit val serialiserNothing: Serialiser[Nothing] =
    (_: Nothing) => Array.emptyByteArray

  implicit val serialiserVectorJson: Serialiser[Vector[Json]] =
    _.map(_.noSpaces).mkString("\n").getBytes
}