# Scala Free Monads

- [Setup](docs/setup.md)
- [Build](docs/build.md)
- [Release](docs/release.md)
- [Deploy](docs/deploy.md)
- [Terraform](docs/terraform.md)
- [Free](docs/free.md)

Free Monad Algebra providing convenient program DSLs covering:

- Http - Interact with any Http API with any underlying implementation (sttp provided as a recommended default).
- AWS S3 - Write and read to S3 including dynamic upload streaming providing a source to target pipeline (thanks to [Alex Hall](https://github.com/alexmojaki/s3-stream-upload))

## Getting Started

```scala
libraryDependencies ++= {
  val freeScala =
    "tech.backwards" %% "free-scala" % "<version>"
    
  List(
    freeScala,
    freeScala % "test, it" classifier "tests",
    freeScala % "test, it" classifier "it" 
  )    
}
```

## Examples

The following two examples use more than one `Algebra`, specifically `Http` and `S3`, so we use the following type alias:

```scala
type Algebras[A] = EitherK[Http, S3, A]
```

Also, the first example shows the use of `Context Bound` to express the `Algebras` dependency, whereas the second uses `implicit parameters`.

## Get paginated Http accumulate each page and Put as one S3 Object

Take a look at the example code [AlgebrasIOInterpreterITApp](src/it/scala/tech/backwards/algebra/interpreter/AlgebrasIOInterpreterITApp.scala) where the following program (of multiple Algebra) is run:

```scala
type `Http~>Algebras`[_] = InjectK[Http, Algebras]

type `S3~>Algebras`[_] = InjectK[S3, Algebras]

def program[F: `Http~>Algebras`: `S3~>Algebras`]: Free[Algebras, Jsonl] =
  for {
    bucket    <- bucket("my-bucket").liftFree[Algebras]
    _         <- CreateBucket(createBucketRequest(bucket))
    data      <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate
    _         <- PutObject(putObjectRequest(bucket, "foo"), RequestBody.fromString(data.map(_.noSpaces).mkString("\n")))
    response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
  } yield response
```
given "paginate" is an extension method:
```scala
def paginate: Free[F, Vector[Json]] = {
  def accumulate(acc: Vector[Json], json: Json): Vector[Json] =
    (json \ "data").flatMap(_.asArray).fold(acc)(acc ++ _)

  def go(get: Get[Json], acc: Vector[Json], page: Int): Free[F, Vector[Json]] =
    for {
      content <- paramsL[Json].modify(_ + ("page" -> page))(get)
      pages   = (content \ "meta" \ "pagination" \ "pages").flatMap(_.as[Int].toOption).getOrElse(0)
      data    <- if (page < pages && page < maxPages) go(get, accumulate(acc, content), page + 1) else Free.pure[F, Vector[Json]](accumulate(acc, content))
    } yield data

  go(get, acc = Vector.empty, page = 1)
}
```

## Get paginated Http streaming each page to S3 completing as one Object

Take a look at the example code [AlgebrasIOStreamInterpreterITApp](src/it/scala/tech/backwards/algebra/interpreter/AlgebrasIOStreamInterpreterITApp.scala) where the following program (of multiple Algebra) is run:

```scala
def program(implicit H: InjectK[Http, Algebras], S: InjectK[S3, Algebras]): Free[Algebras, Jsonl] =
  for {
    bucket    <- bucket("my-bucket").liftFree[Algebras]
    _         <- CreateBucket(createBucketRequest(bucket))
    _         <- Get[Json](uri("https://gorest.co.in/public/v1/users")).paginate(bucket, "foo")
    response  <- GetObject[Jsonl](getObjectRequest(bucket, "foo"))
  } yield response
```
given "paginate" is an extension method:
```scala
def paginate(bucket: Bucket, key: String): Free[Algebras, Unit] = {
  def go(get: Get[Json], page: Int): Free[Algebras, Unit] = {
    for {
      json  <- paramsL[Json].modify(_ + ("page" -> page))(get)
      data  <- Jsonl((json \ "data").flatMap(_.asArray)).liftFree[Algebras]
      _     <- when(data.value.nonEmpty, PutStream(bucket, key, data), unit[Algebras])
      pages <- (json \ "meta" \ "pagination" \ "pages").flatMap(_.as[Int].toOption).getOrElse(0).liftFree[Algebras]
      _     <- if (page < pages && page < maxPages) go(get, page + 1) else unit[Algebras]
    } yield ()
  }

  go(get, page = 1).as(CompletePutStream(bucket, key))
}
```