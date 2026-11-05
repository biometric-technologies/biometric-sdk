package net.iriscan.sdk.iris

import net.iriscan.sdk.BiometricSdkConfigBuilder
import net.iriscan.sdk.IrisConfig
import net.iriscan.sdk.iris.impl.NormalizedIris
import net.iriscan.sdk.iris.impl.encodeNormalized
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Settings of different properties classes that only work together. */
class IrisConfigTest {

    private fun config(normalizedWidth: Int = 256, templateCols: Int = 128) = IrisConfig(
        IrisExtractProperties(normalizedWidth = normalizedWidth),
        IrisEncodeProperties(templateCols = templateCols),
        IrisMatchProperties(),
    )

    /** A fully visible unwrapped iris [cols] samples wide, with texture at a few angular frequencies. */
    private fun texture(cols: Int): NormalizedIris {
        val rows = 16
        val values = FloatArray(rows * cols) { i ->
            val r = i / cols
            val a = 2 * PI * (i % cols) / cols
            (128 + 40 * sin(7 * a + r) + 30 * sin(23 * a - 2 * r) + 20 * sin(61 * a + 3 * r)).toFloat()
        }
        return NormalizedIris(rows, cols, values, BooleanArray(rows * cols) { true })
    }

    @Test
    fun templateColsThatDoNotDivideTheTextureWidthAreRejected() {
        config() // the defaults: 128 columns of a texture 256 wide
        val error = assertFailsWith<IllegalArgumentException> { config(templateCols = 96) }
        assertEquals("normalizedWidth (256) must be a multiple of templateCols (96)", error.message)
        assertFailsWith<IllegalArgumentException> { config(templateCols = 512) } // wider than the texture
        config(normalizedWidth = 512, templateCols = 512)
        // the application learns it where it configures the SDK, not at its first encoding
        assertFailsWith<IllegalArgumentException> {
            BiometricSdkConfigBuilder().withIris(
                IrisExtractProperties(), IrisEncodeProperties(templateCols = 96), IrisMatchProperties()
            )
        }
    }

    @Test
    fun aConfigurationIsAcceptedExactlyWhenItsTextureCanBeEncoded() {
        for (width in listOf(64, 128, 256, 512, 1024, 2048, 4096)) {
            val texture = texture(width)
            val accepted = (32..2048 step 32).filter { cols ->
                val isAccepted = runCatching { config(width, cols) }.isSuccess
                val encodes = runCatching { encodeNormalized(texture, IrisEncodeProperties(templateCols = cols)) }.isSuccess
                assertEquals(encodes, isAccepted, "normalizedWidth $width, templateCols $cols")
                isAccepted
            }
            // the powers of two from 32 up to the texture width (templateCols is at most 2048)
            val powersOfTwo = generateSequence(32) { it * 2 }.takeWhile { it <= minOf(width, 2048) }.toList()
            assertEquals(powersOfTwo, accepted, "normalizedWidth $width")
        }
    }
}
