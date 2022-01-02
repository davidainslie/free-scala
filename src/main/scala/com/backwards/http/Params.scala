package com.backwards.http

import monocle.Lens
import monocle.macros.GenLens

final case class Params(value: Map[String, String] = Map.empty) extends AnyVal {
  def +(keyValue: (String, String)): Params =
    Params.valueL.modify(_ + (keyValue._1 -> keyValue._2))(this)

  def +(keyValue: (String, Int))(implicit dummyImplicit: DummyImplicit): Params =
    Params.valueL.modify(_ + (keyValue._1 -> keyValue._2.toString))(this)
}

object Params {
  def valueL: Lens[Params, Map[String, String]] =
    GenLens[Params](_.value)
}