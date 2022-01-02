package com.backwards.aws.s3

import software.amazon.awssdk.services.s3.model.{Bucket => S3Bucket, GetObjectRequest => S3GetObjectRequest}

object GetObjectRequest {
  def apply(bucket: S3Bucket, key: String): S3GetObjectRequest =
    S3GetObjectRequest.builder.bucket(bucket.name).key(key).build
}