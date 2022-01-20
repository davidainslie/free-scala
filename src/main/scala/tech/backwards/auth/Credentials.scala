package tech.backwards.auth

import cats.Show
import cats.derived._
import cats.implicits._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json}
import tech.backwards.json.JsonOps.Codec
import tech.backwards.serialisation.{Deserialiser, DeserialiserError}

final case class Credentials(user: User, password: Password)

object Credentials extends Codec {
  implicit val showCredentials: Show[Credentials] =
    semiauto.show

  implicit val decoderCredentials: Decoder[Credentials] =
    deriveDecoder[Credentials]

  implicit val encoderCredentials: Encoder[Credentials] =
    deriveEncoder[Credentials]

  implicit def deserialiserCredentials(implicit Deserialiser: Deserialiser[Json], Decoder: Decoder[Credentials]): Deserialiser[Credentials] =
    (bytes: Array[Byte]) => Deserialiser.deserialise(bytes).flatMap(Decoder.decodeJson).leftMap(DeserialiserError.apply)
}
