package net.iriscan.sdk.finger.record.iso

import net.iriscan.sdk.core.io.MemoryStream
import net.iriscan.sdk.core.io.buildBytes

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.iriscan.sdk.io.record.BiometricRecordSerializer

/**
 * @author Anton Kurinnoy
 */
class IsoRecord19794_2Serializer : BiometricRecordSerializer<IsoRecord19794_2> {

    private companion object {
        /** "FMR" (finger minutiae record) and a zero byte. */
        const val FORMAT_IDENTIFIER = "FMR"

        /** Format identifier (4), version (4), record length (4), number of representations (2),
         *  certification flag (1). */
        const val GENERAL_HEADER_BYTES = 15

        /** Representation header without quality and certification blocks; see [IsoRecord19794_2Header.size]. */
        const val REPRESENTATION_HEADER_BYTES = 32

        const val NANOSECONDS_PER_MILLISECOND = 1_000_000

        /** With the certification flag set, every representation lists its certifications. */
        const val CERTIFICATION_FLAG_SET = 1

        /** Certification block: authority id (2) and scheme id (1). */
        const val CERTIFICATION_BLOCK_BYTES = 3

        /** The 2-byte length of the extended data that ends every representation. */
        const val EXTENDED_DATA_LENGTH_BYTES = 2

        /** The byte after the image size: minutia field length in the high 4 bits, ridge ending type in the low 4. */
        const val MINUTIA_LENGTH_SHIFT = 4
        const val RIDGE_ENDING_TYPE_MASK = 0x0F

        /** A minutia is 5 bytes (type and x, y, angle) or 6 bytes with a quality byte. */
        const val MINUTIA_BYTES_WITH_QUALITY = 6

        /** Quality value of a minutia that has no quality byte: "not reported". */
        const val MINUTIA_QUALITY_NOT_REPORTED = 254
    }

    override val formatIdentifier: String get() = ""
    override fun canRead(data: ByteArray): Boolean =
        data.size >= GENERAL_HEADER_BYTES + REPRESENTATION_HEADER_BYTES + EXTENDED_DATA_LENGTH_BYTES &&
                MemoryStream(data).readStringz(FORMAT_IDENTIFIER.length) == FORMAT_IDENTIFIER

    override fun read(data: ByteArray): IsoRecord19794_2 {
        val stream = MemoryStream(data)

        val formatIdentifier = stream.readStringz(4)
        val formatVersion = stream.readStringz(4)
        stream.skip(4) //length of record
        val numberOfFingers = stream.readU16BE()
        val certificationFlag = stream.readU8()
        val blocks = mutableListOf<IsoRecord19794_2DataBlock>()
        (1..numberOfFingers).forEach { _ ->
            stream.skip(4) //representationLength
            val year = stream.readU16BE()
            val month = stream.readU8()
            val day = stream.readU8()
            val hour = stream.readU8()
            val min = stream.readU8()
            val sec = stream.readU8()
            val milliseconds = stream.readU16BE()
            val date = LocalDateTime(year, month, day, hour, min, sec, milliseconds * NANOSECONDS_PER_MILLISECOND)
                .toInstant(TimeZone.UTC)
            val deviceTechnologyId = stream.readU8()
            val deviceVendorId = stream.readU16BE()
            val deviceTypeId = stream.readU16BE()
            val qualityRecordNumber = stream.readU8()
            val qualityRecords = List(qualityRecordNumber) {
                QualityRecord(
                    qualityScore = stream.readU8(),
                    qualityAlgorithmVendorId = stream.readU16BE(),
                    qualityAlgorithmId = stream.readU16BE()
                )
            }
            val certificationRecords = when (certificationFlag) {
                CERTIFICATION_FLAG_SET -> {
                    val certificationRecordNumber = stream.readU8()
                    List(certificationRecordNumber) {
                        CertificationRecord(
                            certificationAuthorityId = stream.readU16BE(),
                            certificationSchemeId = stream.readU8()
                        )
                    }
                }
                else -> listOf()
            }
            val fingerPosition = stream.readU8()
            val representationNumber = stream.readU8()
            val spatialSamplingRateHoriz = stream.readU16BE()
            val spatialSamplingRateVert = stream.readU16BE()
            val impressionType = stream.readU8()
            val width = stream.readU16BE()
            val height = stream.readU16BE()
            val lengthAndRidgeType = stream.readU8()
            val minutiaeLength = lengthAndRidgeType ushr MINUTIA_LENGTH_SHIFT
            val ridgeEndingType = lengthAndRidgeType and RIDGE_ENDING_TYPE_MASK
            val minutiaeNumber = stream.readU8()
            val minutias = mutableListOf<IsoRecord19794_2MinutiaData>()
            (1..minutiaeNumber).forEach { _ ->
                val source = stream.readU16BE()
                val type = getMinutia(MINUTIA_TYPE, source)
                val minutiaX = getMinutia(MINUTIA_POSITION, source)
                val minutiaY = getMinutia(MINUTIA_POSITION, stream.readU16BE())
                val angle = stream.readU8()
                val quality = when (minutiaeLength) {
                    MINUTIA_BYTES_WITH_QUALITY -> stream.readU8()
                    else -> MINUTIA_QUALITY_NOT_REPORTED
                }
                minutias.add(
                    IsoRecord19794_2MinutiaData(
                        type = type,
                        minutiaX = minutiaX,
                        minutiaY = minutiaY,
                        angle = angle,
                        quality = quality
                    )
                )
            }
            val extendedDataLength = stream.readU16BE()
            blocks.add(
                IsoRecord19794_2DataBlock(
                    header = IsoRecord19794_2Header(
                        dateAndTime = date,
                        deviceTechnologyId = deviceTechnologyId,
                        deviceVendorId = deviceVendorId,
                        deviceTypeId = deviceTypeId,
                        qualityRecords = qualityRecords,
                        certificationRecords = certificationRecords,
                        fingerPosition = fingerPosition,
                        representationNumber = representationNumber,
                        spatialSamplingRateHoriz = spatialSamplingRateHoriz,
                        spatialSamplingRateVert = spatialSamplingRateVert,
                        impressionType = impressionType,
                        width = width,
                        height = height,
                        minutiaeLength = minutiaeLength,
                        ridgeEndingType = ridgeEndingType,
                        minutiaeNumber = minutiaeNumber
                    ),
                    data = minutias,
                    extendedData = stream.readBytes(extendedDataLength)
                )
            )
        }

        return IsoRecord19794_2(
            formatIdentifier = formatIdentifier,
            formatVersion = formatVersion,
            numberOfFingers = numberOfFingers,
            certificationFlag = certificationFlag.toByte(),
            data = blocks
        )
    }

