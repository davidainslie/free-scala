package com.backwards.aws.s3

import alex.mojaki.s3upload.{MultiPartOutputStream, StreamTransferManager}
import cats.Show
import cats.implicits._
import software.amazon.awssdk.services.s3.model.Bucket
import com.backwards.serialisation.Serialiser

final case class PutStreamHandle(s3Client: S3Client, bucket: Bucket, key: String) {
  lazy val streamManager: StreamTransferManager =
    new StreamTransferManager(bucket.name, key, s3Client.v1.sync)

  lazy val outputStream: MultiPartOutputStream =
    streamManager.getMultiPartOutputStreams.get(0)

  def write[A: Serialiser](data: A): Unit =
    try {
      scribe.info(s"Writing PutStream: ${Show[PutStreamHandle].show(this)}")
      outputStream.write(Serialiser[A].serialise(data))
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting PutStream: ${Show[PutStreamHandle].show(this)}", t)
        outputStream.close()
        streamManager.abort(t)
    }

  def complete(): Unit =
    try {
      scribe.info(s"Completing PutStream: ${Show[PutStreamHandle].show(this)}")
      outputStream.close()
      streamManager.complete()
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting PutStream: ${Show[PutStreamHandle].show(this)}", t)
        streamManager.abort()
    }

  def abort(): Unit =
    try {
      scribe.error(s"Aborting PutStream: ${Show[PutStreamHandle].show(this)}")
      outputStream.close()
      streamManager.abort()
    } catch {
      case t: Throwable =>
        throw t
    }

  def abort(t: Throwable): Unit =
    try {
      scribe.error(s"Aborting PutStream: ${Show[PutStreamHandle].show(this)}")
      outputStream.close()
      streamManager.abort(t)
    } catch {
      case t: Throwable =>
        throw t
    }
}

object PutStreamHandle {
  implicit val showPutStreamHandle: Show[PutStreamHandle] =
    (h: PutStreamHandle) => s"Bucket = ${h.bucket.name}, Key = ${h.key}"
}