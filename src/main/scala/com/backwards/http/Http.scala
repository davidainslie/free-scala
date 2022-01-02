package com.backwards.http

import java.net.URI
import cats.InjectK
import cats.free.Free
import cats.free.Free._
import cats.implicits._
import monocle.{Lens, PLens}
import monocle.macros.GenLens
import com.backwards.auth.Credentials
import com.backwards.io.Deserialiser

sealed trait Http[A]

object Http {
  implicit def httpToFree[F[_]: InjectK[Http, *[_]], A](fa: Http[A]): Free[F, A] =
    liftInject[F](fa)

  final case class GrantByPassword(uri: URI, credentials: Credentials) extends Http[Auth]

  final case class GrantByClientCredentials(uri: URI, credentials: Credentials) extends Http[Auth]

  final case class Post[A: Deserialiser](uri: URI) extends Http.WithDeserialiser[A]

  final case class Put[A: Deserialiser]() extends Http.WithDeserialiser[A]

  final case class Get[A: Deserialiser](uri: URI, headers: Headers = Headers(), params: Params = Params()) extends Http.WithDeserialiser[A]

  object Get {
    def uriL[A: Deserialiser]: Lens[Get[A], URI] =
      GenLens[Get[A]](_.uri)

    def headersL[A: Deserialiser]: Lens[Get[A], Headers] =
      GenLens[Get[A]](_.headers)

    def paramsL[A: Deserialiser]: Lens[Get[A], Params] =
      GenLens[Get[A]](_.params)

    object WithDeserialiser {
      def unapply[A](get: Get[A]): Option[(URI, Headers, Params, Deserialiser[A])] =
        (get.uri, get.headers, get.params, get.deserialiser).some
    }
  }

  sealed abstract class WithDeserialiser[A: Deserialiser] extends Http[A] {
    val deserialiser: Deserialiser[A] =
      implicitly[Deserialiser[A]]
  }
}