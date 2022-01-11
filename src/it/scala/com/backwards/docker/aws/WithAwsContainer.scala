package com.backwards.docker.aws

import scala.util.Try
import scala.util.chaining.scalaUtilChainingOps
import cats.effect.Sync
import cats.implicits._
import software.amazon.awssdk.regions.Region
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers.LocalStackContainer
import com.backwards.aws.s3.{S3Client, awsCredentials}

/**
 * List objects e.g.
 *  - aws --endpoint-url=http://localhost:55981 s3 ls s3://my-bucket --recursive
 *
 * View object e.g.
 *  - aws --endpoint-url=http://127.0.0.1:54998 s3 cp s3://my-bucket/foo -
 */
trait WithAwsContainer {
  val container: LocalStackContainer =
    LocalStackContainer(services = List(Service.S3))

  container.start()

  def s3Client[F[_]: Sync]: F[S3Client] = {
    val shutdownHook: S3Client => Unit =
      s3Client => sys.addShutdownHook(
        (Try(s3Client.close()) *> Try(scribe.info(s"Stopping LocalStackContainer: ${container.containerId}")) *> Try(container.stop())).fold(throw _, identity)
      )

    awsCredentials[F]
      .map(S3Client(_, Region.of(container.container.getRegion), container.container.getEndpointOverride(Service.S3).some)
      .tap(shutdownHook)
    )
  }
}