package org.jetbrains.integratedBinaryPacking

import com.squareup.javapoet.MethodSpec
import io.netty.buffer.ByteBuf
import javax.lang.model.element.Modifier

internal fun compressVariableMethod(sizeBits: Int): MethodSpec {
  val isInt = sizeBits == Int.SIZE_BITS
  val methodSpec = MethodSpec.methodBuilder("compressVariable")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .addParameter(if (isInt) IntArray::class.java else LongArray::class.java, "in", Modifier.FINAL)
    .addParameter(Int::class.java, "startIndex", Modifier.FINAL)
    .addParameter(Int::class.java, "endIndex", Modifier.FINAL)
    .addParameter(ByteBuf::class.java, "buf", Modifier.FINAL)
    .returns(Void.TYPE)

  val type = if (isInt) "int" else "long"

  methodSpec.addStatement("$type initValue = 0")
  methodSpec.beginControlFlow("for (int index = startIndex; index < endIndex; index++)")

  methodSpec.addStatement("final $type value = (in[index] - initValue)")
  methodSpec.addStatement("initValue = in[index]")

  compressItem(isInt, methodSpec)
  methodSpec.endControlFlow()

  return methodSpec.build()
}

internal fun compressItemVariableMethod(sizeBits: Int): MethodSpec {
  val isInt = sizeBits == Int.SIZE_BITS
  val methodSpec = MethodSpec.methodBuilder("writeVar")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .addParameter(ByteBuf::class.java, "buf", Modifier.FINAL)
    .addParameter(if (isInt) Int::class.java else Long::class.java, "value", Modifier.FINAL)
    .returns(Void.TYPE)

  compressItem(isInt, methodSpec)

  return methodSpec.build()
}

internal fun decompressItemVariableMethod(sizeBits: Int): MethodSpec {
  val isInt = sizeBits == Int.SIZE_BITS
  val methodSpec = MethodSpec.methodBuilder("readVar")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .addParameter(ByteBuf::class.java, "buf", Modifier.FINAL)
    .returns(if (isInt) Int::class.java else Long::class.java)

  decompressItem(isInt = isInt, isStandalone = true, methodSpec = methodSpec)
  methodSpec.addStatement("return value")
  return methodSpec.build()
}

private fun compressItem(isInt: Boolean, methodSpec: MethodSpec.Builder) {
  val maxSize = if (isInt) 5 else 9
  for (i in 1..maxSize) {
    val line = "(value >>> ${i * 7} == 0)"
    when {
      i == 1 -> methodSpec.beginControlFlow("if $line")
      i < maxSize -> methodSpec.nextControlFlow("else if $line")
      else -> methodSpec.nextControlFlow("else")
    }

    if (i == 1) {
      methodSpec.addStatement("buf.writeByte((byte)value)")
    }
    else {
      methodSpec.addStatement("buf.writeByte((byte)((value & 127) | 128))")
      for (j in 1 until i) {
        methodSpec.addStatement("buf.writeByte((byte)(value >>> ${j * 7}${if (j == (i - 1)) "" else " | 128"}))")
      }
    }
  }
  methodSpec.endControlFlow()
}

internal fun decompressVariableMethod(sizeBits: Int): MethodSpec {
  val isInt = sizeBits == Int.SIZE_BITS
  val methodSpec = MethodSpec.methodBuilder("decompressVariable")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .addParameter(ByteBuf::class.java, "buf", Modifier.FINAL)
    .addParameter(if (isInt) IntArray::class.java else LongArray::class.java, "out", Modifier.FINAL)
    .addParameter(Int::class.java, "endIndex", Modifier.FINAL)
    .returns(Void.TYPE)

  val type = if (isInt) "int" else "long"

  methodSpec.addStatement("$type initValue = 0")
  methodSpec.addStatement("$type value")
  methodSpec.beginControlFlow("for (int index = 0; index < endIndex; out[index++] = (initValue += value))")

  decompressItem(isInt = isInt, isStandalone = false, methodSpec = methodSpec)
  methodSpec.endControlFlow()

  return methodSpec.build()
}

private fun decompressItem(isInt: Boolean, isStandalone: Boolean, methodSpec: MethodSpec.Builder) {
  methodSpec.addStatement("byte aByte = buf.readByte()")
  methodSpec.addStatement("${if (isStandalone) (if (isInt) "int " else "long ") else ""}value = aByte & 127")
  val n = if (isInt) 4 else 8
  for (i in 1..n) {
    methodSpec.beginControlFlow("if ((aByte & 128) != 0)")
    if (i == n) {
      methodSpec.addStatement("value |= ${if (!isInt && i >= 4) "(long)" else ""}buf.readByte() << ${i * 7}")
    }
    else {
      methodSpec.addStatement("aByte = buf.readByte()")
      methodSpec.addStatement("value |= ${if (!isInt && i >= 4) "(long)" else ""}(aByte & 127) << ${i * 7}")
    }
  }
  for (i in 1..n) {
    methodSpec.endControlFlow()
  }
}
