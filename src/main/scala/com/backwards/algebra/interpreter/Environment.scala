package com.backwards.algebra.interpreter

import cats.effect.Sync
import eu.timepit.refined.types.string.NonEmptyString
import software.amazon.awssdk.auth.credentials.{AwsCredentials, ProfileCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.Bucket
import com.backwards.auth.{Credentials, Password, User}
import com.backwards.aws.s3.bucket

final case class Environment(credentials: Credentials, region: Region, bucket: Bucket)

object Environment {
  def apply[F[_]: Sync]: F[Environment] =
    Sync[F].delay {
      val awsCredentials: AwsCredentials =
        ProfileCredentialsProvider.create(sys.env("AWS_PROFILE")).resolveCredentials

      val credentials = Credentials(
        User(NonEmptyString.unsafeFrom(awsCredentials.accessKeyId)),
        Password(NonEmptyString.unsafeFrom(awsCredentials.secretAccessKey))
      )

      val region: Region =
        Region.of(sys.env("AWS_REGION"))

      Environment(credentials, region, bucket(sys.env("AWS_BUCKET")))
    }
}