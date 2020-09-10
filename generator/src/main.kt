package org.jetbrains.integratedBinaryPacking

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import com.squareup.javapoet.MethodSpec
import java.lang.StringBuilder
import java.nio.file.Paths
import java.util.*

fun main() {
  packer("IntBitPacker", Int.SIZE_BITS)
  packer("LongBitPacker", Long.SIZE_BITS)
}

private fun packer(name: String, sizeBits: Int) {
  val classSpec = TypeSpec.classBuilder(name)
    .addModifiers(Modifier.PUBLIC, Modifier.FINAL)

  classSpec.addMethod(compressMethod(sizeBits))
  classSpec.addMethod(decompressMethod(sizeBits))

  classSpec.addMethod(compressItemVariableMethod(sizeBits))
  classSpec.addMethod(decompressItemVariableMethod(sizeBits))

  classSpec.addMethod(compressVariableMethod(sizeBits))
  classSpec.addMethod(decompressVariableMethod(sizeBits))

  val isInt = sizeBits == Int.SIZE_BITS
  val blockSize = if (isInt) 32 else 64
  classSpec.addMethod(
    MethodSpec.methodBuilder("maxDiffBits")
      .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
      .addParameter(if (isInt) Int::class.java else Long::class.java, "initValue")
      .addParameter(if (isInt) IntArray::class.java else LongArray::class.java, "in")
      .addParameter(Int::class.java, "position")
      .returns(Int::class.java)
      .addCode(
        """
        ${if (isInt) "int" else "long"} mask = in[position] - initValue;
        for (int i = position + 1; i < position + $blockSize; ++i) {
          mask |= in[i] - in[i - 1];
        }
        return $sizeBits - ${if (isInt) "Integer" else "Long"}.numberOfLeadingZeros(mask);
      """.trimIndent()
      )
      .build()
  )

  classSpec.addMethod(packMethod(isUnpack = false, sizeBits))
  classSpec.addMethod(packMethod(isUnpack = true, sizeBits))

  for (i in 1 until sizeBits) {
    classSpec.addMethod(addPack(i, sizeBits))
    classSpec.addMethod(addUnpack(i, sizeBits))
  }
  //for (i in 1 until sizeBits) {
  //  classSpec.addMethod(addUnpack(i, sizeBits))
  //}

  val javaFile = JavaFile.builder("org.jetbrains.integratedBinaryPacking", classSpec.build())
    .addFileComment("""
      Copyright Daniel Lemire, http://lemire.me/en/ Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
    """.trimIndent())
    .build()
  javaFile.writeTo(Paths.get("lib/src"))
}

private fun packMethod(isUnpack: Boolean, sizeBits: Int): MethodSpec {
  val methodSpec = MethodSpec.methodBuilder(if (isUnpack) "unpack" else "pack")
    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
  addPackParams(methodSpec, sizeBits == Int.SIZE_BITS)
  methodSpec
    .addParameter(if (sizeBits == Int.SIZE_BITS) Int::class.java else Long::class.java, "bitCount", Modifier.FINAL)
    .addJavadoc(
      """
       ${if (isUnpack) "Unpack" else "Pack"} 32 $sizeBits-bit integers as deltas with an initial value.
       
       @param initValue initial value (used to compute first delta)
       @param in         input array
       @param inPos      initial position in input array
       @param out        output array
       @param outPos     initial position in output array
       @param bitCount        number of bits to use per integer
      """.trimIndent()
    )
    .returns(Void.TYPE)

  methodSpec.beginControlFlow("switch ((byte)bitCount)")
  methodSpec.beginControlFlow("case 0:")
  if (isUnpack) {
    val blockSize = if (sizeBits == Int.SIZE_BITS) 32 else 64
    methodSpec.addStatement("\$T.fill(out, outPos, outPos + $blockSize, initValue)", Arrays::class.java)
  }
  methodSpec.addStatement("break")
  methodSpec.endControlFlow()

  for (i in 1 until sizeBits) {
    methodSpec.beginControlFlow("case $i:")
    methodSpec.addStatement("${if (isUnpack) "unpack" else "pack"}$i(initValue, in, inPos, out, outPos)")
    methodSpec.addStatement("break")
    methodSpec.endControlFlow()
  }
  methodSpec.beginControlFlow("case $sizeBits:")
  methodSpec.addStatement("System.arraycopy(in, inPos, out, outPos, $sizeBits)")
  methodSpec.addStatement("break")
  methodSpec.endControlFlow()

  methodSpec.beginControlFlow("default:")
  methodSpec.addStatement("throw new IllegalArgumentException(\"Unsupported bit width: \" + bitCount)")
  methodSpec.endControlFlow()

  methodSpec.endControlFlow()
  return methodSpec.build()
}

