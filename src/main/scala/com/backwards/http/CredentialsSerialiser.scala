package com.backwards.http

import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets.UTF_8
import cats.implicits.catsSyntaxOptionId
import com.backwards.auth.Credentials

object CredentialsSerialiser { self =>
  val serialise: List[(String, String)] => Array[Byte] =
    _.map { case (key, value) =>
      encode(key, UTF_8) + "=" + encode(value, UTF_8)
    }.mkString("&").getBytes

  implicit object serialiserCredentialsByPassword extends Serialiser[Credentials] {
    val contentType: Option[String] =
      "application/x-www-form-urlencoded".some

    def serialise(credentials: Credentials): Array[Byte] =
      self.serialise(
        List(
          "grant_type" -> "password",
          "username" -> credentials.user.value.value,
          "password" -> credentials.password.value.value
        )
      )
  }

  implicit object serialiserCredentialsByClientCredentials extends Serialiser[Credentials] {
    val contentType: Option[String] =
      "application/x-www-form-urlencoded".some

    def serialise(credentials: Credentials): Array[Byte] =
      self.serialise(
        List(
          "grant_type" -> "client_credentials",
          "client_id" -> credentials.user.value.value,
          "client_secret" -> credentials.password.value.value
        )
      )
  }
}