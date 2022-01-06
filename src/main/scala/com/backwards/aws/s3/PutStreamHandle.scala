package com.backwards.aws.s3

import alex.mojaki.s3upload.{MultiPartOutputStream, StreamManager}
import software.amazon.awssdk.services.s3.model.Bucket

final case class PutStreamHandle(s3Client: S3Client, bucket: Bucket, key: String) {
  lazy val streamManager: StreamManager =
    new StreamManager(bucket.name, key, s3Client.v1.sync)

  lazy val outputStream: MultiPartOutputStream =
    streamManager.getMultiPartOutputStreams.get(0)

  def write[A: Serialiser](data: A): Unit =
    try {
      scribe.info(s"PutStream write to S3 bucket = $bucket, key = $key")
      outputStream.write(Serialiser[A].serialise(data))
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting write of PutStream", t)
        outputStream.close()
        streamManager.abort(t)
    }

  def complete(): Unit =
    try {
      scribe.info(s"Completing PutStream")
      outputStream.close()
      streamManager.complete()
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting completion of PutStream", t)
        streamManager.abort()
    }

  def abort(t: Throwable): Unit =
    try {
      scribe.error(s"Aborting PutStream")
      outputStream.close()
      streamManager.abort(t)
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting PutStream", t)
        outputStream.close()
        streamManager.abort(t)
    }
}