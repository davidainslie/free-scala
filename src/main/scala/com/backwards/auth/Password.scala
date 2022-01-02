package com.backwards.auth

import cats.Show
import cats.derived.semiauto
import eu.timepit.refined.cats._
import eu.timepit.refined.types.string.NonEmptyString

final case class Password(value: NonEmptyString)

object Password {
  implicit val showPassword: Show[Password] =
    semiauto.show
}