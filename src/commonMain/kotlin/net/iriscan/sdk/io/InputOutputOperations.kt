package net.iriscan.sdk.io

import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 *
 * Input/Output operations
 * All functions should be thread-safe
 */
interface InputOutputOperations {
    fun <T : BiometricRecord> convert(data: ByteArray): T
    fun <T : BiometricRecord> read(filePath: String): T
    fun write(filePath: String, record: BiometricRecord)
    fun writeAsByteArray(record: BiometricRecord): ByteArray
}
