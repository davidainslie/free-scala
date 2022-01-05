package com.backwards.aws.s3

import cats.InjectK
import cats.free.Free
import cats.free.Free._
import cats.implicits._
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model._
import com.backwards.fp.Resource

/**
 * Algebra to interact with AWS S3 via Free Monad
 * @tparam A Outcome of application
 */
sealed trait S3[A] extends Product with Serializable

object S3 {
  final case class CreateBucket(request: CreateBucketRequest) extends S3[CreateBucketResponse]

  final case class PutObject(request: PutObjectRequest, body: RequestBody) extends S3[PutObjectResponse]

  // final case class PutStream(bucket: Bucket, key: String) extends S3[PutStreamHandle]
  final case class PutStream[A](bucket: Bucket, key: String, data: A, serialiser: Serialiser[A]) extends S3[A/*PutStreamHandle*/]

  final case class CompletePutStream(bucket: Bucket, key: String) extends S3[Unit]

  final case class GetObject(request: GetObjectRequest) extends S3[ResponseInputStream[GetObjectResponse]]

  object PutStream {
    def apply[A](bucket: Bucket, key: String, data: A)(implicit serialiser: Serialiser[A], dummy: DummyImplicit): PutStream[A] =
      apply(bucket, key, data, serialiser)
  }

  /*implicit class PutStreamExtension[F[_]](free: Free[F, PutStreamHandle]) {
    def use[B](f: PutStreamHandle => Free[F, B]): Free[F, B] = {
      resource(free).use(f)
    }

    def resource(acquire: => Free[F, PutStreamHandle]): Resource[Free[F, *], PutStreamHandle] =
      new Resource[Free[F, *], PutStreamHandle] {
        override def use[B](f: PutStreamHandle => Free[F, B]): Free[F, B] =
          for {
            a <- acquire
            b <- f(a)
            _ <- Free.pure[F, Unit](a.complete())
          } yield b
    }
  }

  implicit def putStreamExtension[F[_]: InjectK[S3, *[_]]](putStream: PutStream): PutStreamExtension[F] =
    PutStreamExtension(s3ToFree(putStream))*/

  implicit def s3ToFree[F[_]: InjectK[S3, *[_]], A](fa: S3[A]): Free[F, A] =
    liftInject[F](fa)
}