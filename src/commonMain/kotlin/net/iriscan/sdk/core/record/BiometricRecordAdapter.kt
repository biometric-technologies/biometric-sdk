package net.iriscan.sdk.core.record

/**
 * @author Slava Gornostal
 *
 * Implement this interface for custom biometric record format and add during SDK configuration
 *
 * @see BiometricRecord
 * @see net.iriscan.sdk.BiometricSdkConfig
 */
interface BiometricRecordAdapter<R : BiometricRecord> {
    val formatIdentifier: String
    fun deserialize(data: ByteArray): R
    fun serialize(record: R): ByteArray
    fun validate(data: ByteArray): BiometricRecordValidationResult
}