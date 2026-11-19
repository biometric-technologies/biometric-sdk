package net.iriscan.sdk.face

import net.iriscan.sdk.face.impl.getOrientationBasedOnAngles
import kotlin.test.Test
import kotlin.test.assertEquals

/** Head position from yaw and pitch: 0 straight, 1 left, 2 right, 3 top, 4 bottom. */
class HeadPositionTest {

    private fun position(yaw: Double, pitch: Double) = getOrientationBasedOnAngles(yawDeg = yaw, pitchDeg = pitch, threshold = 15.0)

    @Test
    fun `small angles are straight`() {
        assertEquals(0, position(0.0, 0.0))
        assertEquals(0, position(14.9, -14.9))
    }

    @Test
    fun `yaw gives left and right from the threshold on`() {
        assertEquals(1, position(15.0, 0.0))
        assertEquals(2, position(-15.0, 0.0))
    }

    @Test
    fun `pitch gives top and bottom`() {
        assertEquals(3, position(0.0, 20.0))
        assertEquals(4, position(0.0, -20.0))
    }

    @Test
    fun `yaw is checked before pitch`() {
        assertEquals(1, position(20.0, 20.0))
        assertEquals(2, position(-20.0, -20.0))
    }
}
