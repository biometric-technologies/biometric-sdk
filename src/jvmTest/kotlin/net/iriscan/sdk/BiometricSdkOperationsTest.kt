package net.iriscan.sdk

import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.face.FaceOperations
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Face is set up apart from iris: a face failure must not take iris down. */
class BiometricSdkOperationsTest {

    private val irisConfig = IrisConfig(IrisExtractProperties(), IrisEncodeProperties(), IrisMatchProperties())
    private val faceConfig = FaceConfig(extractor = null, encoder = null, matcher = FaceMatchProperties(threshold = 1.0), liveness = null)

    @Test
    fun `iris keeps working when the face native libraries do not load`() {
        val missingLibrary = UnsatisfiedLinkError("no jniopencv_highgui in java.library.path")
        val sdk = BiometricSdkOperationsImpl(BiometricSdkConfig(null, irisConfig, faceConfig)) { throw missingLibrary }

        sdk.iris().matcher() // does not throw
        val error = assertFailsWith<IllegalStateException> { sdk.face() }
        assertSame(missingLibrary, error.cause)
        assertTrue("jniopencv_highgui" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `face is returned when it sets up`() {
        var created: FaceOperations? = null
        val sdk = BiometricSdkOperationsImpl(BiometricSdkConfig(null, irisConfig, faceConfig)) { config ->
            net.iriscan.sdk.face.impl.FaceOperationsImpl(config).also { created = it }
        }
        assertSame(created, sdk.face())
    }

    @Test
    fun `face without face configuration asks to initialize it`() {
        val sdk = BiometricSdkOperationsImpl(BiometricSdkConfig(null, irisConfig, null))
        val error = assertFailsWith<IllegalStateException> { sdk.face() }
        assertEquals("Please initialize SDK with face first", error.message)
    }
}
