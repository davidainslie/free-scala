package tech.backwards.fp.free

import cats.InjectK
import cats.free.Free

object FreeOps {
  object syntax {
    def when[F[_], A, B](check: => Boolean, fa: Free[F, A], fb: Free[F, B]) =
      if (check) fa else fb

    def unit[F[_]]: Free[F, Unit] =
      ().toFree[F]

    implicit class ValueExtension[A](self: A) {
      def toFree[F[_]]: Free[F, A] =
        Free.pure[F, A](self)
    }

    implicit class FreeExtension[F[_], A](fa: Free[F, A]) {
      def as[B](b: => Free[F, B]): Free[F, B] =
        fa.flatMap(_ => b)
    }

    implicit class FExtension[F[_], A](fa: F[A]) {
      def injectFree[G[_]](implicit I: InjectK[F, G]): Free[G, A] =
        Free.liftInject(fa)
    }
  }
}