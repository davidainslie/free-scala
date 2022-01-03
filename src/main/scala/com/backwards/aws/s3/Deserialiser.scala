package com.backwards.aws.s3

import cats.implicits._
import io.circe.Json
import io.circe.parser._

trait Deserialiser[A] {
  def deserialise(bytes: Array[Byte]): DeserialiserError Either A
}

object Deserialiser {
  implicit val deserialiserUnit: Deserialiser[Unit] =
    (_: Array[Byte]) => ().asRight

  implicit val deserialiserBytes: Deserialiser[Array[Byte]] =
    (bytes: Array[Byte]) => bytes.asRight

  implicit val deserialiserString: Deserialiser[String] =
    (bytes: Array[Byte]) => new String(bytes).asRight

  implicit val deserialiserJson: Deserialiser[Json] =
    (bytes: Array[Byte]) => parse(new String(bytes)).leftMap(e => DeserialiserError(e.message, e.underlying.some))
}

final case class DeserialiserError(message: String, cause: Option[Throwable] = None)
  extends Exception(message, cause.fold(null.asInstanceOf[Throwable])(identity))