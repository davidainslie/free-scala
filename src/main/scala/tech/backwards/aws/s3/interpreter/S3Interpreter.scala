package tech.backwards.aws.s3.interpreter

import java.util.concurrent.atomic.AtomicReference
import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps
import cats.effect.Resource
import cats.implicits._
import cats.{Id, ~>}
import software.amazon.awssdk.services.s3.model.{Bucket, BucketAlreadyOwnedByYouException, CreateBucketResponse}
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3.{PutStreamHandle, PutStreamHandleKey, S3, S3Client}

/**
 * Default and simplest synchronous S3 Algebra Interpreter
 */
private class S3Interpreter private(s3Client: S3Client, putStreamHandles: AtomicReference[Map[PutStreamHandleKey, PutStreamHandle]]) extends (S3 ~> Id) {
  private def close(): Unit =
    putStreamHandles.getAndUpdate(_.values.foreach(_.abort()).pipe(_ => Map.empty[PutStreamHandleKey, PutStreamHandle]))

  override def apply[A](fa: S3[A]): Id[A] =
    fa match {
      case CreateBucket(request, allowAlreadyExists) =>
        Try(s3Client.v2.sync.createBucket(request)).toEither.valueOr({
          case _: BucketAlreadyOwnedByYouException if allowAlreadyExists => CreateBucketResponse.builder.build
          case t => throw t
        }).asInstanceOf[A]

      case PutObject(request, body) =>
        s3Client.v2.sync.putObject(request, body).asInstanceOf[A]

      case PutStream(bucket, key, data, serialiser) =>
        putStreamHandles.getAndUpdate(_.updatedWith(PutStreamHandleKey(bucket, key)) {
          case Some(h) =>
            h.tap(_.write(data)(serialiser)).pipe(_.some)

          case None =>
            PutStreamHandle(s3Client, bucket, key).tap(_.write(data)(serialiser)).pipe(_.some)
        }).pipe(_ => ().asInstanceOf[A])

      case CompletePutStream(bucket: Bucket, key: String) =>
        putStreamHandles.getAndUpdate { hs =>
          val putStreamHandleKey: PutStreamHandleKey =
            PutStreamHandleKey(bucket, key)

          hs.get(putStreamHandleKey).foreach(h => h.complete()).pipe(_ => hs - putStreamHandleKey)
        }.pipe(_ => ().asInstanceOf[A])

      case GetObject(request, deserialiser) =>
        deserialiser.deserialise(s3Client.v2.sync.getObject(request).readAllBytes).fold(throw _, identity)
    }
}

object S3Interpreter {
  def resource(s3Client: S3Client): Resource[Id, S3 ~> Id] =
    Resource.make(Id(new S3Interpreter(s3Client, new AtomicReference(Map.empty[PutStreamHandleKey, PutStreamHandle]))))(_.close())
}