package com.backwards.aws.s3.interpreter

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import cats.effect.{IO, Resource}
import cats.implicits._
import cats.~>
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.core.{ResponseBytes, ResponseInputStream}
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.{Bucket, GetObjectResponse, PutObjectResponse}
import com.amazonaws.util.IOUtils
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3.{PutStreamHandle, S3, S3Client}

/**
 * Asynchronous S3 Algebra Interpreter
 * TODO - Regarding non-stream cases, there is a fs2 S3 library that could be spiked.
 * TODO - This interpreter carries "state".
 * It would be better to encapsulate state within Free and add to S3 Algebra an ADT that would delegate to some other Free.
 * e.g. PutStream(bucket, key, nextFree)
 */
class S3IOInterpreter private(s3Client: S3Client) extends (S3 ~> IO) {
  private final case class PutStreamHandleKey(bucket: Bucket, key: String)

  // TODO - Any point making this a Ref?
  private val putStreamHandles: AtomicReference[Map[PutStreamHandleKey, PutStreamHandle]] =
    new AtomicReference(Map.empty[PutStreamHandleKey, PutStreamHandle])

  private def close: IO[Unit] =
    IO {
      putStreamHandles.getAndUpdate { hs =>
        hs.values.foreach(_.abort())
        Map.empty[PutStreamHandleKey, PutStreamHandle]
      }
    }

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

      case PutStream(bucket, key, data, serialiser) =>
        IO {
          putStreamHandles.getAndUpdate { hs =>
            hs.updatedWith(PutStreamHandleKey(bucket, key)) {
              case Some(h) =>
                h.write(data)(serialiser)
                h.some

              case None =>
                val h = PutStreamHandle(s3Client, bucket, key)
                h.write(data)(serialiser)
                h.some
            }
          }
      } >> IO.unit.map(_.asInstanceOf[A])

      case CompletePutStream(bucket: Bucket, key: String) =>
        IO {
          putStreamHandles.getAndUpdate { hs =>
            val putStreamHandleKey: PutStreamHandleKey =
              PutStreamHandleKey(bucket, key)

            hs.get(putStreamHandleKey).foreach(h => h.complete())
            hs - putStreamHandleKey
          }
        } >> IO.unit.map(_.asInstanceOf[A])

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

object S3IOInterpreter {
  def resource(s3Client: S3Client): Resource[IO, S3 ~> IO] =
    Resource.make(IO(new S3IOInterpreter(s3Client)))(_.close)
}