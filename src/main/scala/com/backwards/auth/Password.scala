package com.backwards.auth

import cats.Show
import cats.derived.semiauto
import eu.timepit.refined.types.string.NonEmptyString
import com.backwards.fp.ShowRefined

final case class Password(value: NonEmptyString)

object Password extends ShowRefined {
  implicit val showPassword: Show[Password] =
    semiauto.show
}