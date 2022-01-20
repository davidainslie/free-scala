package tech.backwards.fp

import scala.annotation.tailrec
import cats.effect.MonadCancel
import cats.effect.kernel.{CancelScope, Poll}
import cats.{Id, MonadError}

package object implicits {
  /**
   * WARNING - Effectful programs should not use "Id" as Id is not lawful thus throwing exceptions
   * i.e. the monadic instances of Id is not recommended for production code.
   * Production code should use a Monad with some actual error handling effect.
   */
  implicit val monadErrorId: MonadError[Id, Throwable] =
    new MonadError[Id, Throwable] {
      override def raiseError[A](e: Throwable): Id[A] = {
        scribe.error(e.getMessage, e)
        throw e
      }

      override def handleErrorWith[A](fa: Id[A])(f: Throwable => Id[A]): Id[A] =
        fa

      override def pure[A](x: A): Id[A] =
        x

      override def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] =
        f(fa)

      @tailrec
      override def tailRecM[A, B](a: A)(f: A => Id[A Either B]): Id[B] =
        f(a) match {
          case Right(b) => b
          case Left(a) => tailRecM(a)(f)
        }
    }

  /**
   * WARNING - Effectful programs should not use "Id" as Id is not lawful thus throwing exceptions
   * i.e. the monadic instances of Id is not recommended for production code.
   * Production code should use a Monad with some actual error handling effect.
   */
  implicit val monadCancelId: MonadCancel[Id, Throwable] =
    new MonadCancel[Id, Throwable] {
      def rootCancelScope: CancelScope =
        CancelScope.Uncancelable

      def forceR[A, B](fa: Id[A])(fb: Id[B]): Id[B] =
        fb

      def uncancelable[A](body: Poll[Id] => Id[A]): Id[A] =
        body(new Poll[Id] {
          def apply[A](fa: Id[A]): Id[A] = fa
        })

      def canceled: Id[Unit] =
        ()

      def onCancel[A](fa: Id[A], fin: Id[Unit]): Id[A] =
        fa

      def pure[A](x: A): Id[A] =
        x

      def flatMap[A, B](fa: Id[A])(f: A => Id[B]): Id[B] =
        f(fa)

      @tailrec
      def tailRecM[A, B](a: A)(f: A => Id[Either[A, B]]): Id[B] =
        f(a) match {
          case Right(b) => b
          case Left(a) => tailRecM(a)(f)
        }

      def raiseError[A](e: Throwable): Id[A] = {
        scribe.error(e.getMessage, e)
        throw e
      }

      def handleErrorWith[A](fa: Id[A])(f: Throwable => Id[A]): Id[A] =
        fa
    }
}