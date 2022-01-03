package com.backwards.http

import java.net.URLEncoder.encode
import cats.implicits.catsSyntaxOptionId
import com.backwards.auth.Credentials

// TODO - Factor out repetition
object CredentialsSerialiser {
  object ByPassword {
    implicit val serialiserCredentialsByPassword: Serialiser[Credentials] = new Serialiser[Credentials] {
      val contentType: Option[String] =
        "application/x-www-form-urlencoded".some

      def serialise(credentials: Credentials): Array[Byte] =
        List(
          "grant_type" -> "password",
          "username" -> credentials.user.value.value,
          "password" -> credentials.password.value.value
        ).map { case (key, value) =>
          s"""${encode(key, "utf-8")}=${encode(value, "utf-8")}"""
        }.mkString("&").getBytes
    }
  }

  object ByClientCredentials {
    implicit val serialiserCredentialsByClientCredentials: Serialiser[Credentials] = new Serialiser[Credentials] {
      val contentType: Option[String] =
        "application/x-www-form-urlencoded".some

      def serialise(credentials: Credentials): Array[Byte] =
        List(
          "grant_type" -> "client_credentials",
          "client_id" -> credentials.user.value.value,
          "client_secret" -> credentials.password.value.value
        ).map { case (key, value) =>
          s"""${encode(key, "utf-8")}=${encode(value, "utf-8")}"""
        }.mkString("&").getBytes
    }
  }
}