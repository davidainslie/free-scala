package tech.backwards.db.doobie

final case class Movie(id: String, title: String, year: Int, actors: List[String], director: String)