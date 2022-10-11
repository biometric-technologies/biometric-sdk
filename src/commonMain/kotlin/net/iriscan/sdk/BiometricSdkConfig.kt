package net.iriscan.sdk

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.io.image.ImageSerializer
import net.iriscan.sdk.io.record.BiometricRecordSerializer
import net.iriscan.sdk.iris.IrisAlgorithm

/**
 * @author Slava Gornostal
 *
 * SDK configuration object
 */
data class BiometricSdkConfig(
    val biometricSerializers: List<BiometricRecordSerializer<*>>,
    val imageSerializers: List<ImageSerializer>,
    val irisAlgorithm: IrisAlgorithm,
    val biometricAlgorithmProperties: BiometricAlgorithmProperties?,
) {
    companion object {
        fun builder() = Builder()
    }

    data class Builder(
        var biometricSerializers: List<BiometricRecordSerializer<*>>? = null,
        var imageSerializers: List<ImageSerializer>? = null,
        var irisAlgorithm: IrisAlgorithm = IrisAlgorithm.DAUGMAN,
        var irisAlgorithmProperties: BiometricAlgorithmProperties? = null,
    ) {
        fun withBiometricSerializers(serializers: List<BiometricRecordSerializer<*>>) =
            apply { this.biometricSerializers = serializers }

        fun withImageSerializers(serializers: List<ImageSerializer>) =
            apply { this.imageSerializers = serializers }

        fun configureIris(algorithm: IrisAlgorithm, properties: BiometricAlgorithmProperties) =
            apply {
                this.irisAlgorithm = algorithm
                this.irisAlgorithmProperties = properties
            }

        fun build(): BiometricSdkConfig =
            BiometricSdkConfig(
                biometricSerializers = biometricSerializers ?: emptyList(),
                imageSerializers = imageSerializers ?: emptyList(),
                irisAlgorithm = irisAlgorithm,
                biometricAlgorithmProperties = irisAlgorithmProperties,
            )
    }
}
