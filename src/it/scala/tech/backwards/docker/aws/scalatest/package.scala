package tech.backwards.docker.aws

package object scalatest {
  import scala.util.Try
  import cats.MonadError
  import cats.implicits._
  import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
  import software.amazon.awssdk.regions.Region
  import tech.backwards.aws.s3.S3Client
  import org.scalatest.Assertion
  import org.testcontainers.containers.localstack.LocalStackContainer.Service
  import com.dimafeng.testcontainers.LocalStackContainer

  /**
   * List objects e.g.
   *  - aws --endpoint-url=http://localhost:55981 s3 ls s3://my-bucket --recursive
   *
   * View object e.g.
   *  - aws --endpoint-url=http://127.0.0.1:54998 s3 cp s3://my-bucket/foo -
   */
  trait AwsContainer {
    private def s3Client(container: LocalStackContainer): S3Client =
      S3Client(
        AwsBasicCredentials.create(container.container.getAccessKey, container.container.getSecretKey),
        Region.of(container.container.getRegion),
        container.container.getEndpointOverride(Service.S3).some
      )

    def withS3[F[_]: MonadError[*[_], Throwable]](container: LocalStackContainer)(test: S3Client => F[Assertion]): F[Assertion] =
      MonadError[F, Throwable].pure(s3Client(container)).flatMap(s3Client =>
        test(s3Client).attempt.flatTap(_ => MonadError[F, Throwable].pure(Try(s3Client.close())))
      ).map(_.fold(throw _, identity))
  }
}