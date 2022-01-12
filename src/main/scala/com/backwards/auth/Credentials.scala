package com.backwards.auth

import cats.Show
import cats.derived._
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import com.backwards.json.JsonOps

final case class Credentials(user: User, password: Password)

object Credentials extends JsonOps.Codec {
  implicit val showCredentials: Show[Credentials] =
    semiauto.show

  implicit val decoderCredentials: Decoder[Credentials] =
    deriveDecoder[Credentials]

  implicit val encoderCredentials: Encoder[Credentials] =
    deriveEncoder[Credentials]
}
