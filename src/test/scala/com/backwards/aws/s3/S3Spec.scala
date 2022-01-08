package com.backwards.aws.s3

import scala.collection.mutable
import cats.free.Free
import cats.implicits._
import cats.{Id, InjectK, ~>}
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.{CreateBucketResponse, GetObjectResponse, PutObjectResponse}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.backwards.aws.s3.S3._
import com.backwards.fp.free.FreeOps.syntax._

class S3Spec extends AnyWordSpec with Matchers {
  "S3 Algebra" should {
    "be applied against a stubbed interpreter (a naive implementation that can just throw exceptions)" in {
      def program(implicit I: InjectK[S3, S3]): Free[S3, ResponseInputStream[GetObjectResponse]] =
        for {
          bucket    <- bucket("my-bucket").liftFree[S3]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString("Blah blah"))
          response  <- GetObject(getObjectRequest(bucket, "foo"))
        } yield response

      val response: Id[ResponseInputStream[GetObjectResponse]] =
        program.foldMap(S3StubInterpreter)

      new String(response.readAllBytes) mustEqual "Blah blah"
    }
  }
}

object S3StubInterpreter extends (S3 ~> Id) {
  private val buckets: mutable.Map[String, Map[String, RequestBody]] =
    mutable.Map.empty

  override def apply[A](fa: S3[A]): Id[A] =
    fa match {
      case CreateBucket(request) =>
        buckets += (request.bucket -> Map.empty[String, RequestBody])
        CreateBucketResponse.builder.build.asInstanceOf[A]

      case PutObject(request, body) =>
        buckets.update(request.bucket, buckets.getOrElse(request.bucket, Map.empty[String, RequestBody]) + (request.key -> body))
        PutObjectResponse.builder.build.asInstanceOf[A]

      case PutStream(bucket, key, data, serialiser) =>
        ??? // TODO

      case GetObject(request) =>
        val requestBody: RequestBody =
          buckets(request.bucket)(request.key)

        new ResponseInputStream[GetObjectResponse](
          GetObjectResponse.builder.build,
          AbortableInputStream.create(requestBody.contentStreamProvider().newStream())
        ).asInstanceOf[A]
    }
}