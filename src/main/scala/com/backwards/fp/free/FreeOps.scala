package com.backwards.fp.free

import cats.free.Free

object FreeOps {
  object syntax {
    implicit class ValueExtension[A](self: A) {
      def liftFree[F[_]]: Free[F, A] =
        Free.pure[F, A](self)
    }

    implicit class FreeExtension[S[_], A](fa: Free[S, A]) {
      def as[B](b: => B): Free[S, B] =
        fa.map(_ => b)

      def when[B](check: => Boolean, otherwise: Free[S, B]) =
        if (check) fa else otherwise
    }
  }
}