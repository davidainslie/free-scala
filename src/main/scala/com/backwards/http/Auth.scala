package com.backwards.http

import scala.concurrent.duration.{Duration, DurationInt}
import cats._
import cats.derived._
import cats.implicits._
import eu.timepit.refined.cats._
import eu.timepit.refined.types.string.NonEmptyString
import io.circe.{Decoder, DecodingFailure, Encoder}
import com.backwards.auth.Credentials

sealed trait Auth

object Auth {
  // TODO - WIP - Need to add Basic, BasicToken and Digest
  implicit val decoderAuth: Decoder[Auth] =
    List[Decoder[Auth]](
      Decoder[Bearer].widen
    ).reduceLeft(_ or _)

  implicit val showAuth: Show[Auth] =
    semiauto.show
}

final case class Basic(credentials: Credentials) extends Auth

object Basic {
  implicit val showBasic: Show[Basic] =
    semiauto.show
}

final case class BasicToken(token: NonEmptyString) extends Auth

object BasicToken {
  implicit val showBasicToken: Show[BasicToken] =
    semiauto.show
}

final case class Digest(credentials: Credentials) extends Auth

object Digest {
  implicit val showDigest: Show[Digest] =
    semiauto.show
}

final case class Bearer(token: NonEmptyString, expiresIn: Duration) extends Auth {
  lazy val authorization: String = s"${Bearer.key} ${token.value}"
}

object Bearer {
  val key: String = "Bearer"

  implicit val decoderBearer: Decoder[Bearer] =
    Decoder.instance(hcursor =>
      for {
        accessToken <- hcursor.get[String]("access_token").map(NonEmptyString.unsafeFrom)
        expiresIn <- hcursor.get[Int]("expires_in")
        _ <- hcursor.get[String]("token_type").flatMap {
          case `key` => Bearer.asRight
          case x => DecodingFailure(s"Expected $key token type and not: $x", Nil).asLeft
        }
      } yield
        Bearer(accessToken, expiresIn.seconds)
    )

  implicit val encoderBearer: Encoder[Bearer] =
    Encoder.forProduct3("access_token", "expires_in", "token_type")(t =>
      (t.token.value, t.expiresIn.toSeconds, key)
    )

  implicit val showBearer: Show[Bearer] =
    semiauto.show
}