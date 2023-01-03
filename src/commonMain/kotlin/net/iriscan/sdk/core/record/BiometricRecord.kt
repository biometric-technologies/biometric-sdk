package net.iriscan.sdk.core.record

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 *
 * Biometric record interface
 */
interface BiometricRecord {
    val formatIdentifier: String
    val formatVersion: String
    val type: BiometricType
    val certificationFlag: Byte
}