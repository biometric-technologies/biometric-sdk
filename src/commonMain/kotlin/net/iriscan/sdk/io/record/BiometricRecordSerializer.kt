package net.iriscan.sdk.io.record

import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.core.record.BiometricRecordValidationResult

/**
 * @author Slava Gornostal
 *
 * Implement this interface for custom biometric record format and add during SDK configuration
 *
 * @see BiometricRecord
 * @see net.iriscan.sdk.BiometricSdkConfig
 */
interface BiometricRecordSerializer<R : BiometricRecord> {
    fun canRead(data: ByteArray): Boolean
    fun read(data: ByteArray): R
    fun write(record: R): ByteArray
    fun validate(data: ByteArray): BiometricRecordValidationResult
}