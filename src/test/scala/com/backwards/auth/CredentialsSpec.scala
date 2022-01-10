package com.backwards.auth

import cats.implicits._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CredentialsSpec extends AnyWordSpec with Matchers {
  "Credentials" should {
    "be shown with masked Password" in {
      val credentials: Credentials =
        Credentials(User("username"), Password("private"))

      credentials.show mustEqual "Credentials(user = User(value = username), password = Password(value = <password>))"
    }
  }
}