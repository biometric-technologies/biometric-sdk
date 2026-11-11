package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.iris.EyelidCurve
import net.iriscan.sdk.iris.IrisBoundaries
import net.iriscan.sdk.iris.IrisBoundaryCircle
import net.iriscan.sdk.iris.IrisExtractProperties

/**
 * @author Slava Gornostal
 *
 * Segments the eye image and returns the unwrapped iris texture
 * ([IrisExtractProperties.normalizedWidth] x [IrisExtractProperties.normalizedHeight], GRAY),
 * where occluded samples are set to -1. The input image is not modified.
 */
internal fun extractInternal(sample: Image, props: IrisExtractProperties): Image? =
    extractNormalized(sample, props)?.toImage()

internal fun extractNormalized(sample: Image, props: IrisExtractProperties): NormalizedIris? {
    val gray = toGray(sample)
    val seg = segmentIris(gray, props) ?: return null
    val normalized = normalizeIris(gray, seg, props)
    // too little visible iris for a usable template
    if (normalized.validFraction < 0.2) return null
    return normalized
}

/**
 * The segmentation of [sample] as public [IrisBoundaries]: the same [segmentIris] call that
 * [extractNormalized] makes, so what is drawn is exactly what gets unwrapped.
 */
internal fun segmentBoundaries(sample: Image, props: IrisExtractProperties): IrisBoundaries? {
    val seg = segmentIris(toGray(sample), props) ?: return null
    return IrisBoundaries(
        pupil = IrisBoundaryCircle(seg.pupil.x, seg.pupil.y, seg.pupil.r),
        limbus = IrisBoundaryCircle(seg.iris.x, seg.iris.y, seg.iris.r),
        upperEyelid = seg.upperEyelid?.let { EyelidCurve(it.a, it.b, it.c) },
        lowerEyelid = seg.lowerEyelid?.let { EyelidCurve(it.a, it.b, it.c) },
    )
}
