package net.iriscan.sdk.face

import net.iriscan.sdk.face.impl.haar.HaarCascade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The cascade files shipped in the JVM jar are read completely. */
class BundledCascadesTest {

    private fun load(name: String) = HaarCascade.parse(javaClass.getResourceAsStream("/$name")!!.use { it.readBytes().decodeToString() })

    @Test
    fun `frontal face cascade`() {
        val cascade = load("haarcascade_frontalface_alt.xml")
        assertEquals(20 to 20, cascade.windowWidth to cascade.windowHeight)
        assertEquals(22, cascade.stages.size)
        assertEquals(3, cascade.stages.first().classifiers.size)
        assertEquals(cascade.stages.sumOf { it.classifiers.size }, cascade.features.size)
        assertTrue(cascade.features.all { it.rects.size in 2..3 })
    }

    @Test
    fun `eye cascade`() {
        val cascade = load("haarcascade_eye.xml")
        assertEquals(20 to 20, cascade.windowWidth to cascade.windowHeight)
        assertEquals(24, cascade.stages.size)
    }
}
