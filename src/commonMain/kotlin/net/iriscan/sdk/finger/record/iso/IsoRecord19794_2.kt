package net.iriscan.sdk.finger.record.iso

import kotlinx.datetime.Instant
import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.core.record.BiometricType

/**
 * @author Anton Kurinnoy
 */
data class IsoRecord19794_2(
    override val formatIdentifier: String,
    override val formatVersion: String,
    override val type: BiometricType = BiometricType.FINGERPRINT,
    override val certificationFlag: Byte,
    val numberOfFingers: Int,
    val data: List<IsoRecord19794_2DataBlock>
) : BiometricRecord

data class IsoRecord19794_2DataBlock(
    val header: IsoRecord19794_2Header,
    val data: List<IsoRecord19794_2MinutiaData>,
    val extendedData: ByteArray
) {
    /** Minutiae: 5 bytes each (type and x, y, angle), or 6 when the header says they carry a quality byte. */
    private val dataSize = when (header.minutiaeLength) {
        5 -> header.minutiaeNumber * 5
        else -> header.minutiaeNumber * 6
    }

    val size = when (extendedData.size) {
        0 -> header.size + dataSize + 2
        else -> header.size + dataSize + 2 + extendedData.size
    }
}

data class IsoRecord19794_2Header(
    val dateAndTime: Instant,
    val deviceTechnologyId: Int,
    val deviceVendorId: Int,
    val deviceTypeId: Int,
    val qualityRecords: List<QualityRecord>,
    val certificationRecords: List<CertificationRecord>,
    val fingerPosition: Int,
    val representationNumber: Int,
    val spatialSamplingRateHoriz: Int,
    val spatialSamplingRateVert: Int,
    val impressionType: Int,
    val width: Int,
    val height: Int,
    val minutiaeLength: Int,
    val ridgeEndingType: Int,
    val minutiaeNumber: Int,
) {
    /**
     * Bytes this header takes in a record, without certification blocks: 31 bytes of fixed fields
     * (representation length 4, capture date and time 9, device technology 1, vendor 2, type 2,
     * finger position 1, representation number 1, horizontal and vertical resolution 4,
     * impression type 1, width 2, height 2, minutia length and ridge ending type 1, number of
     * minutiae 1), 1 byte for the number of quality records and 5 bytes per quality record.
     */
    val size = qualityRecords.size * 5 + 1 + 31
}

data class IsoRecord19794_2MinutiaData(
    val type: Int,
    val minutiaX: Int,
    val minutiaY: Int,
    val angle: Int,
    val quality: Int
)

data class QualityRecord(val qualityScore: Int, val qualityAlgorithmVendorId: Int, val qualityAlgorithmId: Int)

data class CertificationRecord(val certificationAuthorityId: Int, val certificationSchemeId: Int)