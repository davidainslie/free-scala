package com.backwards.http

import cats.implicits.catsSyntaxOptionId

final case class ContentType(value: Option[String]) extends AnyVal

object ContentType {
  // Some folks believe this should be "application/octet-stream"
  val empty: ContentType =
    ContentType(None)

  def apply(contentType: String): ContentType =
    ContentType(contentType.some)
}