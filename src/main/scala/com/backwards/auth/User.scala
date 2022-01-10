package com.backwards.auth

import cats.Show
import cats.derived.semiauto

final case class User(value: String) extends AnyVal

object User {
  implicit val showUser: Show[User] =
    semiauto.show
}