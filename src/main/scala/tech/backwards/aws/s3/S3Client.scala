package tech.backwards.aws.s3

import java.net.URI
import scala.util.Try
import scala.util.chaining._
import cats.effect.{Resource, Sync}
import cats.implicits._
import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsSessionCredentials}
import software.amazon.awssdk.regions.Region
import com.amazonaws.auth.BasicSessionCredentials
import tech.backwards.fp.FunctionOps.syntax._

final case class S3Client(credentials: AwsCredentials, region: Region, endpoint: Option[URI] = None) {
  def close(): Unit =
    (Try(scribe.info("Closing S3 Client")) *> Try(v1.sync.shutdown()) *> Try(v2.sync.close()) *> Try(v2.async.close())).fold(throw _, identity)

  object v1 {
    import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}
    import com.amazonaws.client.builder.AwsClientBuilder
    import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
    import com.amazonaws.util.AwsHostNameUtils

    lazy val sync: AmazonS3 =
      AmazonS3ClientBuilder.standard
        .withCredentials(
          new AWSStaticCredentialsProvider(
            credentials match {
              case sessionCredentials: AwsSessionCredentials =>
                new BasicSessionCredentials(sessionCredentials.accessKeyId, sessionCredentials.secretAccessKey, sessionCredentials.sessionToken)
              case _ =>
                new BasicAWSCredentials(credentials.accessKeyId, credentials.secretAccessKey)
            }
          )
        )
        .enablePathStyleAccess
        .foldOptional(endpoint)(_.withRegion(region.id))(builder => uri =>
          builder
            .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(uri.toString, AwsHostNameUtils.parseRegion(region.id, null)))
            .tap(_ => scribe.info(s"aws --endpoint-url=$uri s3 ls --recursive"))
        ).build
  }

  object v2 {
    import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
    import software.amazon.awssdk.services.s3.{S3AsyncClient, S3AsyncClientBuilder, S3BaseClientBuilder, S3ClientBuilder, S3Client => S3SyncClient}

    lazy val sync: S3SyncClient =
      withCredentials[S3SyncClient, S3ClientBuilder](S3SyncClient.builder).build

    lazy val async: S3AsyncClient =
      withCredentials[S3AsyncClient, S3AsyncClientBuilder](S3AsyncClient.builder).build

    def withCredentials[C, B <: S3BaseClientBuilder[B, C]]: B => B =
      _.credentialsProvider(StaticCredentialsProvider.create(credentials)).region(region).optional(endpoint)(withEndpoint[C, B])

    def withEndpoint[C, B <: S3BaseClientBuilder[B, C]]: B => URI => B =
      builder => uri => builder.endpointOverride(uri).tap(_ => scribe.info(s"aws --endpoint-url=$uri s3 ls --recursive"))
  }
}

object S3Client {
  def resource[F[_]: Sync](credentials: AwsCredentials, region: Region, endpoint: Option[URI] = None): Resource[F, S3Client] =
    Resource.make(Sync[F].delay(new S3Client(credentials, region, endpoint)))(client => Sync[F].delay(client.close()))
}