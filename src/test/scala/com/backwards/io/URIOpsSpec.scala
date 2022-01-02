package com.backwards.io

import java.net.URI
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.backwards.io.URIOps.syntax._

class URIOpsSpec extends AnyWordSpec with Matchers {
  "URI" should {
    "add query params" in {
      val uri: URI =
        URI.create("http://backwards.com/api")

      uri.addParam("key1" -> "value1").addParam("key2" -> "value2") mustEqual URI.create("http://backwards.com/api?key1=value1&key2=value2")
    }
  }
}