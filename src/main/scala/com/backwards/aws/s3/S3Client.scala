package com.backwards.aws.s3

import java.net.URI
import scala.util.Try
import scala.util.chaining._
import cats.Monad
import cats.effect.{Resource, Sync}
import cats.implicits._
import eu.timepit.refined.auto._
import software.amazon.awssdk.regions.Region
import com.backwards.auth.Credentials
import com.backwards.fp.FunctionOps.syntax._

final case class S3Client(credentials: Credentials, region: Region, endpoint: Option[URI] = None) {
  def close(): Unit =
    (Try(scribe.info("Closing S3 Client")) *> Try(v1.sync.shutdown()) *> Try(v2.sync.close()) *> Try(v2.async.close())).fold(throw _, identity)

  object v1 {
    import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
    import com.amazonaws.client.builder.AwsClientBuilder
    import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
    import com.amazonaws.util.AwsHostNameUtils

    lazy val sync: AmazonS3 =
      AmazonS3ClientBuilder
        .standard
        .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(credentials.user.value, credentials.password.value)))
        .foldOptional(endpoint)(_.withRegion(region.id))(builder => uri =>
          builder
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(uri.toString, AwsHostNameUtils.parseRegion(region.id, null)))
            .tap(_ => scribe.info(s"aws --endpoint-url=$uri s3 ls --recursive"))
        )
        .enablePathStyleAccess
        .build
  }

  object v2 {
    import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}

    lazy val sync: software.amazon.awssdk.services.s3.S3Client =
      software.amazon.awssdk.services.s3.S3Client
        .builder
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.user.value, credentials.password.value)))
        .optional(endpoint)(builder => uri =>
          builder
            .endpointOverride(uri)
            .tap(_ => scribe.info(s"aws --endpoint-url=$uri s3 ls --recursive"))
        )
        .region(region)
        .build

    lazy val async: software.amazon.awssdk.services.s3.S3AsyncClient =
      software.amazon.awssdk.services.s3.S3AsyncClient
        .builder
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(credentials.user.value, credentials.password.value)))
        .optional(endpoint)(builder => uri =>
          builder
            .endpointOverride(uri)
            .tap(_ => scribe.info(s"aws --endpoint-url=$uri s3 ls --recursive"))
        )
        .region(region)
        .build
  }
}

object S3Client {
  def resource[F[_]: Sync](credentials: Credentials, region: Region, endpoint: Option[URI] = None): Resource[F, S3Client] =
    Resource.make(Sync[F].delay(new S3Client(credentials, region, endpoint)))(client => Sync[F].delay(client.close()))
}