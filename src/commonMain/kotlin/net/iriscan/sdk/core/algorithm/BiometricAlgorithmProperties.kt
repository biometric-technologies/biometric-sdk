package net.iriscan.sdk.core.algorithm

/**
 * @author Slava Gornostal
 *
 * Interface represents biometric algorithm properties
 */
interface BiometricAlgorithmProperties {
    fun asMap(): Map<String, Any>
}