package com.backwards.text

/**
 * Type class to generate a (simple/primitive) String value e.g. a value representation as part of a key value pair.
 * @tparam A
 */
trait StringValue[A] {
  def stringValue(a: A): String
}

object StringValue {
  def apply[A: StringValue]: StringValue[A] =
    implicitly

  def apply[A: StringValue](a: A): String =
    apply[A].stringValue(a)

  implicit val stringValueString: StringValue[String] =
    identity

  implicit val stringValueBoolean: StringValue[Boolean] =
    _.toString

  implicit val stringValueByte: StringValue[Byte] =
    _.toString

  implicit val stringValueShort: StringValue[Short] =
    _.toString

  implicit val stringValueInt: StringValue[Int] =
    _.toString

  implicit val stringValueLong: StringValue[Long] =
    _.toString

  implicit val stringValueFloat: StringValue[Float] =
    _.toString

  implicit val stringValueDouble: StringValue[Double] =
    _.toString
}