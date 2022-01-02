package com.backwards.io

import java.net.URI

object URIOps {
  object syntax {
    implicit class URIExtension(self: URI) {
      def addParam(keyValue: (String, Any)): URI = {
        val (key, value) = keyValue

        val uri: String =
          self.toString

        if (uri.contains("?")) {
          URI.create(s"$uri&$key=$value")
        } else {
          URI.create(s"$uri?$key=$value")
        }
      }
    }
  }
}
