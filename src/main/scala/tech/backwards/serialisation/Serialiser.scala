package tech.backwards.serialisation

import scala.util.chaining.scalaUtilChainingOps
import io.circe.Json
import tech.backwards.json.Jsonl

trait Serialiser[A] {
  def serialise(data: A): Array[Byte]
}

object Serialiser {
  def apply[A: Serialiser]: Serialiser[A] =
    implicitly

  implicit val serialiserNothing: Serialiser[Nothing] =
    (_: Nothing) => Array.emptyByteArray

  implicit val serialiserListJson: Serialiser[List[Json]] =
    _.map(_.noSpaces).mkString("", "\n", "\n").getBytes

  implicit val serialiserVectorJson: Serialiser[Vector[Json]] =
    _.map(_.noSpaces).mkString("", "\n", "\n").getBytes

  implicit val serialiserJsonl: Serialiser[Jsonl] =
    _.value.pipe(serialiserVectorJson.serialise)

  implicit val serialiserJson: Serialiser[Json] =
    _.noSpaces.getBytes
}