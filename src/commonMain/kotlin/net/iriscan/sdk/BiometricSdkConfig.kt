package net.iriscan.sdk

import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceLivenessDetectionProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

/**
 * @author Slava Gornostal
 *
 * SDK configuration object
 */
class BiometricSdkConfig(val context: PlatformContext?, val iris: IrisConfig?, val face: FaceConfig?)

data class IrisConfig(
    val extractor: IrisExtractProperties,
    val encoder: IrisEncodeProperties,
    val matcher: IrisMatchProperties,
)

data class FaceConfig(
    val extractor: FaceExtractProperties?,
    val encoder: FaceEncodeProperties?,
    val matcher: FaceMatchProperties?,
    val liveness: FaceLivenessDetectionProperties?,
)

class BiometricSdkConfigBuilder {
    private var context: PlatformContext? = null
    private var irisConfig: IrisConfig? = null
    private var faceConfig: FaceConfig? = null

    fun withContext(context: PlatformContext): BiometricSdkConfigBuilder {
        this.context = context
        return this
    }

    fun withIris(
        extractor: IrisExtractProperties,
        encoder: IrisEncodeProperties,
        matcher: IrisMatchProperties
    ): BiometricSdkConfigBuilder {
        this.irisConfig = IrisConfig(extractor, encoder, matcher)
        return this
    }

    fun withFace(
        extractor: FaceExtractProperties? = null,
        encoder: FaceEncodeProperties? = null,
        matcher: FaceMatchProperties? = null,
        liveness: FaceLivenessDetectionProperties? = null
    ): BiometricSdkConfigBuilder {
        this.faceConfig = FaceConfig(
            extractor,
            encoder,
            matcher,
            liveness,
        )
        return this
    }

    fun build(): BiometricSdkConfig =
        BiometricSdkConfig(this.context, this.irisConfig, this.faceConfig)
}
