package net.iriscan.sdk.io

import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 */
interface InputOutputRecordOperations {
    fun readRecord(data: ByteArray): BiometricRecord
    fun writeRecord(record: BiometricRecord): ByteArray
}