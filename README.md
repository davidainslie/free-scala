# Scala Free Monads

- [Setup](docs/setup.md)
- [Build](docs/build.md)
- [Release](docs/release.md)
- [Free](docs/free.md)

Free Monad Algebra providing convenient program DSLs covering:

- Http - Interact with any Http API with any underlying implementation (sttp provided as a recommended default).
- AWS S3 - Write and read to S3 including dynamic upload streaming providing a source to target pipeline.

## Example - Get paginated Http accumulating each page and then Put as one entire Object to S3

Take a look at the example code [CoproductIOInterpreterApp](src/it/scala/com/backwards/algebra/interpreter/CoproductIOInterpreterApp.scala) where the following program is run:

```scala
def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
  for {
    bucket    <- Bucket("my-bucket").liftFree[Algebras]
    _         <- CreateBucket(CreateBucketRequest(bucket))
    data      <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate
    _         <- PutObject(PutObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
    response  <- GetObject(GetObjectRequest(bucket, "foo"))
  } yield response
```

## Example - Get paginated Http streaming each page and Put as individual Object to S3

Take a look at the example code [CoproductIOStreamInterpreterApp](src/it/scala/com/backwards/algebra/interpreter/CoproductIOStreamInterpreterApp.scala) where the following program is run:

```scala
def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, ResponseInputStream[GetObjectResponse]] =
  for {
    bucket  <- Bucket("my-bucket").liftFree[Algebras]
    _         <- CreateBucket(CreateBucketRequest(bucket))
    handle    <- PutStream(bucket, "foo")
    // TODO - Program must not forget to call handle.complete() - Next code iteration will have some sort of Resource like Cats
    _         <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate(handle).as(handle.complete())
    response  <- GetObject(GetObjectRequest(bucket, "foo"))
  } yield response
```