package tech.backwards.http

import monocle.Lens
import monocle.macros.GenLens
import tech.backwards.text.StringValue

final case class Params(value: Map[String, String] = Map.empty) extends AnyVal {
  def +[V: StringValue](keyValue: (String, V)): Params =
    Params.valueL.modify(_ + (keyValue._1 -> StringValue[V](keyValue._2)))(this)
}

object Params {
  def apply(kvs: (String, String)*): Params =
    Params(kvs.toMap)

  def valueL: Lens[Params, Map[String, String]] =
    GenLens[Params](_.value)
}