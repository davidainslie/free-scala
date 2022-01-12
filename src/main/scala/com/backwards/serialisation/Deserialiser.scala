package com.backwards.serialisation

import scala.util.Using
import cats.Semigroup
import cats.implicits._
import io.circe.Json
import io.circe.parser._
import com.backwards.json.Jsonl

trait Deserialiser[A] {
  def deserialise(bytes: Array[Byte]): DeserialiserError Either A
}

object Deserialiser {
  implicit val deserialiserUnit: Deserialiser[Unit] =
    (_: Array[Byte]) => ().asRight

  implicit val deserialiserBytes: Deserialiser[Array[Byte]] =
    _.asRight

  implicit val deserialiserString: Deserialiser[String] =
    (bytes: Array[Byte]) => new String(bytes).asRight

  implicit val deserialiserJson: Deserialiser[Json] =
    (bytes: Array[Byte]) => parse(new String(bytes)).leftMap(e => DeserialiserError(e.message, e.underlying.some))

  implicit val deserialiserJsonl: Deserialiser[Jsonl] =
    (bytes: Array[Byte]) => Using(scala.io.Source.fromBytes(bytes))(
      _.getLines()
        .map(parse)
        .toVector
        .traverse(_.toValidatedNel)
        .toEither
        .leftMap(_.map(DeserialiserError.apply).reduce)
        .map(Jsonl.apply)
    ).toEither.valueOr(t => DeserialiserError(t.getMessage, t.getCause.some).asLeft)
}

final case class DeserialiserError(message: String, cause: Option[Throwable] = None)
  extends Exception(message, cause.fold(null.asInstanceOf[Throwable])(identity))

object DeserialiserError {
  def apply(t: Throwable): DeserialiserError =
    DeserialiserError(t.getMessage, Option(t.getCause))

  /**
   * Unfortunately we do not accumulate the cause of multiple errors
   */
  implicit val semigroupDeserialiserError: Semigroup[DeserialiserError] =
    (x: DeserialiserError, y: DeserialiserError) => DeserialiserError(List(x.message, y.message).mkString("; "), x.cause orElse y.cause)
}