    override fun write(record: IsoRecord19794_2): ByteArray = buildBytes {
        //Finger general header
        writeStringz(record.formatIdentifier)
        writeStringz(record.formatVersion)
        write32BE(record.data.sumOf { it.size } + GENERAL_HEADER_BYTES)
        write16BE(record.numberOfFingers)
        write8(record.certificationFlag.toInt())
        record.data.forEach {
            //Finger representation header
            // certification blocks are not part of IsoRecord19794_2DataBlock.size: a count byte plus 3 bytes each
            val certificationRecordsLength = if (record.certificationFlag.toInt() == CERTIFICATION_FLAG_SET) {
                1 + CERTIFICATION_BLOCK_BYTES * it.header.certificationRecords.size
            } else 0
            write32BE(it.size + certificationRecordsLength)
            val date = it.header.dateAndTime.toLocalDateTime(TimeZone.UTC)
            write16BE(date.year)
            write8(date.monthNumber)
            write8(date.dayOfMonth)
            write8(date.hour)
            write8(date.minute)
            write8(date.second)
            write16BE(date.nanosecond / NANOSECONDS_PER_MILLISECOND)
            write8(it.header.deviceTechnologyId)
            write16BE(it.header.deviceVendorId)
            write16BE(it.header.deviceTypeId)
            write8(it.header.qualityRecords.size)
            if (it.header.qualityRecords.isNotEmpty()) {
                it.header.qualityRecords.forEach { block ->
                    // same order as read: score, algorithm vendor, algorithm
                    write8(block.qualityScore)
                    write16BE(block.qualityAlgorithmVendorId)
                    write16BE(block.qualityAlgorithmId)
                }
            }
            if (record.certificationFlag.toInt() == CERTIFICATION_FLAG_SET) {
                write8(it.header.certificationRecords.size)
                it.header.certificationRecords.forEach { block ->
                    write16BE(block.certificationAuthorityId)
                    write8(block.certificationSchemeId)
                }
            }
            write8(it.header.fingerPosition)
            write8(it.header.representationNumber)
            write16BE(it.header.spatialSamplingRateHoriz)
            write16BE(it.header.spatialSamplingRateVert)
            write8(it.header.impressionType)
            write16BE(it.header.width)
            write16BE(it.header.height)
            write8((it.header.minutiaeLength shl MINUTIA_LENGTH_SHIFT) or (it.header.ridgeEndingType and RIDGE_ENDING_TYPE_MASK))
            write8(it.header.minutiaeNumber)
            //minutiaes
            it.data.forEach { minutia ->
                write16BE(setMinutia(minutia.type, minutia.minutiaX))
                write16BE(setMinutia(pos = minutia.minutiaY))
                write8(minutia.angle)
                if (it.header.minutiaeLength == MINUTIA_BYTES_WITH_QUALITY) {
                    write8(minutia.quality) // one byte, as read
                }
            }
            write16BE(it.extendedData.size)
            if (it.extendedData.isNotEmpty()) writeBytes(it.extendedData)
        }
    }
}