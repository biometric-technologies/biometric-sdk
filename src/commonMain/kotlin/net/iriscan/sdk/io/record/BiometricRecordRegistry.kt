package net.iriscan.sdk.io.record

import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 *
 * Biometric formats registry
 */
internal class BiometricRecordRegistry(adapters: List<BiometricRecordSerializer<out BiometricRecord>>) {

    companion object {
        private val SERIALIZERS = mutableListOf<BiometricRecordSerializer<out BiometricRecord>>()
    }

    fun register(serializer: BiometricRecordSerializer<out BiometricRecord>) =
        SERIALIZERS.add(serializer)

    fun registerFirst(serializer: BiometricRecordSerializer<out BiometricRecord>) =
        SERIALIZERS.add(0, serializer)

}