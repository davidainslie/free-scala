package com.backwards.http

import java.net.URI
import cats.InjectK
import cats.free.Free
import cats.free.Free._
import cats.implicits._
import monocle.Lens
import monocle.macros.GenLens
import com.backwards.auth.Credentials
import com.backwards.io.Deserialiser

sealed trait Http[A]

// TODO - Horrible repetition in companion objects
object Http {
  implicit def httpToFree[F[_]: InjectK[Http, *[_]], A](fa: Http[A]): Free[F, A] =
    liftInject[F](fa)

  final case class GrantByPassword(uri: URI, credentials: Credentials) extends Http[Auth]

  final case class GrantByClientCredentials(uri: URI, credentials: Credentials) extends Http[Auth]

  final case class Post[A: Deserialiser](uri: URI, headers: Headers = Headers(), params: Params = Params(), auth: Option[Auth] = None) extends Http.WithDeserialiser[A]

  final case class Put[A: Deserialiser](uri: URI, headers: Headers = Headers(), params: Params = Params(), auth: Option[Auth] = None) extends Http.WithDeserialiser[A]

  final case class Get[A: Deserialiser](uri: URI, headers: Headers = Headers(), params: Params = Params(), auth: Option[Auth] = None) extends Http.WithDeserialiser[A]

  object Post {
    def uriL[A: Deserialiser]: Lens[Post[A], URI] =
      GenLens[Post[A]](_.uri)

    def headersL[A: Deserialiser]: Lens[Post[A], Headers] =
      GenLens[Post[A]](_.headers)

    def paramsL[A: Deserialiser]: Lens[Post[A], Params] =
      GenLens[Post[A]](_.params)

    object WithDeserialiser {
      def unapply[A](post: Post[A]): Option[(URI, Headers, Params, Option[Auth], Deserialiser[A])] =
        (post.uri, post.headers, post.params, post.auth, post.deserialiser).some
    }
  }

  object Put {
    def uriL[A: Deserialiser]: Lens[Put[A], URI] =
      GenLens[Put[A]](_.uri)

    def headersL[A: Deserialiser]: Lens[Put[A], Headers] =
      GenLens[Put[A]](_.headers)

    def paramsL[A: Deserialiser]: Lens[Put[A], Params] =
      GenLens[Put[A]](_.params)

    object WithDeserialiser {
      def unapply[A](put: Put[A]): Option[(URI, Headers, Params, Option[Auth], Deserialiser[A])] =
        (put.uri, put.headers, put.params, put.auth, put.deserialiser).some
    }
  }

  object Get {
    def uriL[A: Deserialiser]: Lens[Get[A], URI] =
      GenLens[Get[A]](_.uri)

    def headersL[A: Deserialiser]: Lens[Get[A], Headers] =
      GenLens[Get[A]](_.headers)

    def paramsL[A: Deserialiser]: Lens[Get[A], Params] =
      GenLens[Get[A]](_.params)

    object WithDeserialiser {
      def unapply[A](get: Get[A]): Option[(URI, Headers, Params, Option[Auth], Deserialiser[A])] =
        (get.uri, get.headers, get.params, get.auth, get.deserialiser).some
    }
  }

  sealed abstract class WithDeserialiser[A: Deserialiser] extends Http[A] {
    val deserialiser: Deserialiser[A] =
      implicitly[Deserialiser[A]]
  }
}