package net.iriscan.sdk

import net.iriscan.sdk.core.algorithm.BiometricAlgorithmProperties
import net.iriscan.sdk.core.record.BiometricRecordAdapter
import net.iriscan.sdk.iris.IrisAlgorithm

/**
 * @author Slava Gornostal
 *
 * SDK configuration object
 */
data class BiometricSdkConfig(
    val adapters: List<BiometricRecordAdapter<*>>,
    val irisAlgorithm: IrisAlgorithm,
    val biometricAlgorithmProperties: BiometricAlgorithmProperties?,
) {
    companion object {
        fun builder() = Builder()
    }

    data class Builder(
        var adapters: List<BiometricRecordAdapter<*>>? = null,
        var irisAlgorithm: IrisAlgorithm = IrisAlgorithm.DAUGMAN,
        var irisAlgorithmProperties: BiometricAlgorithmProperties? = null,
    ) {
        fun withAdapters(adapters: List<BiometricRecordAdapter<*>>) =
            apply { this.adapters = adapters }

        fun configureIris(algorithm: IrisAlgorithm, properties: BiometricAlgorithmProperties) =
            apply {
                this.irisAlgorithm = algorithm
                this.irisAlgorithmProperties = properties
            }

        fun build(): BiometricSdkConfig =
            BiometricSdkConfig(
                adapters = adapters ?: emptyList(),
                irisAlgorithm = irisAlgorithm,
                biometricAlgorithmProperties = irisAlgorithmProperties,
            )
    }
}
