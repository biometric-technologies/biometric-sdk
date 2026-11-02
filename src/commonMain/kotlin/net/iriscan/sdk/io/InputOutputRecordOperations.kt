package net.iriscan.sdk.io

import net.iriscan.sdk.core.record.BiometricRecord

/**
 * @author Slava Gornostal
 *
 * Biometric records in the 2011 layouts of ISO/IEC 19794-6, iris images
 * ([net.iriscan.sdk.iris.record.iso.IsoRecord19794_6]), and ISO/IEC 19794-2, finger minutiae
 * ([net.iriscan.sdk.finger.record.iso.IsoRecord19794_2]).
 */
interface InputOutputRecordOperations {

    /**
     * Reads a record of either format, recognized by its signature ("IIR" or "FMR").
     *
     * @throws net.iriscan.sdk.io.exception.UnknownFormatException if [data] is not such a record
     * @throws net.iriscan.sdk.io.exception.IOException if the data ends inside the record
     */
    fun readRecord(data: ByteArray): BiometricRecord

    /**
     * Writes an `IsoRecord19794_6` or an `IsoRecord19794_2` that carries its format's identifier
     * ("IIR" or "FMR"), as the records returned by [readRecord] do.
     *
     * @throws net.iriscan.sdk.io.exception.UnknownFormatException for any other record
     */
    fun writeRecord(record: BiometricRecord): ByteArray
}