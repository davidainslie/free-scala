package tech.backwards.http

import monocle.Lens
import monocle.macros.GenLens

final case class Headers(value: Map[String, String] = Map.empty) extends AnyVal

object Headers {
  def apply(kvs: (String, String)*): Headers =
    Headers(kvs.toMap)

  def valueL: Lens[Headers, Map[String, String]] =
    GenLens[Headers](_.value)
}