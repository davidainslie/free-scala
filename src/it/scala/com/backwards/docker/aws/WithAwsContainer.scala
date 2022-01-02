package com.backwards.docker.aws

import scala.util.Try
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import software.amazon.awssdk.regions.Region
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers.LocalStackContainer
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.aws.s3.S3Client

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

  sys.addShutdownHook(
    Try(s3.close()) >> Try(scribe.info(s"Stopping LocalStackContainer: ${container.containerId}")) >> Try(container.stop())
  )

  /*
  TODO - Maybe return Either e.g.
  val u: String Either NonEmptyString =
    NonEmptyString.from(container.container.getAccessKey)
  */
  lazy val s3: S3Client =
    S3Client(
      Credentials(User(NonEmptyString.unsafeFrom(container.container.getAccessKey)), Password(NonEmptyString.unsafeFrom(container.container.getSecretKey))),
      Region.of(container.container.getRegion),
      container.container.getEndpointOverride(Service.S3).some
    )
}