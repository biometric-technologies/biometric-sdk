package net.iriscan.sdk

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.io.image.ImageSerializer
import net.iriscan.sdk.io.record.BiometricRecordSerializer

/**
 * @author Slava Gornostal
 *
 * SDK configuration object
 */
data class BiometricSdkConfig(
    val biometricSerializers: List<BiometricRecordSerializer<*>>,
    val imageSerializers: List<ImageSerializer>,
    val biometricAlgorithmProperties: BiometricAlgorithmProperties?,
) {
    companion object {
        fun builder() = Builder()
    }

    data class Builder(
        var biometricSerializers: List<BiometricRecordSerializer<*>>? = null,
        var imageSerializers: List<ImageSerializer>? = null,
        var irisAlgorithmProperties: BiometricAlgorithmProperties? = null,
    ) {
        fun withBiometricSerializers(serializers: List<BiometricRecordSerializer<*>>) =
            apply { this.biometricSerializers = serializers }

        fun withImageSerializers(serializers: List<ImageSerializer>) =
            apply { this.imageSerializers = serializers }

        fun configureIris(properties: BiometricAlgorithmProperties) =
            apply {
                this.irisAlgorithmProperties = properties
            }

        fun build(): BiometricSdkConfig =
            BiometricSdkConfig(
                biometricSerializers = biometricSerializers ?: emptyList(),
                imageSerializers = imageSerializers ?: emptyList(),
                biometricAlgorithmProperties = irisAlgorithmProperties,
            )
    }
}
