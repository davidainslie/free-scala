package tech.backwards.db.doobie

import java.util.UUID
import scala.util.chaining.scalaUtilChainingOps
import cats.data.{NonEmptyList, OptionT}
import cats.effect._
import cats.implicits._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import io.estatico.newtype.macros.newtype
import tech.backwards.docker.db.WithPostgresContainer

/**
 * See https://blog.rockthejvm.com/doobie/
 *
 * Doobie uses an instance of the type class Get[A] to map the results of a query into Scala.
 *
 * The Put[A] type class describes creating a non-nullable database value from the Scala type A.
 *
 * The other type classes are Read[A] and Write[A].
 *
 * Doobie defines the instances of the above type classes for the following types:
 *
 *  - JVM numeric types Byte, Short, Int, Long, Float, and Double
 *  - BigDecimal (both Java and Scala versions)
 *  - Boolean, String, and Array[Byte]
 *  - Date, Time, and Timestamp from the java.sql package
 *  - Date from the java.util package
 *  - Instant, LocalDate, LocalTime, LocalDateTime, OffsetTime, OffsetDateTime and ZonedDateTime from the java.time package
 *  - Single-element case classes wrapping one of the above types
 */
sealed trait DoobieDemo {
  /*
  Example of the NOT recommended approach to getting a database connection.
  The JDBC driver manager will try to load the driver for each connection, which can be pretty expensive.

  val xa: Transactor[IO] =
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql:myimdb",
      "docker",  // username
      "docker"   // password
    )

  Since JDBC provides only a blocking interface to interact with SQL databases, we should be careful to also use the blocking facilities available in Cats Effect.
  Doobie takes care of using the Blocking context for us (where "ev" is an instance of Async[IO]):

  val acquire =
    ev.blocking {
      Class.forName(driver)
      conn()
    }
  */

  // Instead, we will use a Transactor that is backed by a connection pool. Doobie integrates well with the HikariCP connection pool library:
  val postgresResource: Resource[IO, HikariTransactor[IO]] =
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](32)
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        "jdbc:postgresql:myimdb",
        "docker",
        "docker",
        ce
      )
    } yield xa
}

// TODO - For this first demo app, run docker-compose.yml (under src/it/resources/db) - All other demos handle Docker automatically.
object DoobieDemoApp1 extends DoobieDemo with IOApp.Simple {
  /**
   * The sql interpolator allows us to create SQL statement fragments.
   * The method query lets us create a type that maps the single-row result of the query in a Scala type.
   *
   * ConnectionIO[A] is a Free Monad.
   * ConnectionIO[A] represents a computation that, given a Connection, will generate a value of type IO[A].
   *
   * Every free monad is only a description of a program. It’s not executable at all since it requires an interpreter.
   *
   * type ConnectionIO[A] = Free[ConnectionOp, A]
   *
   * The interpreter, in this case, is the Transactor we created.
   * Its role is to compile the program into a Kleisli[IO, Connection, A].
   * Where this Kleisli is just a representation of the function Connection => IO[A].
   */
  val findAllActorNames: ConnectionIO[List[String]] = {
    val findAllActorsQuery: Query0[String] =
      sql"select name from actors".query[String]

    val findAllActors: ConnectionIO[List[String]] =
      findAllActorsQuery.to[List]

    findAllActors
  }

