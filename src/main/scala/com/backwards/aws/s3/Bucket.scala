package com.backwards.aws.s3

import software.amazon.awssdk.services.s3.model.{Bucket => S3Bucket}

object Bucket {
  def apply(name: String): S3Bucket =
    S3Bucket.builder.name(name).build
}