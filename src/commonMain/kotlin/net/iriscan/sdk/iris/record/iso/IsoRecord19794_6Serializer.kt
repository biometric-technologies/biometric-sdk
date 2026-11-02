package net.iriscan.sdk.iris.record.iso

import net.iriscan.sdk.core.io.MemoryStream
import net.iriscan.sdk.core.io.buildBytes

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import net.iriscan.sdk.io.record.BiometricRecordSerializer
import net.iriscan.sdk.iris.exts.findEnumById

/**
 * Reads and writes iris image records in the ISO/IEC 19794-6:2011 binary format.
 *
 * A record is a 16-byte general header followed by one representation per iris image. Each
 * representation is a header ([REPRESENTATION_HEADER_BYTES] bytes plus 5 bytes per quality block)
 * followed by the image itself (usually PNG or JPEG 2000). All numbers are big-endian.
 *
 * The capture date and time are stored in UTC with millisecond precision.
 *
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
class IsoRecord19794_6Serializer : BiometricRecordSerializer<IsoRecord19794_6> {

    private companion object {
        /** "IIR" and a zero byte. */
        const val FORMAT_IDENTIFIER = "IIR"

        /** Format identifier (4), version (4), record length (4), number of representations (2),
         *  certification flag (1), number of eyes represented (1). */
        const val GENERAL_HEADER_BYTES = 16

        /** Representation header without the quality blocks; see [IsoRecord19794_6Header.size]. */
        const val REPRESENTATION_HEADER_BYTES = 52

        const val NANOSECONDS_PER_MILLISECOND = 1_000_000

        /** Values of the 2-byte range (camera to eye distance) field. */
        const val RANGE_UNASSIGNED = 0
        const val RANGE_FAILED = 1
    }

    override val formatIdentifier: String get() = ""
    override fun canRead(data: ByteArray): Boolean =
        data.size >= GENERAL_HEADER_BYTES + REPRESENTATION_HEADER_BYTES &&
                MemoryStream(data).readStringz(FORMAT_IDENTIFIER.length) == FORMAT_IDENTIFIER

    override fun read(data: ByteArray): IsoRecord19794_6 {
        val stream = MemoryStream(data)

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
            val milliseconds = stream.readU16BE()
            val date = LocalDateTime(year, month, day, hour, min, sec, milliseconds * NANOSECONDS_PER_MILLISECOND)
                .toInstant(TimeZone.UTC)
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
            // the field holds the distance in millimetres for values 2..65534; the Range enum keeps only
            // whether a distance was measured, so any measured distance is read as RANGE_OVERFLOW
            val range = when (stream.readU16BE()) {
                RANGE_UNASSIGNED -> Range.RANGE_UNASSIGNED
                RANGE_FAILED -> Range.RANGE_FAILED
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

    override fun write(record: IsoRecord19794_6): ByteArray = buildBytes {
        //Iris general header
        writeStringz(record.formatIdentifier)
        writeStringz(record.formatVersion)
        write32BE(record.data.sumOf { it.size } + GENERAL_HEADER_BYTES)
        write16BE(record.numberOfIrises)
        write8(record.certificationFlag.toInt())
        write8(record.numberOfEyesRepresented)
        record.data.forEach {
            //Iris representation header
            write32BE(it.size)
            val date = it.header.dateAndTime.toLocalDateTime(TimeZone.UTC)
            write16BE(date.year)
            write8(date.monthNumber)
            write8(date.dayOfMonth)
            write8(date.hour)
            write8(date.minute)
            write8(date.second)
            write16BE(date.nanosecond / NANOSECONDS_PER_MILLISECOND)
            write8(it.header.deviceTechnologyId.value)
            write16BE(it.header.deviceVendorId)
            write16BE(it.header.deviceTypeId)
            write8(it.header.qualityBlocks.size)
            if (it.header.qualityBlocks.isNotEmpty()) {
                it.header.qualityBlocks.forEach { block ->
                    // same order as read: score, algorithm vendor, algorithm
                    write8(block.qualityScore)
                    write16BE(block.qualityAlgorithmVendorId)
                    write16BE(block.qualityAlgorithmId)
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