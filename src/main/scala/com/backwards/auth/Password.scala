package com.backwards.auth

import eu.timepit.refined.types.string.NonEmptyString

final case class Password(value: NonEmptyString)