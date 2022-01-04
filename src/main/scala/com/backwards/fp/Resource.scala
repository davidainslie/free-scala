package com.backwards.fp

/**
 * Even though using [[cats.effect.Resource]] is recommended, this Resource is a fallback for a simpler approach to managing a Resource monadically.
 * Example usage see [[com.backwards.aws.s3.S3.PutStreamExtension]]
 * @tparam F
 * @tparam A
 */
trait Resource[F[_], A] {
  def use[B](f: A => F[B]): F[B]
}