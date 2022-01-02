package com.backwards.auth

import cats.Show
import cats.derived._
import cats.implicits._

final case class Credentials(user: User, password: Password)

object Credentials {
  implicit val showCredentials: Show[Credentials] =
    semiauto.show
}