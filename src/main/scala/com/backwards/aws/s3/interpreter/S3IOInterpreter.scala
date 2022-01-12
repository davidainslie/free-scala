package com.backwards.aws.s3.interpreter

import java.util.concurrent.CompletableFuture
import scala.util.chaining.scalaUtilChainingOps
import cats.data.EitherT
import cats.effect.{IO, Ref, Resource}
import cats.implicits._
import cats.~>
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.core.{ResponseBytes, ResponseInputStream}
import software.amazon.awssdk.http.AbortableInputStream
import software.amazon.awssdk.services.s3.model.{Bucket, BucketAlreadyOwnedByYouException, CreateBucketResponse, GetObjectResponse, PutObjectResponse}
import com.amazonaws.util.IOUtils
import com.backwards.aws.s3.S3._
import com.backwards.aws.s3.interpreter.S3IOInterpreter.PutStreamHandleKey
import com.backwards.aws.s3.{PutStreamHandle, S3, S3Client}

/**
 * Asynchronous S3 Algebra Interpreter
 * TODO - Regarding non-stream cases, there is a fs2 S3 library that could be spiked.
 * TODO - This interpreter carries "state".
 * It would be better to encapsulate state within Free and add to S3 Algebra an ADT that would delegate to some other Free.
 * e.g. PutStream(bucket, key, nextFree)
 */
class S3IOInterpreter private(s3Client: S3Client, putStreamHandles: Ref[IO, Map[PutStreamHandleKey, PutStreamHandle]]) extends (S3 ~> IO) {
  private def close: IO[Unit] =
    putStreamHandles.update(_.values.foreach(_.abort()).pipe(_ => Map.empty[PutStreamHandleKey, PutStreamHandle]))

  override def apply[A](fa: S3[A]): IO[A] =
    fa match {
      case CreateBucket(request, allowAlreadyExists) =>
        EitherT(IO.fromCompletableFuture(IO(s3Client.v2.async.createBucket(request))).attempt).foldF(
          {
            case _: BucketAlreadyOwnedByYouException if allowAlreadyExists => IO(CreateBucketResponse.builder.build.asInstanceOf[A])
            case t => IO.raiseError(t)
          },
          response => IO(response.asInstanceOf[A])
        )

      case PutObject(request, body) =>
        val bytes: Array[Byte] =
          IOUtils.toByteArray(body.contentStreamProvider().newStream())

        val putObjectResponseFuture: CompletableFuture[PutObjectResponse] =
          s3Client.v2.async.putObject(request, AsyncRequestBody.fromBytes(bytes))

        IO.fromCompletableFuture(IO(putObjectResponseFuture)).map(_.asInstanceOf[A])

      case PutStream(bucket, key, data, serialiser) =>
        putStreamHandles.update(hs =>
          hs.updatedWith(PutStreamHandleKey(bucket, key)) {
            case Some(h) =>
              h.tap(_.write(data)(serialiser)).pipe(_.some)

            case None =>
              PutStreamHandle(s3Client, bucket, key).tap(_.write(data)(serialiser)).pipe(_.some)
          }
        ).map(_.asInstanceOf[A])

      case CompletePutStream(bucket: Bucket, key: String) =>
        putStreamHandles.update { hs =>
          val putStreamHandleKey: PutStreamHandleKey =
            PutStreamHandleKey(bucket, key)

          hs.get(putStreamHandleKey).foreach(h => h.complete()).pipe(_ => hs - putStreamHandleKey)
        }.map(_.asInstanceOf[A])

      case GetObject(request, deserialiser) =>
        val asyncResponseTransformer: AsyncResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]] =
          AsyncResponseTransformer.toBytes()

        val getObjectResponseFuture: CompletableFuture[ResponseBytes[GetObjectResponse]] =
          s3Client.v2.async.getObject(request, asyncResponseTransformer)

        IO.fromCompletableFuture(IO(getObjectResponseFuture)).flatMap(responseBytes =>
          deserialiser.deserialise(
            new ResponseInputStream[GetObjectResponse](
              responseBytes.response(),
              AbortableInputStream.create(responseBytes.asInputStream())
            ).readAllBytes
          ).fold(IO.raiseError, IO.pure)
        )
    }
}

object S3IOInterpreter {
  final case class PutStreamHandleKey(bucket: Bucket, key: String)

  def resource(s3Client: S3Client): Resource[IO, S3 ~> IO] =
    Resource.make(Ref[IO].of(Map.empty[PutStreamHandleKey, PutStreamHandle]).map(new S3IOInterpreter(s3Client, _)))(_.close)
}