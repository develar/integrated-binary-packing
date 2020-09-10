package org.jetbrains.integratedBinaryPacking

import org.assertj.core.presentation.Representation
import org.assertj.core.presentation.StandardRepresentation
import java.lang.StringBuilder
import kotlin.random.Random

val random = Random(42)

const val arraySeparator = "\n  "
val arrayPresentation = object : Representation {
  override fun toStringOf(o: Any?): String {
    return when (o) {
      is IntArray -> arrayToString(o)
      is LongArray -> arrayToString(o)
      else -> StandardRepresentation.STANDARD_REPRESENTATION.toStringOf(o)
    }
  }

  override fun unambiguousToStringOf(o: Any?) = toStringOf(o)
}

private fun arrayToString(array: IntArray): String {
  val builder = StringBuilder()
  builder.append("[\n  ")
  array.joinTo(builder, separator = ", $arraySeparator")
  builder.append("\n]")
  return builder.toString()
}

private fun arrayToString(array: LongArray): String {
  val builder = StringBuilder()
  builder.append("[\n  ")
  array.joinTo(builder, separator = ", $arraySeparator")
  builder.append("\n]")
  return builder.toString()
}