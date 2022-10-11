package net.iriscan.sdk.io

import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 */
interface InputOutputBiometricOperations {
    fun <T : BiometricRecord> readRecord(data: ByteArray): T
    fun <T : BiometricRecord> readRecord(filePath: String): T
    fun writeRecord(filePath: String, record: BiometricRecord)
    fun writeAsByteArrayRecord(record: BiometricRecord): ByteArray
}