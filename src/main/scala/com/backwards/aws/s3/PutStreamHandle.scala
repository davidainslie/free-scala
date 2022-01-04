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
      outputStream.write(Serialiser[A].serialise(data))
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting output stream write to S3", t)
        streamManager.abort()
    }

  def complete(): Unit =
    try {
      scribe.info(s"Completing Put Stream")
      outputStream.close()
      streamManager.complete()
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting completion of output stream to S3", t)
        streamManager.abort()
    }
}