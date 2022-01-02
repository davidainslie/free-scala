package com.backwards.http

import java.net.URI
import cats.InjectK
import cats.free.Free
import cats.free.Free._
import cats.implicits._
import monocle.Lens
import monocle.macros.GenLens
import com.backwards.auth.Credentials
import com.backwards.io.{Deserialiser, Serialiser}

sealed trait Http[A]

// TODO - Factor out repetition in companion objects
object Http {
  implicit def httpToFree[F[_]: InjectK[Http, *[_]], A](fa: Http[A]): Free[F, A] =
    liftInject[F](fa)

  final case class GrantByPassword(uri: URI, credentials: Credentials) extends Http[Auth]

  final case class GrantByClientCredentials(uri: URI, credentials: Credentials) extends Http[Auth]

  final case class Post[A, B](uri: URI, headers: Headers, params: Params, auth: Option[Auth], body: Option[A], serialiser: Serialiser[A], deserialiser: Deserialiser[B]) extends Http[B]

  final case class Put[A, B](uri: URI, headers: Headers, params: Params, auth: Option[Auth], body: Option[A], serialiser: Serialiser[A], deserialiser: Deserialiser[B]) extends Http[B]

  final case class Get[B](uri: URI, headers: Headers, params: Params, auth: Option[Auth], deserialiser: Deserialiser[B]) extends Http[B]

  object Post {
    def uriL[A: Serialiser, B: Deserialiser]: Lens[Post[A, B], URI] =
      GenLens[Post[A, B]](_.uri)

    def headersL[A: Serialiser, B: Deserialiser]: Lens[Post[A, B], Headers] =
      GenLens[Post[A, B]](_.headers)

    def paramsL[A: Serialiser, B: Deserialiser]: Lens[Post[A, B], Params] =
      GenLens[Post[A, B]](_.params)

    def apply[A, B](uri: URI, headers: Headers = Headers(), params: Params = Params(), auth: Option[Auth] = None, body: Option[A] = None)(implicit serialiser: Serialiser[A], deserialiser: Deserialiser[B], dummy: DummyImplicit): Post[A, B] =
      apply(uri, headers, params, auth, body, serialiser, deserialiser)
  }

  object Put {
    def uriL[A: Serialiser, B: Deserialiser]: Lens[Put[A, B], URI] =
      GenLens[Put[A, B]](_.uri)

    def headersL[A: Serialiser, B: Deserialiser]: Lens[Put[A, B], Headers] =
      GenLens[Put[A, B]](_.headers)

    def paramsL[A: Serialiser, B: Deserialiser]: Lens[Put[A, B], Params] =
      GenLens[Put[A, B]](_.params)

    def apply[A, B](uri: URI, headers: Headers = Headers(), params: Params = Params(), auth: Option[Auth] = None, body: Option[A] = None)(implicit serialiser: Serialiser[A], deserialiser: Deserialiser[B], dummy: DummyImplicit): Put[A, B] =
      apply(uri, headers, params, auth, body, serialiser, deserialiser)
  }

  object Get {
    def uriL[B: Deserialiser]: Lens[Get[B], URI] =
      GenLens[Get[B]](_.uri)

    def headersL[B: Deserialiser]: Lens[Get[B], Headers] =
      GenLens[Get[B]](_.headers)

    def paramsL[B: Deserialiser]: Lens[Get[B], Params] =
      GenLens[Get[B]](_.params)

    def apply[B](uri: URI, headers: Headers = Headers(), params: Params = Params(), auth: Option[Auth] = None)(implicit deserialiser: Deserialiser[B], dummy: DummyImplicit): Get[B] =
      apply(uri, headers, params, auth, deserialiser)
  }
}