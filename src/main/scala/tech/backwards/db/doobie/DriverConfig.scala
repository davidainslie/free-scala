package tech.backwards.db.doobie

import java.net.URI
import java.sql.Driver
import tech.backwards.auth.Credentials

final case class DriverConfig[_ <: Driver](uri: URI, credentials: Credentials)