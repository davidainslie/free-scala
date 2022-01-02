package com.backwards.auth

import cats.Show
import cats.derived.semiauto
import eu.timepit.refined.cats._
import eu.timepit.refined.types.string.NonEmptyString

final case class User(value: NonEmptyString)

object User {
  implicit val showUser: Show[User] =
    semiauto.show
}