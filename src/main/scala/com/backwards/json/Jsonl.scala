package com.backwards.json

import cats.implicits._
import cats.{Monoid, Show}
import io.circe.Json

final case class Jsonl(value: Vector[Json] = Vector.empty) extends AnyVal

object Jsonl {
  implicit val monoidJsonl: Monoid[Jsonl] = new Monoid[Jsonl] {
    def empty: Jsonl =
      Jsonl()

    def combine(x: Jsonl, y: Jsonl): Jsonl =
      Jsonl(x.value combine y.value)
  }

  implicit val showJsonl: Show[Jsonl] =
    _.value.map(_.noSpaces).mkString("\n")

  def apply(xs: Option[Vector[Json]]): Jsonl =
    Jsonl(xs.getOrElse(Vector.empty[Json]))
}