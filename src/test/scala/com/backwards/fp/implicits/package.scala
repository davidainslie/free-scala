package com.backwards.fp

import scala.annotation.tailrec
import cats.{Id, MonadError}

package object implicits {
  /**
   * Only intended for tests i.e. production code must not use a MonadError for Id as it is not lawful.
   * Production code must use a Monad with some actual error handling effect.
   */
  implicit val monadErrorId: MonadError[Id, Throwable] =
    new MonadError[Id, Throwable] {
      override def raiseError[A](e: Throwable): Id[A] = {
        scribe.error(e.getMessage, e)
        throw e
      }

      override def handleErrorWith[A](fa: Id[A])(f: Throwable => Id[A]): Id[A] = {
        scribe.error(s"Ignoring error")
        fa
      }

      override def pure[A](x: A): Id[A] =
        x

      override def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] =
        f(fa)

      @tailrec
      override def tailRecM[A, B](a: A)(f: A => Id[Either[A, B]]): Id[B] =
        f(a) match {
          case Right(b) => b
          case Left(a) => tailRecM(a)(f)
        }
    }
}