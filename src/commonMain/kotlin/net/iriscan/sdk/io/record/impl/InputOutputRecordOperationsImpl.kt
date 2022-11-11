package net.iriscan.sdk.io.record.impl

import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.io.InputOutputRecordOperations
import net.iriscan.sdk.io.exception.UnknownFormatException
import net.iriscan.sdk.io.record.BiometricRecordSerializer

/**
 * @author Slava Gornostal
 */
internal class InputOutputRecordOperationsImpl : InputOutputRecordOperations {

    private val serializers = listOf<BiometricRecordSerializer<BiometricRecord>>()

    override fun readRecord(data: ByteArray): BiometricRecord {
        val serializer = serializers.firstOrNull { it.canRead(data) }
            ?: throw UnknownFormatException("Unknown biometric format")
        return serializer.read(data)
    }


    override fun writeRecord(record: BiometricRecord): ByteArray {
        val serializer = serializers.firstOrNull { it.formatIdentifier == record.formatIdentifier }
            ?: throw UnknownFormatException("Unsupported biometric format")
        return serializer.write(record)
    }
}