package net.iriscan.sdk.io

import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 */
interface InputOutputRecordOperations {
    fun readRecord(data: ByteArray): BiometricRecord
    fun readRecord(filePath: String): BiometricRecord
    fun writeRecord(filePath: String, record: BiometricRecord)
    fun writeAsByteArrayRecord(record: BiometricRecord): ByteArray
}