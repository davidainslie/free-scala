package com.backwards.aws.s3.interpreter

import cats.implicits._
import cats.{Id, ~>}
import com.backwards.aws.s3.{S3, S3Client}
import com.backwards.aws.s3.S3._

/**
 * Default and simplest synchronous S3 Algebra Interpreter
 */
object S3Interpreter {
  def apply(s3Client: S3Client): S3 ~> Id =
    new (S3 ~> Id) {
      override def apply[A](fa: S3[A]): Id[A] =
        fa match {
          case CreateBucket(request, allowAlreadyExists) =>
            s3Client.v2.sync.createBucket(request).asInstanceOf[A]

          case PutObject(request, body) =>
            s3Client.v2.sync.putObject(request, body).asInstanceOf[A]

          case PutStream(bucket, key, data, serialiser) =>
            ??? // TODO

          case GetObject(request) =>
            s3Client.v2.sync.getObject(request).asInstanceOf[A]
        }
    }
}