private fun compressMethod(sizeBits: Int): MethodSpec {
  val isInt = sizeBits == Int.SIZE_BITS
  val arrayType = if (isInt) IntArray::class.java else LongArray::class.java
  val methodSpec = MethodSpec.methodBuilder("compressIntegrated")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .addParameter(arrayType, "in", Modifier.FINAL)
    .addParameter(Int::class.java, "startIndex")
    .addParameter(Int::class.java, "endIndex", Modifier.FINAL)
    .addParameter(arrayType, "out", Modifier.FINAL)
    .addParameter(if (isInt) Int::class.java else Long::class.java, "initValue")
    .returns(Int::class.java)

  val type = if (isInt) "int" else "long"

  methodSpec.addStatement("int tmpOutPos = 0")
  val blockCount = if (isInt) 4 else 8
  val blockSize = if (sizeBits == Int.SIZE_BITS) 32 else 64
  methodSpec.beginControlFlow("for (; startIndex + ${blockSize * (blockCount - 1)} < endIndex; startIndex += ${blockSize * blockCount})")
  methodSpec.addStatement("final $type mBits1 = maxDiffBits(initValue, in, startIndex)")
  for (i in 2..blockCount) {
    methodSpec.addStatement("final $type initOffset$i = in[startIndex + ${(blockSize - 1) + (blockSize * (i - 2))}]")
    methodSpec.addStatement("final $type mBits$i = maxDiffBits(initOffset$i, in, startIndex + ${blockSize * (i - 1)})")
  }

  val bitCount = StringBuilder("out[tmpOutPos++] = ")
  for (i in 1 until blockCount) {
    bitCount.append("mBits${i} << ${(blockCount - i) * 8} | ")
  }
  bitCount.append("mBits${blockCount}")
  methodSpec.addStatement(bitCount.toString())

  for (i in 1..blockCount) {
    methodSpec.addStatement("pack(${if (i == 1) "initValue" else "initOffset$i"}, in, startIndex${plusValue((i - 1) * blockSize)}, out, tmpOutPos, mBits$i)")
    methodSpec.addStatement("tmpOutPos += mBits$i")
  }
  methodSpec.addStatement("initValue = in[startIndex + ${(blockCount - 1) * blockSize + (blockSize - 1)}]")
  methodSpec.endControlFlow()

  methodSpec.addCode(
    """
      
    for (; startIndex < endIndex; startIndex += $blockSize) {
      final int mBits = maxDiffBits(initValue, in, startIndex);
      out[tmpOutPos++] = mBits;
      pack(initValue, in, startIndex, out, tmpOutPos, mBits);
      tmpOutPos += mBits;
      initValue = in[startIndex + ${blockSize - 1}];
    }
    
    """.trimIndent()
  )

  methodSpec.addStatement("return tmpOutPos")

  return methodSpec.build()
}

private fun decompressMethod(sizeBits: Int): MethodSpec {
  val isInt = sizeBits == Int.SIZE_BITS
  val arrayType = if (isInt) IntArray::class.java else LongArray::class.java

  val methodSpec = MethodSpec.methodBuilder("decompressIntegrated")
    .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
    .addParameter(arrayType, "in", Modifier.FINAL)
    .addParameter(Int::class.java, "startIndex")
    .addParameter(arrayType, "out", Modifier.FINAL)
    .addParameter(Int::class.java, "outPosition", Modifier.FINAL)
    .addParameter(Int::class.java, "outEndIndex", Modifier.FINAL)
    .addParameter(if (isInt) Int::class.java else Long::class.java, "initValue")
    .returns(Void.TYPE)

  val blockCount = if (isInt) 4 else 8
  val blockSize = if (sizeBits == Int.SIZE_BITS) 32 else 64

  methodSpec.addStatement("assert outEndIndex != 0")
  methodSpec.addStatement("int index = startIndex")
  methodSpec.addStatement("int s = outPosition")

  val type = if (isInt) "int" else "long"
  methodSpec.beginControlFlow("for (; s + ${(blockSize * blockCount) - 1} < outEndIndex; s += ${blockSize * blockCount})")
  methodSpec.addStatement("final $type mBits1 = in[index] >>> ${(blockCount - 1) * 8}")
  for (i in 2 until blockCount) {
    methodSpec.addStatement("final $type mBits$i = in[index] >>> ${(blockCount - i) * 8} & 0xff")
  }
  methodSpec.addStatement("final $type mBits$blockCount = in[index] & 0xff")
  methodSpec.addStatement("index++")

  for (i in 1..blockCount) {
    methodSpec.addStatement("unpack(initValue, in, index, out, s${plusValue((i - 1) * blockSize)}, mBits$i)")
    methodSpec.addStatement("index += mBits$i")
    methodSpec.addStatement("initValue = out[s + ${((i - 1) * blockSize) + (blockSize - 1)}]")
  }

  methodSpec.endControlFlow()

  methodSpec.addCode(
    """
      
    for (; s < outEndIndex; s += $blockSize) {
      final $type mBits = in[index];
      index++;
      unpack(initValue, in, index, out, s, mBits);
      initValue = out[s + ${blockSize - 1}];
      index += mBits;
    }
    
    """.trimIndent()
  )
  return methodSpec.build()
}

