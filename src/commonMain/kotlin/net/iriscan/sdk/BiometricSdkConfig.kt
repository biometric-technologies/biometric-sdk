package net.iriscan.sdk

import net.iriscan.sdk.io.image.ImageSerializer
import net.iriscan.sdk.io.record.BiometricRecordSerializer
import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

/**
 * @author Slava Gornostal
 *
 * SDK configuration object
 */
data class BiometricSdkConfig(
    val biometricSerializers: List<BiometricRecordSerializer<*>>,
    val imageSerializers: List<ImageSerializer>,
    val iris: IrisConfig,
) {
    companion object {
        fun builder() = Builder()
    }

    data class Builder(
        var biometricSerializers: List<BiometricRecordSerializer<*>>? = null,
        var imageSerializers: List<ImageSerializer>? = null,
        var irisConfig: IrisConfig? = null,
    ) {
        fun withBiometricSerializers(serializers: List<BiometricRecordSerializer<*>>) =
            apply { this.biometricSerializers = serializers }

        fun withImageSerializers(serializers: List<ImageSerializer>) =
            apply { this.imageSerializers = serializers }

        fun configureIris(
            extractor: IrisExtractProperties,
            encoder: IrisEncodeProperties,
            matcher: IrisMatchProperties
        ) =
            apply {
                this.irisConfig = IrisConfig(extractor, encoder, matcher)
            }

        fun build(): BiometricSdkConfig =
            BiometricSdkConfig(
                biometricSerializers = biometricSerializers ?: emptyList(),
                imageSerializers = imageSerializers ?: emptyList(),
                iris = irisConfig ?: IrisConfig(
                    IrisExtractProperties(),
                    IrisEncodeProperties(),
                    IrisMatchProperties()
                ),
            )
    }
}

data class IrisConfig(
    val extractor: IrisExtractProperties,
    val encoder: IrisEncodeProperties,
    val matcher: IrisMatchProperties,
)