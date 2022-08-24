package net.iriscan.sdk.core.record

/**
 * @author Slava Gornostal
 *
 * Biometric record interface
 */
interface BiometricRecord {
    val formatIdentifier: String
    val formatVersion: String
    val type: BiometricType
    val length: Long
    val certificationFlag: Byte
}


