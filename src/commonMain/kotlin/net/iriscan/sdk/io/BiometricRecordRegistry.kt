package net.iriscan.sdk.io

import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.core.record.BiometricRecordAdapter

/**
 * @author Slava Gornostal
 *
 * Biometric formats registry
 */
internal class BiometricRecordRegistry(adapters: List<BiometricRecordAdapter<out BiometricRecord>>) {

    companion object {
        private val SDK_DEFAULT_FORMATS = listOf<BiometricRecordAdapter<out BiometricRecord>>()
    }

    private val availableFormats: List<BiometricRecordAdapter<out BiometricRecord>> =
        SDK_DEFAULT_FORMATS.filter { adapters.count { cf -> it.formatIdentifier == cf.formatIdentifier } == 0 } + adapters

    internal fun getAdapterFor(formatIdentifier: String): BiometricRecordAdapter<out BiometricRecord>? =
        availableFormats.find { it.formatIdentifier == formatIdentifier }

}