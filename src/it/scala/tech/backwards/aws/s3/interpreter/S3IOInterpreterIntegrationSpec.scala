package tech.backwards.aws.s3.interpreter

import cats.InjectK
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.free.Free
import software.amazon.awssdk.core.sync.RequestBody
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import com.dimafeng.testcontainers.{ForAllTestContainer, LocalStackContainer}
import tech.backwards.aws.s3.S3._
import tech.backwards.aws.s3._
import tech.backwards.docker.aws.scalatest.AwsContainer
import tech.backwards.fp.free.FreeOps.syntax._

/**
 * List objects:
 *  - aws --endpoint-url=http://localhost:55981 s3 ls s3://my-bucket --recursive
 *
 * View object:
 *  - aws --endpoint-url=http://127.0.0.1:54998 s3 cp s3://my-bucket/foo -
 */
class S3IOInterpreterIntegrationSpec extends AsyncWordSpec with AsyncIOSpec with Matchers with ForAllTestContainer with AwsContainer {
  override val container: LocalStackContainer =
    LocalStackContainer(services = List(Service.S3))

  "S3 Algebra" should {
    "be applied against an async interpreter" in withS3(container) { s3Client =>
      def program(implicit I: InjectK[S3, S3]): Free[S3, String] =
        for {
          bucket    <- bucket("my-bucket").liftFree[S3]
          _         <- CreateBucket(createBucketRequest(bucket))
          _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString("Blah blah"))
          response  <- GetObject[String](getObjectRequest(bucket, "foo"))
        } yield response

      S3IOInterpreter.resource(s3Client).use(program.foldMap(_)).map(response =>
        response mustEqual "Blah blah"
      )
    }
  }
}