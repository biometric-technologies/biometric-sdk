package net.iriscan.sdk.core.record

/**
 * @author Slava Gornostal
 *
 * Biometric record validation result
 *
 * @see BiometricRecord
 * @see BiometricRecordSerializer
 */
data class BiometricRecordValidationResult(val validated: Boolean, val error: String?)