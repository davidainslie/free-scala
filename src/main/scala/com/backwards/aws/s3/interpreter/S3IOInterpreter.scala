package com.backwards.aws.s3.interpreter

import java.util.concurrent.CompletableFuture
import alex.mojaki.s3upload.{MultiPartOutputStream, StreamTransferManager}
import cats.effect.IO
import cats.implicits._
import cats.~>
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.core.{ResponseBytes, ResponseInputStream}
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.{GetObjectResponse, PutObjectResponse}
import com.amazonaws.util.IOUtils
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3.{PutStreamHandle, S3, S3Client}

/**
 * Asynchronous S3 Algebra Interpreter
 * TODO - Regarding non-stream cases, there is a fs2 S3 library that could be spiked.
 */
object S3IOInterpreter {
  def apply(s3Client: S3Client): S3 ~> IO =
    new (S3 ~> IO) {
      override def apply[A](fa: S3[A]): IO[A] =
        fa match {
          case CreateBucket(request) =>
            IO.fromCompletableFuture(IO(s3Client.v2.async.createBucket(request))).map(_.asInstanceOf[A])

          case PutObject(request, body) =>
            val bytes: Array[Byte] =
              IOUtils.toByteArray(body.contentStreamProvider().newStream())

            val putObjectResponseFuture: CompletableFuture[PutObjectResponse] =
              s3Client.v2.async.putObject(request, AsyncRequestBody.fromBytes(bytes))

            IO.fromCompletableFuture(IO(putObjectResponseFuture)).map(_.asInstanceOf[A])

          case PutStream(bucket, key) =>
            IO {
              val manager: StreamTransferManager =
                new StreamTransferManager(bucket.name, key, s3Client.v1.sync)

              val outputStream: MultiPartOutputStream =
                manager.getMultiPartOutputStreams.get(0)

              PutStreamHandle(manager, outputStream).asInstanceOf[A]
            }

          case GetObject(request) =>
            val asyncResponseTransformer: AsyncResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]] =
              AsyncResponseTransformer.toBytes()

            val getObjectResponseFuture: CompletableFuture[ResponseBytes[GetObjectResponse]] =
              s3Client.v2.async.getObject(request, asyncResponseTransformer)

            IO.fromCompletableFuture(IO(getObjectResponseFuture)).map(responseBytes =>
              new ResponseInputStream[GetObjectResponse](
                responseBytes.response(),
                AbortableInputStream.create(responseBytes.asInputStream())
              ).asInstanceOf[A]
            )
        }
    }
}