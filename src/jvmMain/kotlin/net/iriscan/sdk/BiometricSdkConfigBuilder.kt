package net.iriscan.sdk

import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

actual class BiometricSdkConfigBuilder {
    actual fun withIris(
        extractor: IrisExtractProperties,
        encoder: IrisEncodeProperties,
        matcher: IrisMatchProperties
    ): BiometricSdkConfigBuilder {
        TODO("Not yet implemented")
    }

    actual fun withFace(
        extractor: FaceExtractProperties,
        encoder: FaceEncodeProperties,
        matcher: FaceMatchProperties
    ): BiometricSdkConfigBuilder {
        TODO("Not yet implemented")
    }

    actual fun build(): BiometricSdkConfig {
        TODO("Not yet implemented")
    }

}