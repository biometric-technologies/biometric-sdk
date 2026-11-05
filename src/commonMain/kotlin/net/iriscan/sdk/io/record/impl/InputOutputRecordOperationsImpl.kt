package net.iriscan.sdk.io.record.impl

import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.finger.record.iso.IsoRecord19794_2
import net.iriscan.sdk.finger.record.iso.IsoRecord19794_2Serializer
import net.iriscan.sdk.io.InputOutputRecordOperations
import net.iriscan.sdk.io.exception.UnknownFormatException
import net.iriscan.sdk.iris.record.iso.IsoRecord19794_6
import net.iriscan.sdk.iris.record.iso.IsoRecord19794_6Serializer

/**
 * Record I/O with the two record formats of the SDK: ISO/IEC 19794-6 iris images and
 * ISO/IEC 19794-2 finger minutiae.
 *
 * The serializers are called by record class, not looked up in a list of
 * `BiometricRecordSerializer<BiometricRecord>`: such a list needs an unchecked cast, and a record
 * of another class that carries the same format identifier would then fail with a
 * `ClassCastException` instead of [UnknownFormatException].
 *
 * @author Slava Gornostal
 */
internal class InputOutputRecordOperationsImpl : InputOutputRecordOperations {

    private val irisImageSerializer = IsoRecord19794_6Serializer()
    private val fingerMinutiaeSerializer = IsoRecord19794_2Serializer()

    override fun readRecord(data: ByteArray): BiometricRecord = when {
        irisImageSerializer.canRead(data) -> irisImageSerializer.read(data)
        fingerMinutiaeSerializer.canRead(data) -> fingerMinutiaeSerializer.read(data)
        else -> throw UnknownFormatException("Unknown biometric format")
    }

    /**
     * A serializer writes the record's own [BiometricRecord.formatIdentifier] as the signature of
     * the data, so a record that does not carry its format's identifier would be written as data
     * that [readRecord] does not recognize. Such a record is rejected like any other format.
     */
    override fun writeRecord(record: BiometricRecord): ByteArray = when {
        record is IsoRecord19794_6 && record.formatIdentifier == irisImageSerializer.formatIdentifier ->
            irisImageSerializer.write(record)

        record is IsoRecord19794_2 && record.formatIdentifier == fingerMinutiaeSerializer.formatIdentifier ->
            fingerMinutiaeSerializer.write(record)

        else -> throw UnknownFormatException("Unsupported biometric format")
    }
}