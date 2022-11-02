package net.iriscan.sdk.core.image

import kotlin.math.pow

/**
 * @author Slava Gornostal
 */
data class Circle(val x: Int, val y: Int, val r: Int) {
    fun contains(x: Int, y: Int): Boolean =
        (x - this.x).toDouble().pow(2.0) + (y - this.y).toDouble().pow(2.0) <= r.toDouble().pow(2.0)
}