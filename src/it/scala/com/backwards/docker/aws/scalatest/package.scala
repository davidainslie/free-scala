package com.backwards.docker.aws

package object scalatest {
  import scala.util.Try
  import cats.MonadError
  import cats.implicits._
  import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
  import software.amazon.awssdk.regions.Region
  import org.scalatest.Assertion
  import org.testcontainers.containers.localstack.LocalStackContainer.Service
  import com.dimafeng.testcontainers.LocalStackContainer
  import com.backwards.aws.s3.S3Client

  /**
   * List objects e.g.
   *  - aws --endpoint-url=http://localhost:55981 s3 ls s3://my-bucket --recursive
   *
   * View object e.g.
   *  - aws --endpoint-url=http://127.0.0.1:54998 s3 cp s3://my-bucket/foo -
   */
  trait AwsContainer { self =>
    private def s3Client(container: LocalStackContainer): S3Client =
      S3Client(
        AwsBasicCredentials.create(container.container.getAccessKey, container.container.getSecretKey),
        Region.of(container.container.getRegion),
        container.container.getEndpointOverride(Service.S3).some
      )

    def withS3(container: LocalStackContainer)(test: S3Client => Assertion): Assertion = {
      val s3Client: S3Client =
        self.s3Client(container)

      try test(s3Client) finally s3Client.close()
    }

    def withMonadS3[F[_]: MonadError[*[_], Throwable]](container: LocalStackContainer)(test: S3Client => F[Assertion]): F[Assertion] =
      for {
        s3Client <- MonadError[F, Throwable].pure(self.s3Client(container))
        result   <- test(s3Client).attempt
        _ = Try(s3Client.close())
      } yield
        result.fold(throw _, identity)
  }
}