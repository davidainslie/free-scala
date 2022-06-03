package tech.backwards.db.doobie

import doobie.postgres.{Instances, JavaTimeInstances, free, syntax}

/**
 * Use instead of the following imports as Intellij thinks these imports are not required:
 * {{{
 *   import doobie.postgres._
 *   import doobie.postgres.implicits._
 * }}}
 */
trait DoobieImplicits extends Instances
  with free.Instances
  with JavaTimeInstances
  with syntax.ToPostgresMonadErrorOps
  with syntax.ToFragmentOps
  with syntax.ToPostgresExplainOps