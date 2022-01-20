package tech.backwards.auth

import cats.Show

final case class Password(value: String) extends AnyVal

object Password {
  implicit val showPassword: Show[Password] =
    Show.show(_ => "Password(value = <password>)")
}