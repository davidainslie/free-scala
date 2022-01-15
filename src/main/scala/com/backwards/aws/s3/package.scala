package com.backwards.aws

import cats.effect.Sync
import software.amazon.awssdk.auth.credentials.{AwsCredentials, DefaultCredentialsProvider}
import software.amazon.awssdk.services.s3.model._

package object s3 {
  def awsCredentials[F[_]: Sync]: F[AwsCredentials] =
    Sync[F].delay(DefaultCredentialsProvider.create.resolveCredentials)

  val bucket: String => Bucket =
    name => Bucket.builder.name(name).build

  val createBucketRequest: Bucket => CreateBucketRequest =
    bucket => CreateBucketRequest.builder.bucket(bucket.name).build

  val getObjectRequest: (Bucket, String) => GetObjectRequest =
    (bucket, key) => GetObjectRequest.builder.bucket(bucket.name).key(key).build

  val putObjectRequest: (Bucket, String) => PutObjectRequest =
    (bucket, key) => PutObjectRequest.builder.bucket(bucket.name).key(key).build
}