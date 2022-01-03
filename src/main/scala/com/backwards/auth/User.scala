package com.backwards.auth

import cats.Show
import cats.derived.semiauto
import eu.timepit.refined.types.string.NonEmptyString
import com.backwards.fp.ShowRefined

final case class User(value: NonEmptyString)

object User extends ShowRefined {
  implicit val showUser: Show[User] =
    semiauto.show
}