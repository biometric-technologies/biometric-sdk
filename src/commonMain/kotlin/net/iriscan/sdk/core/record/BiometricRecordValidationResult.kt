package net.iriscan.sdk.core.record

/**
 * @author Slava Gornostal
 *
 * Biometric record validation result
 *
 * @see BiometricRecord
 * @see BiometricRecordAdapter
 */
data class BiometricRecordValidationResult(val validated: Boolean, val error: String?)