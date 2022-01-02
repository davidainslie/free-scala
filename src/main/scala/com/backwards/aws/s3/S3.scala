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
sealed trait S3[A]

object S3 {
  implicit def s3ToFree[F[_]: InjectK[S3, *[_]], A](fa: S3[A]): Free[F, A] =
    liftInject[F](fa)

  final case class CreateBucket(request: CreateBucketRequest) extends S3[CreateBucketResponse]

  final case class PutObject(request: PutObjectRequest, body: RequestBody) extends S3[PutObjectResponse]

  final case class PutStream(bucket: Bucket, key: String) extends S3[PutStreamHandle]

  final case class GetObject(request: GetObjectRequest) extends S3[ResponseInputStream[GetObjectResponse]]
}