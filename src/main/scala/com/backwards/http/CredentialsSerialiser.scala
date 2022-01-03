package com.backwards.http

import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets.UTF_8
import cats.implicits.catsSyntaxOptionId
import com.backwards.auth.Credentials

// TODO - Factor out repetition
object CredentialsSerialiser {
  implicit object serialiserCredentialsByPassword extends Serialiser[Credentials] {
    val contentType: Option[String] =
      "application/x-www-form-urlencoded".some

    def serialise(credentials: Credentials): Array[Byte] =
      List(
        "grant_type" -> "password",
        "username" -> credentials.user.value.value,
        "password" -> credentials.password.value.value
      ).map { case (key, value) =>
        s"""${encode(key, UTF_8)}=${encode(value, UTF_8)}"""
      }.mkString("&").getBytes
  }

  implicit object serialiserCredentialsByClientCredentials extends Serialiser[Credentials] {
    val contentType: Option[String] =
      "application/x-www-form-urlencoded".some

    def serialise(credentials: Credentials): Array[Byte] =
      List(
        "grant_type" -> "client_credentials",
        "client_id" -> credentials.user.value.value,
        "client_secret" -> credentials.password.value.value
      ).map { case (key, value) =>
        s"""${encode(key, UTF_8)}=${encode(value, UTF_8)}"""
      }.mkString("&").getBytes
  }
}