package net.iriscan.sdk

import android.content.Context
import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

actual class BiometricSdkConfigBuilder(private val context: Context) {
    private var irisConfig: IrisConfig? = null
    private var faceConfig: FaceConfig? = null

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
        this.faceConfig = FaceConfig(
            extractor,
            encoder,
            matcher,
            context.assets.open("facenet.tflite").readBytes()
        )
        return this
    }

    actual fun build(): BiometricSdkConfig = BiometricSdkConfig(this.irisConfig, this.faceConfig)
}