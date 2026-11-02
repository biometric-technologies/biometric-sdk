package net.iriscan.sdk.iris.impl

import net.iriscan.sdk.core.image.Image
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
