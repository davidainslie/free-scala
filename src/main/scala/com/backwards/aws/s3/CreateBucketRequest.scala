package com.backwards.aws.s3

import software.amazon.awssdk.services.s3.model.{Bucket => S3Bucket, CreateBucketRequest => S3CreateBucketRequest}

object CreateBucketRequest {
  def apply(bucket: S3Bucket): S3CreateBucketRequest =
    S3CreateBucketRequest.builder.bucket(bucket.name).build
}