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
class BiometricSdkConfig(val iris: IrisConfig?, val face: FaceConfig?)

data class IrisConfig(
    val extractor: IrisExtractProperties,
    val encoder: IrisEncodeProperties,
    val matcher: IrisMatchProperties,
)

data class FaceConfig(
    val extractor: FaceExtractProperties,
    val encoder: FaceEncodeProperties,
    val matcher: FaceMatchProperties,
    val faceNetModel: ByteArray
)

expect class BiometricSdkConfigBuilder {
    fun withIris(
        extractor: IrisExtractProperties,
        encoder: IrisEncodeProperties,
        matcher: IrisMatchProperties,
    ): BiometricSdkConfigBuilder

    fun withFace(
        extractor: FaceExtractProperties,
        encoder: FaceEncodeProperties,
        matcher: FaceMatchProperties
    ): BiometricSdkConfigBuilder

    fun build(): BiometricSdkConfig
}
