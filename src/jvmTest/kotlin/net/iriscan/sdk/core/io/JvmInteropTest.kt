package net.iriscan.sdk.core.io

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.io.image.serializers.BMP
import net.iriscan.sdk.io.image.serializers.PNG
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Compares the pure-Kotlin implementations with the JDK on many random inputs: java.util.zip
 * (which wraps the C zlib), java.security.MessageDigest and javax.imageio.
 */
class JvmInteropTest {

    private val random = Random(20261101)

    /** Inputs of many sizes and kinds: empty, tiny, around block boundaries, repetitive, random, sparse. */
    private fun samples(): List<ByteArray> {
        val sizes = listOf(0, 1, 2, 3, 55, 56, 63, 64, 65, 255, 256, 1000, 5551, 5552, 5553, 32768, 65535, 65536, 65537, 200_000)
        return sizes.flatMap { size ->
            listOf(
                ByteArray(size) { random.nextInt(256).toByte() },
                ByteArray(size) { (it % 7).toByte() },
                ByteArray(size) { if (random.nextInt(10) == 0) random.nextInt(256).toByte() else 0 },
            )
        }
    }

    @Test
    fun checksumsMatchTheJdk() {
        for (data in samples()) {
            val crc = java.util.zip.CRC32().apply { update(data) }.value.toInt()
            val adler = java.util.zip.Adler32().apply { update(data) }.value.toInt()
            val sha = MessageDigest.getInstance("SHA-256").digest(data)
            assertEquals(crc, CRC32.compute(data), "CRC-32 of ${data.size} bytes")
            assertEquals(adler, adler32(data), "Adler-32 of ${data.size} bytes")
            assertContentEquals(sha, SHA256.digest(data), "SHA-256 of ${data.size} bytes")
        }
    }

    @Test
    fun jdkInflatesOurStreams() {
        for (data in samples()) {
            val inflater = Inflater()
            inflater.setInput(ZLib.compress(data))
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                check(!inflater.needsInput()) { "the JDK needs more input than our stream of ${data.size} bytes has" }
                out.write(buffer, 0, inflater.inflate(buffer))
            }
            assertEquals(0, inflater.remaining, "no bytes after the Adler-32 checksum")
            inflater.end()
            assertContentEquals(data, out.toByteArray(), "${data.size} bytes")
        }
    }

    @Test
    fun weInflateJdkStreamsAtEveryLevelAndStrategy() {
        val strategies = listOf(Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY)
        for (data in samples().filter { it.size <= 65537 }) {
            for (level in listOf(Deflater.NO_COMPRESSION, Deflater.BEST_SPEED, 6, Deflater.BEST_COMPRESSION)) {
                for (strategy in strategies) {
                    val deflater = Deflater(level).apply { setStrategy(strategy); setInput(data); finish() }
                    val stream = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (!deflater.finished()) stream.write(buffer, 0, deflater.deflate(buffer))
                    deflater.end()
                    assertContentEquals(data, ZLib.uncompress(stream.toByteArray()), "${data.size} bytes, level $level, strategy $strategy")
                }
            }
        }
    }

    private fun randomImage(width: Int, height: Int) =
        Image(width, height, ImageColorType.RGB, IntArray(width * height) { random.nextInt(0x1000000) })

    private fun BufferedImage.pixels(): IntArray = IntArray(width * height) { getRGB(it % width, it / width) and 0xFFFFFF }

    @Test
    fun imageIoReadsOurPngAndBmp() {
        for ((width, height) in listOf(1 to 1, 7 to 5, 33 to 17, 640 to 3)) {
            val image = randomImage(width, height)
            for ((name, bytes) in listOf("png" to PNG.write(image), "bmp" to BMP.write(image))) {
                val decoded = ImageIO.read(ByteArrayInputStream(bytes)) ?: error("ImageIO cannot read our $name")
                assertContentEquals(image.colors, decoded.pixels(), "$name ${width}x$height")
            }
        }
    }

    @Test
    fun weReadPngAndBmpWrittenByImageIo() {
        for (type in listOf(BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_3BYTE_BGR)) {
            val source = BufferedImage(29, 13, type)
            for (y in 0 until 13) for (x in 0 until 29) source.setRGB(x, y, (0xFF shl 24) or random.nextInt(0x1000000))
            for (format in listOf("png", "bmp")) {
                if (format == "bmp" && type == BufferedImage.TYPE_INT_ARGB) continue // ImageIO writes no BMP with alpha
                val bytes = ByteArrayOutputStream().also { ImageIO.write(source, format, it) }.toByteArray()
                val image = if (format == "png") PNG.read(bytes) else BMP.read(bytes)
                assertContentEquals(source.pixels(), image.colors, "$format from image type $type")
            }
        }
    }

    @Test
    fun weReadGrayscalePngFromImageIo() {
        val source = BufferedImage(31, 9, BufferedImage.TYPE_BYTE_GRAY)
        val raster = source.raster
        for (y in 0 until 9) for (x in 0 until 31) raster.setSample(x, y, 0, (x * 8 + y) and 0xFF)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(source, "png", it) }.toByteArray()
        val image = PNG.read(bytes)
        assertEquals(ImageColorType.GRAY, image.colorType)
        assertContentEquals(IntArray(31 * 9) { raster.getSample(it % 31, it / 31, 0) }, image.colors)
    }
}
