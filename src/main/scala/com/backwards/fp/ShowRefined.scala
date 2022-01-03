package com.backwards.fp

import cats.Show
import eu.timepit.refined.api.RefType
import eu.timepit.refined.cats

object ShowRefined extends ShowRefined

/**
 * To Show a Refined type simply:
 * {{{
 *   import eu.timepit.refined.cats._
 * }}}
 * However, Intellij (unless configured) will think the import is unused and can remove it.
 * Hence, just mixin the following to avoid this.
 */
trait ShowRefined {
  implicit def refTypeShow[F[_, _], T: Show, P](implicit rt: RefType[F]): Show[F[T, P]] =
    cats.derivation.refTypeViaContravariant[F, Show, T, P]
}