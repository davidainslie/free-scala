package com.backwards.fp.free

import cats.free.Free

object FreeOps {
  def whenF[F[_], A, B](check: => Boolean, fa: Free[F, A], fb: Free[F, B]) = {
    if (check) fa else fb
  }

  object syntax {
    implicit class ValueExtension[A](self: A) {
      def liftFree[F[_]]: Free[F, A] =
        Free.pure[F, A](self)
    }

    implicit class FreeExtension[F[_], A](fa: Free[F, A]) {
      def as[B](b: => Free[F, B]): Free[F, B] =
        fa.flatMap(_ => b)
    }
  }
}