package net.iriscan.sdk

import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

/**
 * @author Slava Gornostal
 *
 * SDK configuration object
 */
class BiometricSdkConfig(val iris: IrisConfig?, val face: FaceConfig?) {
    companion object {
        fun builder() = Builder()
    }

    data class Builder(
        var irisConfig: IrisConfig? = null,
        var faceConfig: FaceConfig? = null,
    ) {

        fun withIris(
            extractor: IrisExtractProperties = IrisExtractProperties(),
            encoder: IrisEncodeProperties = IrisEncodeProperties(),
            matcher: IrisMatchProperties = IrisMatchProperties()
        ) =
            apply {
                this.irisConfig = IrisConfig(extractor, encoder, matcher)
            }

        fun withFace(
            extractor: FaceExtractProperties = FaceExtractProperties(),
            encoder: FaceEncodeProperties = FaceEncodeProperties(),
            matcher: FaceMatchProperties = FaceMatchProperties()
        ) = apply {
            this.faceConfig = FaceConfig(extractor, encoder, matcher)
        }

        fun build(): BiometricSdkConfig =
            BiometricSdkConfig(
                iris = irisConfig,
                face = faceConfig,
            )
    }
}

data class IrisConfig(
    val extractor: IrisExtractProperties,
    val encoder: IrisEncodeProperties,
    val matcher: IrisMatchProperties,
)

data class FaceConfig(
    val extractor: FaceExtractProperties,
    val encoder: FaceEncodeProperties,
    val matcher: FaceMatchProperties,
)