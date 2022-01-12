package com.backwards.json

import cats.Show
import io.circe.Json

final case class Jsonl(value: Vector[Json]) extends AnyVal

object Jsonl {
  implicit val showJsonl: Show[Jsonl] =
    _.value.map(_.noSpaces).mkString("\n")

  def apply(xs: Option[Vector[Json]]): Jsonl =
    Jsonl(xs.getOrElse(Vector.empty[Json]))
}