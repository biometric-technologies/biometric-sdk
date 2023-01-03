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
    val size = qualityRecords.size * 5 + 1 + 31 // + 1 - length of qualityRecords field
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