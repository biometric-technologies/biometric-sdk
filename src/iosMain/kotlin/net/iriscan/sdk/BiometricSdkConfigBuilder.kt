package net.iriscan.sdk

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import net.iriscan.sdk.core.io.ResourceHelperFactory
import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

actual class BiometricSdkConfigBuilder {
    var irisConfig: IrisConfig? = null
    var faceConfig: FaceConfig? = null
    actual fun withIris(
        extractor: IrisExtractProperties,
        encoder: IrisEncodeProperties,
        matcher: IrisMatchProperties
    ): BiometricSdkConfigBuilder {
        this.irisConfig = IrisConfig(extractor, encoder, matcher)
        return this
    }

    actual fun withFace(
        extractor: FaceExtractProperties,
        encoder: FaceEncodeProperties,
        matcher: FaceMatchProperties
    ): BiometricSdkConfigBuilder {
        this.faceConfig = FaceConfig(extractor, encoder, matcher)
        return this
    }

    actual fun build(): BiometricSdkConfig {
        Napier.base(DebugAntilog())
        ResourceHelperFactory.initialize()
        return BiometricSdkConfig(irisConfig, faceConfig)
    }

}