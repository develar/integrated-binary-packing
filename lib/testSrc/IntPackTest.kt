package org.jetbrains.integratedBinaryPacking

import io.netty.buffer.PooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class IntPackTest(private val maxBitCount: Int) {
  companion object {
    @Parameterized.Parameters
    @JvmStatic
    fun data(): Iterable<Array<Int>> {
      return Array(Int.SIZE_BITS - 1) { k -> Array(1) { k + 1 } }.asIterable()
    }
  }

  @Test
  fun testPositive() {
    var n = IntegratedBinaryPacking.INT_BLOCK_SIZE
    do {
      doTest(n, maxBitCount, 0)
      n *= 10
    } while (n <= 10_024)
  }

  @Test
  fun testNegative() {
    var n = IntegratedBinaryPacking.INT_BLOCK_SIZE
    do {
      doTest(n, maxBitCount, Integer.MIN_VALUE)
      n *= 10
    } while (n <= 10_024)
  }

  private fun doTest(n: Int, maxBitCount: Int, startValue: Int) {
    val maxDelta = (1 shl maxBitCount) - 1
    val randomUntil = if (maxDelta == Integer.MAX_VALUE) maxDelta else (maxDelta + 1)

    var original = IntArray(n)
    original[0] = startValue
    for (i in 1 until n) {
      val prevValue = original[i - 1]
      val value = prevValue + random.nextInt(randomUntil)
      if (value < prevValue) {
        // overflow - trim to nearest block size
        val newSize = i - (i % IntegratedBinaryPacking.INT_BLOCK_SIZE)
        if (newSize == 0) {
          original = IntArray(IntegratedBinaryPacking.INT_BLOCK_SIZE)
          original[original.size - 1] = maxDelta
        }
        else {
          original = original.copyOf(newSize)
        }
        break
      }
      original[i] = value
    }

    val compressed = IntArray(IntegratedBinaryPacking.estimateCompressedArrayLength(original, 0, original.size, 0))
    val compressedSize = IntBitPacker.compressIntegrated(original, 0, original.size, compressed, 0)
    assertThat(compressedSize).isLessThanOrEqualTo(compressed.size)

    val back = IntArray(original.size)
    IntBitPacker.decompressIntegrated(compressed, 0, back, 0, original.size, 0)
    assertThat(back).withRepresentation(arrayPresentation).isEqualTo(original)
  }

  @Test
  fun positiveVariable() {
    var n = IntegratedBinaryPacking.INT_BLOCK_SIZE / 3
    do {
      testVariable(n, maxBitCount, 0)
      n *= 10
    } while (n <= 10_024)
  }

  @Test
  fun negativeVariable() {
    var n = IntegratedBinaryPacking.INT_BLOCK_SIZE / 3
    do {
      testVariable(n, maxBitCount, Integer.MIN_VALUE)
      n *= 10
    } while (n <= 10_024)
  }

  private fun testVariable(n: Int, maxBitCount: Int, startValue: Int) {
    val maxDelta = (1 shl maxBitCount) - 1
    val randomUntil = if (maxDelta == Integer.MAX_VALUE) maxDelta else (maxDelta + 1)

    var original = IntArray(n)
    original[0] = startValue
    for (i in 1 until n) {
      val prevValue = original[i - 1]
      val value = prevValue + random.nextInt(randomUntil)
      if (value < prevValue) {
        // overflow - trim to nearest block size
        original = original.copyOf(i)
        break
      }
      original[i] = value
    }

    val out = PooledByteBufAllocator.DEFAULT.heapBuffer()
    IntBitPacker.compressVariable(original, 0, original.size, out)

    val back = IntArray(original.size)
    IntBitPacker.decompressVariable(out, back, original.size)
    assertThat(back).withRepresentation(arrayPresentation).isEqualTo(original)
  }
}