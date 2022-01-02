package com.backwards.fp

object FunctionOps {
  object syntax {
    implicit class FunctionExtension[A](a: A) {
      def optional[B](option: Option[B])(some: A => B => A): A =
        option.fold(a)(some(a))

      def foldOptional[B](option: Option[B])(none: A => A)(some: A => B => A): A =
        option.fold(none(a))(some(a))
    }
  }
}