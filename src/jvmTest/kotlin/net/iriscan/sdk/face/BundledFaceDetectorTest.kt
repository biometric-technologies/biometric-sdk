package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.ImageColorType
import net.iriscan.sdk.face.impl.FaceExtractorInternal
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
        // reading both outputs also checks their sizes: a swapped or resized output would not fit
        val flat = Image(640, 480, ImageColorType.RGB, IntArray(640 * 480) { 0x808080 })
        assertTrue(extractor.detectFaces(flat).isEmpty())
        assertNull(extractor.extract(flat, true, null))
        val stripes = Image(300, 500, ImageColorType.GRAY, IntArray(300 * 500) { if ((it % 300) / 10 % 2 == 0) 0 else 255 })
        assertNull(extractor.extract(stripes, true, null))
    }
}
