package com.backwards.aws.s3

import alex.mojaki.s3upload.{MultiPartOutputStream, StreamTransferManager}
import com.backwards.io.Serialiser

final case class PutStreamHandle(manager: StreamTransferManager, stream: MultiPartOutputStream) {
  def write[A: Serialiser](data: A): Unit =
    try {
      stream.write(Serialiser[A].serialise(data))
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting output stream write to S3", t)
        manager.abort()
    }

  def complete(): Unit =
    try {
      scribe.info(s"Completing Put Stream")
      stream.close()
      manager.complete()
    } catch {
      case t: Throwable =>
        scribe.error(s"Aborting completion of output stream to S3", t)
        manager.abort()
    }
}