  def run: IO[Unit] =
    postgresResource.use(findAllActorNames.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp2 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * If we know for sure that the query will return exactly one row, we can use the unique method.
   * However, if the query doesn’t return any row, we will get an exception.
   * So, we can safely use the option method and let the program return an Option[Actor], as shown in the next App
   */
  val findActorById: Int => ConnectionIO[Actor] =
    id => sql"select id, name from actors where id = $id".query[Actor].unique

  def run: IO[Unit] =
    postgresResource.use(findActorById(1).transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp3 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * To avoid exceptions with "unique", we can safely use the option method and let the program return an Option[Actor].
   */
  val findActorById: Int => ConnectionIO[Option[Actor]] =
    id => sql"select id, name from actors where id = $id".query[Actor].option

  def run: IO[Unit] =
    postgresResource.use(findActorById(1).transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp4 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Although extracting actors in a List[String] seems legit at first sight, it’s not safe in a real-world scenario. In fact, the number of extracted rows could be too much for the memory allocated to the application.
   */
  val actorNames: fs2.Stream[ConnectionIO, String] =
    sql"select name from actors".query[String].stream

  def run: IO[Unit] =
    postgresResource.use(actorNames.compile.toList.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp5 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Although extracting actors in a List[String] seems legit at first sight, it’s not safe in a real-world scenario. In fact, the number of extracted rows could be too much for the memory allocated to the application.
   */
  val actorNames: fs2.Stream[ConnectionIO, String] =
    sql"select name from actors".query[String].stream

  val findAllActorIdsAndNames: ConnectionIO[List[(Int, String)]] = {
    val findAllActorIdsAndNamesQuery: Query0[(Int, String)] =
      sql"select id, name from actors".query[(Int, String)]

    val findAllActorIdsAndNames: ConnectionIO[List[(Int, String)]] =
      findAllActorIdsAndNamesQuery.to[List]

    findAllActorIdsAndNames
  }

  def run: IO[Unit] =
    postgresResource.use(findAllActorIdsAndNames.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp6 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  val findAllActors: fs2.Stream[ConnectionIO, Actor] =
    sql"select id, name from actors".query[Actor].stream

  def run: IO[Unit] =
    postgresResource.use(findAllActors.compile.toList.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp7 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  val findActorsByNameInitialLetter: Char => fs2.Stream[ConnectionIO, Actor] =
    _.toString.pipe(initialLetter => sql"select id, name from actors where LEFT(name, 1) = $initialLetter".query[Actor].stream)

  def run: IO[Unit] =
    postgresResource.use(findActorsByNameInitialLetter('H').compile.toList.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp8 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * The `sql` interpolator is an alias of the more general `fr` interpolator, whose name stands for Fragment.
   * A fragment is a piece of an SQL statement that we can combine with any other fragment to build a proper SQL instruction.
   * This becomes useful when building up dynamic statements (to run).
   */
  val findActorsByNameInitialLetter: Char => fs2.Stream[ConnectionIO, Actor] =
    initialLetter => {
      val select: Fragment =
        fr"select id, name"

      val from: Fragment =
        fr"from actors"

      val where: Fragment =
        fr"where LEFT(name, 1) = ${initialLetter.toString}"

      val statement: Fragment =
        select |+| from |+| where

      statement.query[Actor].stream
    }

  def run: IO[Unit] =
    postgresResource.use(findActorsByNameInitialLetter('H').compile.toList.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp9 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * As a follow on, Fragments provides useful functions e.g. using "in" with "where"
   */
  val findActorsByName: NonEmptyList[String] => fs2.Stream[ConnectionIO, Actor] =
    actorNames => {
      val statement: Fragment =
        fr"select id, name from actors where " ++ Fragments.in(fr"name", actorNames)

      statement.query[Actor].stream
    }

  def run: IO[Unit] =
    postgresResource.use(findActorsByName(NonEmptyList.of("Henry Cavill", "Gal Godot")).compile.toList.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp10 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Moving onto mutation such as "insert"
   */
  val saveNewActor: String => ConnectionIO[Int] =
    name => {
      val update: Update0 =
        sql"insert into actors (name) values ($name)".update // Think of "update" as the opposite of query i.e. Update0 as opposed to Query0

      update.run // Call one available method to get a ConnectionIO from Update0
      // Calling the "run" method returns the number of updated rows inside the ConnectionIO
    }

  def run: IO[Unit] =
    postgresResource.use(saveNewActor("Michael Keaton").transact[IO]).map(pprint.pprintln(_))
}

/**
 * Now using test containers
 */
object DoobieDemoApp11 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Moving onto mutation such as "insert"
   */
  val saveNewActor: String => ConnectionIO[Int] =
    name => {
      val update: Update0 =
        sql"insert into actors (name) values ($name)".update // Think of "update" as the opposite of query i.e. Update0 as opposed to Query0

      update.run // Call one available method to get a ConnectionIO from Update0
      // Calling the "run" method returns the number of updated rows inside the ConnectionIO
    }

  def run: IO[Unit] =
    postgresResource.use(saveNewActor("Michael Keaton").transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp12 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Moving onto mutation such as "insert"
   */
  val saveNewActorAndGetId: String => ConnectionIO[Int] =
    name => {
      val update: Update0 =
        sql"insert into actors (name) values ($name)".update // Think of "update" as the opposite of query i.e. Update0 as opposed to Query0

      update.withUniqueGeneratedKeys[Int]("id")
    }

  def run: IO[Unit] =
    postgresResource.use(saveNewActorAndGetId("Michael Keaton").transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp13 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * We use the fact that the type ConnectionIO[A] is a monad, which means we can chain operations on it through a sequence of calls to flatMap and map methods.
   */
  val saveNewActorAndGetBack: String => ConnectionIO[Actor] =
    name => for {
      id <- sql"insert into actors (name) values ($name)".update.withUniqueGeneratedKeys[Int]("id")
      actor <- sql"select * from actors where id = $id".query[Actor].unique
    } yield actor

  def run: IO[Unit] =
    postgresResource.use(saveNewActorAndGetBack("Michael Keaton").transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp14 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Flatmap version of above for comprehension
   */
  val saveNewActorAndGetBack: String => ConnectionIO[Actor] =
    name =>
      sql"insert into actors (name) values ($name)".update.withUniqueGeneratedKeys[Int]("id") >>=
        (id => sql"select * from actors where id = $id".query[Actor].unique)

  def run: IO[Unit] =
    postgresResource.use(saveNewActorAndGetBack("Michael Keaton").transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp15 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Batch insertion, which will return the number of rows inserted
   */
  val saveNewActors: NonEmptyList[String] => ConnectionIO[Int] =
    names => Update[String]("insert into actors (name) values (?)").updateMany(names.toList)

  def run: IO[Unit] =
    postgresResource.use(saveNewActors(NonEmptyList.of("Michael Keaton", "Lesley-Anne Down")).transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp16 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Batch insertion, which will return the Actors inserted
   */
  val saveNewActors: NonEmptyList[String] => ConnectionIO[List[Actor]] =
    names => {
      val stream: fs2.Stream[ConnectionIO, Actor] =
        Update[String]("insert into actors (name) values (?)").updateManyWithGeneratedKeys[Actor]("id", "name")(names.toList)

      stream.compile.toList
    }

  def run: IO[Unit] =
    postgresResource.use(saveNewActors(NonEmptyList.of("Michael Keaton", "Lesley-Anne Down")).transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp17 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Update
   */
  val updateYearOfJusticeLeague: ConnectionIO[Int] = {
    val justiceLeagueId: String =
      "5e5a39bb-a497-4432-93e8-7322f16ac0b2"

    val correctYear: Int =
      2021

    sql"update movies set year_of_production = $correctYear where id = cast($justiceLeagueId as uuid)".update.run
  }

  def run: IO[Unit] =
    postgresResource.use(updateYearOfJusticeLeague.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp18 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple with DoobieImplicits {
  /**
   * Warning - We mixin DoobieImplicits to avoid Intellij thinking that imports are not used.
   *
   * Update using Doobie extensions (specifically for UUID)
   */
  val updateYearOfJusticeLeague: ConnectionIO[Int] = {
    val justiceLeagueId: UUID =
      UUID.fromString("5e5a39bb-a497-4432-93e8-7322f16ac0b2")

    val correctYear: Int =
      2021

    sql"update movies set year_of_production = $correctYear where id = $justiceLeagueId".update.run
  }

  def run: IO[Unit] =
    postgresResource.use(updateYearOfJusticeLeague.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp19 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Type class instances for our own types
   */
  @newtype final case class ActorName(value: String)

  object ActorName {
    implicit val getActorName: Get[ActorName] =
      Get[String].map(ActorName(_))

    implicit val putActorName: Put[ActorName] =
      Put[String].contramap(_.value) // i.e. Put[String].contramap(actorName => actorName.value)
  }

  val findAllActorNames: ConnectionIO[List[ActorName]] =
    sql"select name from actors".query[ActorName].to[List]

  def run: IO[Unit] =
    postgresResource.use(findAllActorNames.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp20 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Type class instances for our own types - Using "deriving" function available in newtype library
   */
  @newtype final case class ActorName(value: String)

  object ActorName {
    implicit val getActorName: Get[ActorName] =
      deriving

    implicit val putActorName: Put[ActorName] =
      deriving
  }

  val findAllActorNames: ConnectionIO[List[ActorName]] =
    sql"select name from actors".query[ActorName].to[List]

  def run: IO[Unit] =
    postgresResource.use(findAllActorNames.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp21 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Type class instances for our own types - Derive type class instances at the same time for Get and Put
   */
  @newtype final case class ActorName(value: String)

  object ActorName {
    implicit val metaActorName: Meta[ActorName] =
      Meta[String].imap(ActorName(_))(_.value) // Recommended to use "timap" instead of "imap"
  }

  val findAllActorNames: ConnectionIO[List[ActorName]] =
    sql"select name from actors".query[ActorName].to[List]

  def run: IO[Unit] =
    postgresResource.use(findAllActorNames.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp22 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Type class instances for our own types - Derive type class instances at the same time for Get and Put via "deriving"
   */
  @newtype final case class ActorName(value: String)

  object ActorName {
    implicit val metaActorName: Meta[ActorName] =
      deriving
  }

  val findAllActorNames: ConnectionIO[List[ActorName]] =
    sql"select name from actors".query[ActorName].to[List]

  def run: IO[Unit] =
    postgresResource.use(findAllActorNames.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp23 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * Doobie uses the Get and Put type classes only to manage single-column schema types.
   * In general, we need to map more than one column directly into a Scala class or into a tuple.
   * For this reason, Doobie defines two more type classes, Read[A] and Write[A], which can handle heterogeneous collections of columns.
   */
  @newtype final case class DirectorId(id: Int)

  @newtype final case class DirectorName(name: String)

  @newtype final case class DirectorLastName(lastName: String)

  final case class Director(id: DirectorId, name: DirectorName, lastName: DirectorLastName)

  object Director {
    implicit val readDirector: Read[Director] =
      Read[(Int, String, String)].map { case (id, name, lastname) =>
        Director(DirectorId(id), DirectorName(name), DirectorLastName(lastname))
      }

    implicit val writeDirector: Write[Director] =
      Write[(Int, String, String)].contramap(director =>
        (director.id.id, director.name.name, director.lastName.lastName)
      )
  }

  val findAllDirectors: fs2.Stream[ConnectionIO, Director] =
    sql"select id, name, last_name from directors".query[Director].stream

  def run: IO[Unit] =
    postgresResource.use(findAllDirectors.compile.toList.transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp24 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple with DoobieImplicits {
  /**
   * Warning - We mixin DoobieImplicits to avoid Intellij thinking that imports are not used.
   *
   * Joins - Find a movie by its name, also retrieving the director’s information and the list of actors that played in the film.
   *
   * The "array_agg" Postgres function creates an array from actor names.
   * The array type is not SQL standard - to let Doobie map the array type to a Scala List, we need to import the doobie.postgres._ and doobie.postgres.implicits._
   */
  val findMovieByName: String => ConnectionIO[Option[Movie]] =
    movieName =>
      sql"""
        |SELECT
        |  m.id,
        |  m.title,
        |  m.year_of_production,
        |  array_agg(a.name) as actors,
        |  d.name
        |FROM movies m
        |JOIN movies_actors ma ON m.id = ma.movie_id
        |JOIN actors a ON ma.actor_id = a.id
        |JOIN directors d ON m.director_id = d.id
        |WHERE m.title = $movieName
        |GROUP BY (
        |  m.id,
        |  m.title,
        |  m.year_of_production,
        |  d.name,
        |  d.last_name
        |)
      |""".stripMargin
      .query[Movie]
      .option

  def run: IO[Unit] =
    postgresResource.use(findMovieByName("Zack Snyder's Justice League").transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp25 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple with DoobieImplicits {
  /**
   * Joins - Find a movie by its name, also retrieving the director’s information and the list of actors that played in the film.
   *
   * For standard SQL we can't rely on the Postgres "array_agg" function with Doobie implicits.
   * Instead we perform the join manually, which means splitting the original query into three different queries and joining the data programmatically.
   * The three queries are executed in a single database transaction.
   *
   * Warning - Even though we no longer use a specific Postgres function, we still need the relevant Postgres Doobie implicits for type class instances of Read[A]
   */
  val findMovieByName: String => ConnectionIO[Option[Movie]] =
    movieName => {
      val findMovieByTitle: ConnectionIO[Option[(UUID, String, Int, Int)]] =
        sql"""
        | select id, title, year_of_production, director_id
        | from movies
        | where title = $movieName
        |""".stripMargin
        .query[(UUID, String, Int, Int)].option

      val findDirectorById: Int => ConnectionIO[List[(String, String)]] =
        directorId => sql"select name, last_name from directors where id = $directorId".query[(String, String)].to[List]

      val findActorsByMovieId: UUID => ConnectionIO[List[String]] =
        movieId =>
          sql"""
          | select a.name
          | from actors a
          | join movies_actors ma on a.id = ma.actor_id
          | where ma.movie_id = $movieId
          |""".stripMargin
          .query[String]
          .to[List]

      val query: ConnectionIO[Option[Movie]] =
        for {
          maybeMovie <- findMovieByTitle
          directors <- maybeMovie match {
            case Some((_, _, _, directorId)) => findDirectorById(directorId)
            case None => List.empty[(String, String)].pure[ConnectionIO]
          }
          actors <- maybeMovie match {
            case Some((movieId, _, _, _)) => findActorsByMovieId(movieId)
            case None => List.empty[String].pure[ConnectionIO]
          }
        } yield
          maybeMovie.map { case (id, title, year, _) =>
            val directorName = directors.head._1
            val directorLastName = directors.head._2
            Movie(id.toString, title, year, actors, s"$directorName $directorLastName")
          }

      query
    }

  def run: IO[Unit] =
    postgresResource.use(findMovieByName("Zack Snyder's Justice League").transact[IO]).map(pprint.pprintln(_))
}

object DoobieDemoApp26 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple with DoobieImplicits {
  /**
   * Joins with the help of OptionT - Find a movie by its name, also retrieving the director’s information and the list of actors that played in the film.
   *
   * For standard SQL we can't rely on the Postgres "array_agg" function with Doobie implicits.
   * Instead we perform the join manually, which means splitting the original query into three different queries and joining the data programmatically.
   * The three queries are executed in a single database transaction.
   *
   * Warning - Even though we no longer use a specific Postgres function, we still need the relevant Postgres Doobie implicits for type class instances of Read[A]
   */
  val findMovieByName: String => ConnectionIO[Option[Movie]] =
    movieName => {
      val findMovieByTitle: ConnectionIO[Option[(UUID, String, Int, Int)]] =
        sql"""
        | select id, title, year_of_production, director_id
        | from movies
        | where title = $movieName
        |""".stripMargin
        .query[(UUID, String, Int, Int)].option

      val findDirectorById: Int => ConnectionIO[Option[Director]] =
        directorId => sql"select id, name, last_name from directors where id = $directorId".query[Director].option

      val findActorsByMovieId: UUID => ConnectionIO[List[String]] =
        movieId =>
          sql"""
          | select a.name
          | from actors a
          | join movies_actors ma on a.id = ma.actor_id
          | where ma.movie_id = $movieId
          |""".stripMargin
          .query[String]
          .to[List]

      (for {
        (movieId: UUID, title: String, year: Int, directorId: Int) <- OptionT(findMovieByTitle)
        director <- OptionT(findDirectorById(directorId))
        actors <- OptionT liftF findActorsByMovieId(movieId)
      } yield
        Movie(movieId.toString, title, year, actors, s"${director.name} ${director.lastName}")
      ).value
    }

  def run: IO[Unit] =
    postgresResource.use(findMovieByName("Zack Snyder's Justice League").transact[IO]).map(pprint.pprintln(_))
}

/**
 * Full Demo
 */
object DoobieDemoApp27 extends WithPostgresContainer("db/sql/schema-test-container.sql".some) with IOApp.Simple {
  /**
   * In a tagless final approach, we first define an algebra as a trait, storing all the functions we implement for a type.
   */
  trait Directors[F[_]] {
    def findById(id: Int): F[Option[Director]]

    def findAll: F[List[Director]]

    def create(name: String, lastName: String): F[Int]
  }

  /**
   * We need an interpreter of the algebra, that is a concrete implementation of the functions defined in the algebra.
   */
  object Directors extends DoobieImplicits {
    def make[F[_]: MonadCancelThrow](postgresResource: Resource[F, Transactor[F]]): Directors[F] =
      new Directors[F] {
        def findById(id: Int): F[Option[Director]] =
          postgresResource.use(sql"SELECT id, name, last_name FROM directors WHERE id = $id".query[Director].option.transact[F])

        def findAll: F[List[Director]] =
          postgresResource.use(sql"SELECT id, name, last_name FROM directors".query[Director].to[List].transact[F])

        def create(name: String, lastName: String): F[Int] =
          postgresResource.use(sql"INSERT INTO directors (name, last_name) VALUES ($name, $lastName)".update.withUniqueGeneratedKeys[Int]("id").transact[F])
      }
  }

  def run: IO[Unit] =
    for {
      directors <- IO(Directors.make(postgresResource))
      id <- directors.create("Steven", "Spielberg")
      spielberg <- directors.findById(id)
      _ <- IO.println(s"The director of Jaws is: $spielberg")
    } yield ()
}