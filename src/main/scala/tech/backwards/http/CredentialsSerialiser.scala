package tech.backwards.http

import java.net.URLEncoder.encode
import cats.implicits.catsSyntaxOptionId
import tech.backwards.auth.Credentials

object CredentialsSerialiser {
  sealed trait CredentialsSerialiser extends Serialiser[Credentials] {
    val contentType: ContentType =
      ContentType("application/x-www-form-urlencoded")

    val serialise: List[(String, String)] => Array[Byte] =
      _.map { case (key, value) =>
        s"${encode(key, "UTF-8")}=${encode(value, "UTF-8")}"
      }.mkString("&").getBytes
  }

  implicit object serialiserCredentialsByPassword extends CredentialsSerialiser {
    def serialise(credentials: Credentials): Array[Byte] =
      serialise(
        List(
          "grant_type" -> "password",
          "username" -> credentials.user.value,
          "password" -> credentials.password.value
        )
      )
  }

  implicit object serialiserCredentialsByClientCredentials extends CredentialsSerialiser {
    def serialise(credentials: Credentials): Array[Byte] =
      serialise(
        List(
          "grant_type" -> "client_credentials",
          "client_id" -> credentials.user.value,
          "client_secret" -> credentials.password.value
        )
      )
  }
}