package net.iriscan.sdk

import android.content.Context
import net.iriscan.sdk.core.io.ResourceHelper
import net.iriscan.sdk.face.FaceEncodeProperties
import net.iriscan.sdk.face.FaceExtractProperties
import net.iriscan.sdk.face.FaceMatchProperties
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

actual class BiometricSdkConfigBuilder(context: Context) {
    private val resourceHelper = ResourceHelper(context)
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
            resourceHelper.getCached("facenet-model.tflite", encoder.faceNetModel.tfliteModelPath)
        )
        return this
    }

    actual fun build(): BiometricSdkConfig = BiometricSdkConfig(this.irisConfig, this.faceConfig)
}