private fun addPack(bitCount: Int, sizeBits: Int): MethodSpec? {
  val methodSpec = MethodSpec.methodBuilder("pack$bitCount")
    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
    .returns(Void.TYPE)

  addPackParams(methodSpec, sizeBits == Int.SIZE_BITS)

  val statement = StringBuilder()

  var rightShift = 0
  var shift = bitCount
  var position = 0
  var rest: Int
  for (i in 0 until bitCount) {
    statement.append(if (i == 0) "out[outPos]" else "out[outPos + $i]").append(" = ")

    if (i == 0) {
      statement.append("in[inPos] - initValue")
    }
    else {
      statement.append("(in[inPos + $position] - in[inPos${plusValue(position - 1)}])")
      if (rightShift != 0) {
        statement.append(" >>> $rightShift")
      }
    }

    while (true) {
      position++

      statement.append(" | (in[inPos + $position] - in[inPos${plusValue(position - 1)}]) << $shift")
      shift += bitCount
      if (shift >= sizeBits) {
        break
      }
    }

    rest = shift - sizeBits
    if (rest == 0) {
      // everything was packed
      position++
      rightShift = 0
      shift = bitCount
    }
    else {
      rightShift = bitCount - rest
      shift = rest
    }

    methodSpec.addStatement(statement.toString())
    statement.setLength(0)
  }

  return methodSpec.build()
}

private fun addUnpack(bitCount: Int, sizeBits: Int): MethodSpec? {
  val methodSpec = MethodSpec.methodBuilder("unpack$bitCount")
    .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
    .returns(Void.TYPE)

  val step: Long = (2L shl (bitCount - 1)) - 1
  addPackParams(methodSpec, sizeBits == Int.SIZE_BITS)

  val statement = StringBuilder()
  var shift = 0
  var position = 0
  for (i in 0 until sizeBits) {
    statement.append("out[outPos${plusValue(i)}] = (in[inPos${plusValue(position)}]")
    if (shift == 0) {
      statement.append(" & $step${if (step > Int.MAX_VALUE) "L" else ""}")
      shift += bitCount
    }
    else {
      statement.append(" >>> $shift")
      val rest = sizeBits - shift
      if (rest < bitCount) {
        val toDecode = bitCount - rest
        position++
        // decode available bits
        val m = (2L shl (toDecode - 1)) - 1
        statement.append(" | (in[inPos + ${position}] & $m${if (m > Int.MAX_VALUE) "L" else ""}) << $rest")
        shift = bitCount - rest
      }
      else {
        if (rest != bitCount) {
          statement.append(" & $step${if (step > Int.MAX_VALUE) "L" else ""}")
        }

        shift += bitCount
        if (shift >= sizeBits) {
          shift = 0
          position++
        }
      }
    }

    if (i == 0) {
      statement.append(") + initValue")
    }
    else {
      statement.append(") + out[outPos${plusValue(i - 1)}]")
    }

    methodSpec.addStatement(statement.toString())
    statement.setLength(0)
  }
  return methodSpec.build()
}

private fun addPackParams(methodSpec: MethodSpec.Builder, isInt: Boolean) {
  val arrayType = if (isInt) IntArray::class.java else LongArray::class.java
  methodSpec
    .addParameter(if (isInt) Int::class.java else Long::class.java, "initValue", Modifier.FINAL)
    .addParameter(arrayType, "in", Modifier.FINAL)
    .addParameter(Int::class.java, "inPos", Modifier.FINAL)
    .addParameter(arrayType, "out", Modifier.FINAL)
    .addParameter(Int::class.java, "outPos", Modifier.FINAL)
}

private fun plusValue(value: Int): String {
  return if (value == 0) "" else " + $value"
}