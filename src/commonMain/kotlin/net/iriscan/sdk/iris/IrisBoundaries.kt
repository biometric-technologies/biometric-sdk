package net.iriscan.sdk.iris

/**
 * Where the SDK found the iris in an eye image: the boundaries [IrisExtractor.extract] unwraps
 * the iris between, and the eyelids it masks out. Returned by [IrisExtractor.segment].
 *
 * It is meant for showing the segmentation (an overlay on the photo in an enrollment screen or a
 * test report) and for finding out why an image does not encode: a pupil found on a spectacle
 * frame or a limbus found on an eyelash line is obvious in a picture and nearly impossible to see
 * from a score.
 *
 * Coordinates are pixels of the input image, x to the right and y down, with (0, 0) at the centre
 * of the top-left pixel.
 */
data class IrisBoundaries(
    /** The pupil boundary. */
    val pupil: IrisBoundaryCircle,
    /** The limbus, the boundary between the iris and the sclera. */
    val limbus: IrisBoundaryCircle,
    /** The edge of the upper eyelid where it covers the iris; null when it does not cover it. */
    val upperEyelid: EyelidCurve?,
    /** The edge of the lower eyelid where it covers the iris; null when it does not cover it. */
    val lowerEyelid: EyelidCurve?,
)

/** A circle in image pixels (see [IrisBoundaries] for the coordinates). */
data class IrisBoundaryCircle(val x: Double, val y: Double, val radius: Double)

/**
 * An eyelid edge as the parabola y = [a] + [b] x + [c] x² in image pixels. It is only meaningful
 * across the iris, between `limbus.x - limbus.radius` and `limbus.x + limbus.radius`.
 */
data class EyelidCurve(val a: Double, val b: Double, val c: Double) {
    /** The y of the eyelid edge at column [x]. */
    fun y(x: Double): Double = a + b * x + c * x * x
}
