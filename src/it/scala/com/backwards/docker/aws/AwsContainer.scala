package com.backwards.docker.aws

import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import software.amazon.awssdk.regions.Region
import org.scalatest.Assertion
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
trait AwsContainer {
  def withS3(container: LocalStackContainer)(test: S3Client => Assertion): Assertion = {
    val s3Client: S3Client =
      S3Client(
        Credentials(User(NonEmptyString.unsafeFrom(container.container.getAccessKey)), Password(NonEmptyString.unsafeFrom(container.container.getSecretKey))),
        Region.of(container.container.getRegion),
        container.container.getEndpointOverride(Service.S3).some
      )

    try test(s3Client) finally s3Client.close()
  }
}