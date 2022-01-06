package com.backwards.aws.s3.interpreter

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import alex.mojaki.s3upload.{MultiPartOutputStream, StreamManager}
import cats.effect.IO
import cats.implicits._
import cats.~>
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.core.{ResponseBytes, ResponseInputStream}
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.{Bucket, GetObjectResponse, PutObjectResponse}
import com.amazonaws.util.IOUtils
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3.{PutStreamHandle, S3, S3Client, Serialiser}

/**
 * Asynchronous S3 Algebra Interpreter
 * TODO - Regarding non-stream cases, there is a fs2 S3 library that could be spiked.
 */
object S3IOInterpreter {
  /*val putStreamHandles: AtomicReference[Map[(Bucket, String), PutStreamHandle]] =
        new AtomicReference[Map[(Bucket, String), PutStreamHandle]]()*/

  private val putStreamHandles: AtomicReference[Map[String, PutStreamHandle]] =
    new AtomicReference(Map.empty[String, PutStreamHandle])

  def failure(t: Throwable): Throwable = {
    println("====> aha")
    putStreamHandles.get.values.foreach(_.abort(t))
    t
  }

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

          case PutStream(bucket, key, data, serialiser) =>
            IO {

              putStreamHandles.getAndUpdate { hs =>
                hs.updatedWith(key) {
                  case Some(h) =>
                    try {
                      scribe.info(s"Writing output stream to S3 bucket = $bucket, key = $key")
                      h.outputStream.write(serialiser.serialise(data))
                      h.some
                    } catch {
                      case t: Throwable =>
                        scribe.error(s"Aborting output stream to S3 bucket = $bucket, key = $key", t)
                        h.streamManager.abort()
                        h.some
                    }
                  case None =>
                    val h = PutStreamHandle(s3Client, bucket, key)

                    try {
                      scribe.info(s"Writing new output stream write to S3 bucket = $bucket, key = $key")
                      h.outputStream.write(serialiser.serialise(data))
                      h.some
                    } catch {
                      case t: Throwable =>
                        scribe.error(s"Aborting output stream write to S3 bucket = $bucket, key = $key", t)
                        h. streamManager.abort()
                        h.some
                    }
                }
            }

              data
            }

          case CompletePutStream(bucket: Bucket, key: String) =>
            IO {
              putStreamHandles.getAndUpdate { hs =>
                hs.get(key).foreach(h => h.complete())
                hs - key
              }
            }.attempt.map {
              case Left(t) =>
                println(s"=====> LEFT")
                putStreamHandles.getAndUpdate { hs =>
                  hs.get(key).foreach(h => h.abort(t))
                  hs - key
                }

                ().asInstanceOf[A]

              case Right(m) =>
                println(s"=====> RIGHT")
                putStreamHandles.getAndUpdate { hs =>
                  hs.get(key).foreach(h => h.complete())
                  hs - key
                }

                ().asInstanceOf[A]
            }

            //>> IO.unit.map(_.asInstanceOf[A])

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