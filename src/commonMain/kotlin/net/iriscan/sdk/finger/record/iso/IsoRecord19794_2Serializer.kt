package net.iriscan.sdk.finger.record.iso

import com.soywiz.korio.stream.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.iriscan.sdk.io.record.BiometricRecordSerializer

/**
 * @author Anton Kurinnoy
 */
class IsoRecord19794_2Serializer : BiometricRecordSerializer<IsoRecord19794_2> {
    override val formatIdentifier: String get() = ""
    override fun canRead(data: ByteArray): Boolean =
        data.size > 53 && MemorySyncStream(data).readStringz(3) == "FMR"

    override fun read(data: ByteArray): IsoRecord19794_2 {
        val stream = MemorySyncStream(data)

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
            val mlsec = stream.readU16BE()
            val date = LocalDateTime(year, month, day, hour, min, sec, mlsec).toInstant(TimeZone.UTC)
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
                1 -> {
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
            val tmp = stream.readU8()
            val minutiaeLength = tmp shr 4
            val ridgeEndingType = tmp and 11
            val minutiaeNumber = stream.readU8()
            val minutias = mutableListOf<IsoRecord19794_2MinutiaData>()
            (1..minutiaeNumber).forEach { _ ->
                val source = stream.readU16BE()
                val type = getMinutia(1, source)
                val minutiaX = getMinutia(2, source)
                val minutiaY = getMinutia(2, stream.readU16BE())
                val angle = stream.readU8()
                val quality = when (minutiaeLength) {
                    6 -> stream.readU8()
                    else -> 254 // 254 - quality score is not reported, 255 - failure to compute quality
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

    override fun write(record: IsoRecord19794_2): ByteArray = MemorySyncStreamToByteArray {
        //Finger general header
        writeStringz(record.formatIdentifier)
        writeStringz(record.formatVersion)
        write32BE(record.data.sumOf { it.size } + 15)
        write16BE(record.numberOfFingers)
        write8(record.certificationFlag.toInt())
        record.data.forEach {
            //Finger representation header
            val certificationRecordsLength = record.certificationFlag * (5 * it.header.certificationRecords.size + 1)
            write32BE(it.size + certificationRecordsLength)
            val date = it.header.dateAndTime.toLocalDateTime(TimeZone.currentSystemDefault())
            write16BE(date.year)
            write8(date.monthNumber)
            write8(date.dayOfMonth)
            write8(date.hour)
            write8(date.minute)
            write8(date.second)
            write16BE(date.nanosecond)
            write8(it.header.deviceTechnologyId)
            write16BE(it.header.deviceVendorId)
            write16BE(it.header.deviceTypeId)
            write8(it.header.qualityRecords.size)
            if (it.header.qualityRecords.isNotEmpty()) {
                it.header.qualityRecords.forEach { block ->
                    write8(block.qualityScore)
                    write16BE(block.qualityAlgorithmId)
                    write16BE(block.qualityAlgorithmVendorId)
                }
            }
            if (record.certificationFlag.toInt() == 1) {
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
            write8((it.header.minutiaeLength shl 4 or it.header.ridgeEndingType))
            write8(it.header.minutiaeNumber)
            //minutiaes
            it.data.forEach { minutia ->
                write16BE(setMinutia(minutia.type, minutia.minutiaX))
                write16BE(setMinutia(pos = minutia.minutiaY))
                write8(minutia.angle)
                if (it.header.minutiaeLength == 6) {
                    write16BE(minutia.quality)
                }
            }
            write16BE(it.extendedData.size)
            if (it.extendedData.isNotEmpty()) writeBytes(it.extendedData)
        }
    }
}