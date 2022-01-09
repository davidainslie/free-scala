package com.backwards.aws.s3

import cats.InjectK
import cats.free.Free
import cats.free.Free._
import cats.implicits._
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.model._

/**
 * Algebra to interact with AWS S3 via Free Monad
 * @tparam A Outcome of application
 */
sealed trait S3[A] extends Product with Serializable

object S3 {
  final case class CreateBucket(request: CreateBucketRequest, allowAlreadyExists: Boolean = true) extends S3[CreateBucketResponse]

  final case class PutObject(request: PutObjectRequest, body: RequestBody) extends S3[PutObjectResponse]

  final case class PutStream[A](bucket: Bucket, key: String, data: A, serialiser: Serialiser[A]) extends S3[A]

  final case class CompletePutStream(bucket: Bucket, key: String) extends S3[Unit]

  final case class GetObject(request: GetObjectRequest) extends S3[ResponseInputStream[GetObjectResponse]]

  object PutStream {
    def apply[A](bucket: Bucket, key: String, data: A)(implicit serialiser: Serialiser[A], dummy: DummyImplicit): PutStream[A] =
      apply(bucket, key, data, serialiser)
  }

  implicit def s3ToFree[F[_]: InjectK[S3, *[_]], A](fa: S3[A]): Free[F, A] =
    liftInject[F](fa)
}