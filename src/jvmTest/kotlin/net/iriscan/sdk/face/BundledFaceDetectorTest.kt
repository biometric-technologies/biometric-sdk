package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.face.impl.FaceExtractorInternal
import org.bytedeco.javacpp.Loader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled BlazeFace model loads from the jar and runs in TensorFlow Lite. Real faces are
 * checked end to end by biometric-sdk-samples (face photos are not stored in this repository).
 */
class BundledFaceDetectorTest {
    private val extractor = FaceExtractorInternal()

    @Test
    fun modelIsBundled() {
        val model = assertNotNull(javaClass.getResourceAsStream("/models/face_detection_short_range.tflite")).use { it.readBytes() }
        // TensorFlow Lite flatbuffer identifier
        assertEquals("TFL3", model.copyOfRange(4, 8).decodeToString())
        assertTrue(model.size in 200_000..300_000, "${model.size} bytes")
    }

    @Test
    fun imagesWithoutFacesGiveNoFace() {
        // bytedeco's tensorflow-lite 2.10.0-1.5.8 has natives only for these platforms; elsewhere
        // (the macOS arm64 runner of the CI build job) there is nothing to run the model with
        if (Loader.getPlatform() !in PLATFORMS_WITH_NATIVES) {
            println("BundledFaceDetectorTest skipped: no TensorFlow Lite natives for ${Loader.getPlatform()}")
            return
        }
        // reading both outputs also checks their sizes: a swapped or resized output would not fit
        val flat = Image(640, 480, ImageColorType.RGB, IntArray(640 * 480) { 0x808080 })
        assertTrue(extractor.detectFaces(flat).isEmpty())
        assertNull(extractor.extract(flat, true, null))
        // a horizontal gray ramp; not stripes: regular vertical stripes do look like a face to
        // BlazeFace (score 0.6 in MediaPipe's own pipeline), which is the model, not this code
        val ramp = Image(300, 500, ImageColorType.GRAY, IntArray(300 * 500) { (it % 300) * 255 / 299 })
        assertNull(extractor.extract(ramp, true, null))
    }

    private companion object {
        val PLATFORMS_WITH_NATIVES = setOf("linux-x86_64", "linux-arm64", "macosx-x86_64", "windows-x86_64")
    }
}
