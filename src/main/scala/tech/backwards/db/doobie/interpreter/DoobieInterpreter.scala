package tech.backwards.db.doobie.interpreter

import java.sql.Driver
import scala.reflect.{ClassTag, classTag}
import cats.effect.{Async, Resource}
import cats.free.Free
import cats.free.Free.liftInject
import cats.{InjectK, ~>}
import doobie.ConnectionIO
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.util.ExecutionContexts
import tech.backwards.db.doobie.DriverConfig

private class DoobieInterpreter[F[_]: Async] private(transactor: HikariTransactor[F]) extends (ConnectionIO ~> F) {
  def apply[A](fa: ConnectionIO[A]): F[A] =
    fa.transact(transactor)
}

object DoobieInterpreter {
  implicit def doobieIsFree[F[_]: InjectK[ConnectionIO, *[_]], A](fa: ConnectionIO[A]): Free[F, A] =
    liftInject[F](fa)

  def apply[F[_]: Async] =
    new Builder[F]

  class Builder[F[_]: Async] {
    def resource[D <: Driver: ClassTag](driverConfig: DriverConfig[D]): Resource[F, ConnectionIO ~> F] = {
      import driverConfig._

      // TODO - Extract ExecutionContexts.fixedThreadPool[F](32) as a default, allowing override
      ExecutionContexts.fixedThreadPool[F](32).flatMap(ce =>
        HikariTransactor.newHikariTransactor[F](classTag[D].runtimeClass.getName, uri.toString, credentials.user.value, credentials.password.value, ce)
          .map(new DoobieInterpreter(_))
      )
    }
  }
}