package com.backwards.net

import eu.timepit.refined.auto._
import eu.timepit.refined.util.string.uri
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.backwards.net.URIOps.syntax._

class URIOpsSpec extends AnyWordSpec with Matchers {
  "URI" should {
    "add query params" in {
      uri("http://backwards.com/api")
        .addParam("key1" -> "value1")
        .addParam("key2" -> "value2") mustEqual uri("http://backwards.com/api?key1=value1&key2=value2")
    }
  }
}