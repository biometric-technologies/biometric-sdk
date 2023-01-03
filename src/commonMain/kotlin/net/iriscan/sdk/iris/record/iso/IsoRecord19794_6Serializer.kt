package net.iriscan.sdk.iris.record.iso

import com.soywiz.korio.stream.*
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.iriscan.sdk.io.record.BiometricRecordSerializer
import net.iriscan.sdk.iris.exts.findEnumById

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
class IsoRecord19794_6Serializer : BiometricRecordSerializer<IsoRecord19794_6> {
    override val formatIdentifier: String get() = ""
    override fun canRead(data: ByteArray): Boolean =
        data.size > 68 && MemorySyncStream(data).readStringz(3) == "IIR"

    override fun read(data: ByteArray): IsoRecord19794_6 {
        val stream = MemorySyncStream(data)

        val formatIdentifier = stream.readStringz(4)
        val formatVersion = stream.readStringz(4)
        stream.skip(4) //length of record
        val numberOfIrises = stream.readU16BE()
        val certificationFlag = stream.readU8()
        val numberOfEyesRepresented = stream.readU8()
        val blocks = mutableListOf<IsoRecord19794_6DataBlock>()
        (1..numberOfIrises).forEach { _ ->
            stream.skip(4) //representation length
            val year = stream.readU16BE()
            val month = stream.readU8()
            val day = stream.readU8()
            val hour = stream.readU8()
            val min = stream.readU8()
            val sec = stream.readU8()
            val mlsec = stream.readU16BE()
            val date = LocalDateTime(year, month, day, hour, min, sec, mlsec).toInstant(TimeZone.UTC)
            val deviceTechnologyId = findEnumById<DeviceTechnology>(stream.readU8())
            val deviceVendorId = stream.readU16BE()
            val deviceTypeId = stream.readU16BE()
            val qualityBlockNumber = stream.readU8()
            val qualityBlocks = mutableListOf<QualityBlock>()
            if (qualityBlockNumber > 0) {
                (1..qualityBlockNumber).forEach { _ ->
                    qualityBlocks.add(
                        QualityBlock(
                            qualityScore = stream.readU8(),
                            qualityAlgorithmVendorId = stream.readU16BE(),
                            qualityAlgorithmId = stream.readU16BE()
                        )
                    )
                }
            }
            val representationNumber = stream.readU16BE()
            val eyeLabel = findEnumById<EyeLabel>(stream.readU8())
            val imageType = findEnumById<ImageType>(stream.readU8())
            val imageFormat = findEnumById<ImageFormat>(stream.readU8())
            val property = stream.readU8()
            val horizontalOrientation =
                findEnumById<HorizontalOrientation>(getProperty(property, Properties.HORIZONTAL_ORIENTATION))
            val verticalOrientation =
                findEnumById<VerticalOrientation>(getProperty(property, Properties.VERTICAL_ORIENTATION))
            val compressionHistory =
                findEnumById<CompressionHistory>(getProperty(property, Properties.COMPRESSION_HISTORY))
            val width = stream.readU16BE()
            val height = stream.readU16BE()
            val bitDepth = stream.readU8()
            val range = when (stream.readU16BE()) {
                0 -> Range.RANGE_UNASSIGNED
                1 -> Range.RANGE_FAILED
                else -> Range.RANGE_OVERFLOW
            }
            val rollAngleOfEye = stream.readU16BE()
            val rollAngleUncertainty = stream.readU16BE()
            val smX = stream.readU16BE()
            val lgX = stream.readU16BE()
            val smY = stream.readU16BE()
            val lgY = stream.readU16BE()
            val smDiameter = stream.readU16BE()
            val lgDiameter = stream.readU16BE()
            val imageLength = stream.readU32BE()
            val imageData = stream.readBytes(imageLength.toInt())
            blocks.add(
                IsoRecord19794_6DataBlock(
                    header = IsoRecord19794_6Header(
                        dateAndTime = date,
                        deviceTechnologyId = deviceTechnologyId,
                        deviceVendorId = deviceVendorId,
                        deviceTypeId = deviceTypeId,
                        qualityBlocks = qualityBlocks,
                        representationNumber = representationNumber,
                        eyeLabel = eyeLabel,
                        imageType = imageType,
                        imageFormat = imageFormat,
                        horizontalOrientation = horizontalOrientation,
                        verticalOrientation = verticalOrientation,
                        compressionHistory = compressionHistory,
                        width = width,
                        height = height,
                        bitDepth = bitDepth,
                        range = range,
                        rollAngleOfEye = rollAngleOfEye,
                        rollAngleUncertainty = rollAngleUncertainty,
                        smX = smX,
                        lgX = lgX,
                        smY = smY,
                        lgY = lgY,
                        smDiameter = smDiameter,
                        lgDiameter = lgDiameter,
                    ),
                    image = imageData
                )
            )
        }

        return IsoRecord19794_6(
            formatIdentifier = formatIdentifier,
            formatVersion = formatVersion,
            numberOfIrises = numberOfIrises,
            certificationFlag = certificationFlag.toByte(),
            numberOfEyesRepresented = numberOfEyesRepresented,
            data = blocks
        )
    }

    override fun write(record: IsoRecord19794_6): ByteArray = MemorySyncStreamToByteArray {
        //Iris general header
        writeStringz(record.formatIdentifier)
        writeStringz(record.formatVersion)
        write32BE(record.data.sumOf { it.size } + 16)
        write16BE(record.numberOfIrises)
        write8(record.certificationFlag.toInt())
        write8(record.numberOfEyesRepresented)
        record.data.forEach {
            //Iris representation header
            write32BE(it.size)
            val date = it.header.dateAndTime.toLocalDateTime(TimeZone.currentSystemDefault())
            write16BE(date.year)
            write8(date.monthNumber)
            write8(date.dayOfMonth)
            write8(date.hour)
            write8(date.minute)
            write8(date.second)
            write16BE(date.nanosecond)
            write8(it.header.deviceTechnologyId.value)
            write16BE(it.header.deviceVendorId)
            write16BE(it.header.deviceTypeId)
            write8(it.header.qualityBlocks.size)
            if (it.header.qualityBlocks.isNotEmpty()) {
                it.header.qualityBlocks.forEach { block ->
                    write8(block.qualityScore)
                    write16BE(block.qualityAlgorithmId)
                    write16BE(block.qualityAlgorithmVendorId)
                }
            }
            write16BE(it.header.representationNumber)
            write8(it.header.eyeLabel.value)
            write8(it.header.imageType.value)
            write8(it.header.imageFormat.value)
            write8(
                setProperty(
                    hor = it.header.horizontalOrientation.value,
                    ver = it.header.verticalOrientation.value,
                    com = it.header.compressionHistory.value
                )
            )
            write16BE(it.header.width)
            write16BE(it.header.height)
            write8(it.header.bitDepth)
            write16BE(it.header.range.value)
            write16BE(it.header.rollAngleOfEye)
            write16BE(it.header.rollAngleUncertainty)
            write16BE(it.header.smX)
            write16BE(it.header.lgX)
            write16BE(it.header.smY)
            write16BE(it.header.lgY)
            write16BE(it.header.smDiameter)
            write16BE(it.header.lgDiameter)
            write32BE(it.image.size)
            //image data
            writeBytes(it.image)
        }
    }
}