package net.iriscan.sdk.face

import net.iriscan.sdk.core.image.NativeImage

/**
 * @author Slava Gornostal
 *
 * Interface for extracting face from image or record
 */
interface FaceExtractor {
    /**
     * Detects and extracts face from raw image
     * */
    fun extract(nativeImage: NativeImage): NativeImage?
}