package com.backwards.aws.s3

import software.amazon.awssdk.services.s3.model.{Bucket => S3Bucket, PutObjectRequest => S3PutObjectRequest}

object PutObjectRequest {
  def apply(bucket: S3Bucket, key: String): S3PutObjectRequest =
    S3PutObjectRequest.builder.bucket(bucket.name).key(key).build
}