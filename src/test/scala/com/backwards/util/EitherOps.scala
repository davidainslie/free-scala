package com.backwards.util

object EitherOps {
  object syntax {
    implicit class EitherExtension[L, R](either: Either[L, R]) {
      def rightValue: R =
        either.fold(l => throw new Exception(s"Expected Right but got Left: $l"), identity)

      def leftValue: L =
        either.fold(identity, r => throw new Exception(s"Expected Left but got Right: $r"))
    }
  }
}