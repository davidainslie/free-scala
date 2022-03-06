package tech.backwards.db

final case class Movie(id: String, title: String, year: Int, actors: List[String], director: String)