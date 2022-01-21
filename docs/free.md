# Free

Free programs (essentially a function) at the simplest level, would often be declared e.g.

```scala
import cats.free.Free
import tech.backwards.http.Http

type Result = String

val program: Free[Http, Result]
```

where (in this case) `Http` is your Algebra that is lifted into a Free Monad.
To peform a `lift` we could declare e.g.

```scala
import cats.free.Free
import cats.free.Free.liftF
import tech.backwards.http.Http

implicit def httpToFree[A](fa: Http[A]): Free[Http, A] =
  liftF(fa)
```

However, this only works for a single Algebra, and normally you will have more, so even for the simplest we won't do this.

Instead, our simplest Algebra (even before multiple Algebra) we would declare a program e.g.

```scala
import cats.free.Free
import cats.free.Free.liftInject
import cats.InjectK
import tech.backwards.http.Http

type Result = String

def program(implicit I: InjectK[Http, Http]): Free[Http, Result]
```

We kind of double up on the parametric type of `InjectK`, but for multiple Algebra, the first type will be replaced by our Algebra composition.

Note the above `program with Inject` could be declared as:

```scala
import cats.free.Free
import cats.free.Free.liftInject
import cats.InjectK
import tech.backwards.http.Http

type Result = String

def program[F[_]: InjectK[Http, *[_]]]: Free[F, Result]
```
but for this simplest case it is overkill, as we would have to pass in `Http` as our type to `program` which is somewhat redundant.

Along with the use of `InjectK` our `implicit free` lifting becomes:

```scala
import cats.free.Free
import cats.free.Free.liftInject
import cats.InjectK
import tech.backwards.http.Http

implicit def httpToFree[F[_]: InjectK[Http, *[_]], A](fa: Http[A]): Free[F, A] =
  liftInject[F](fa)
```

For an excellent blog on CoProduct and Inject see [The Free Monad with Multiple Algebras](https://underscore.io/blog/posts/2017/03/29/free-inject.html).