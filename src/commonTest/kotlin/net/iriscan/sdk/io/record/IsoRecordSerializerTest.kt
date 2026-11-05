package net.iriscan.sdk.io.record

import kotlinx.datetime.Instant
import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.core.record.BiometricType
import net.iriscan.sdk.finger.record.iso.CertificationRecord
import net.iriscan.sdk.finger.record.iso.IsoRecord19794_2
import net.iriscan.sdk.finger.record.iso.IsoRecord19794_2DataBlock
import net.iriscan.sdk.finger.record.iso.IsoRecord19794_2Header
import net.iriscan.sdk.finger.record.iso.IsoRecord19794_2MinutiaData
import net.iriscan.sdk.finger.record.iso.IsoRecord19794_2Serializer
import net.iriscan.sdk.finger.record.iso.MINUTIA_POSITION
import net.iriscan.sdk.finger.record.iso.MINUTIA_TYPE
import net.iriscan.sdk.finger.record.iso.QualityRecord
import net.iriscan.sdk.finger.record.iso.getMinutia
import net.iriscan.sdk.finger.record.iso.setMinutia
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.exception.UnknownFormatException
import net.iriscan.sdk.io.image.ImageFixtures
import net.iriscan.sdk.io.record.impl.InputOutputRecordOperationsImpl
import net.iriscan.sdk.iris.record.iso.CompressionHistory
import net.iriscan.sdk.iris.record.iso.DeviceTechnology
import net.iriscan.sdk.iris.record.iso.EyeLabel
import net.iriscan.sdk.iris.record.iso.HorizontalOrientation
import net.iriscan.sdk.iris.record.iso.ImageFormat
import net.iriscan.sdk.iris.record.iso.ImageType
import net.iriscan.sdk.iris.record.iso.IsoRecord19794_6
import net.iriscan.sdk.iris.record.iso.IsoRecord19794_6DataBlock
import net.iriscan.sdk.iris.record.iso.IsoRecord19794_6Header
import net.iriscan.sdk.iris.record.iso.IsoRecord19794_6Serializer
import net.iriscan.sdk.iris.record.iso.Properties
import net.iriscan.sdk.iris.record.iso.QualityBlock
import net.iriscan.sdk.iris.record.iso.Range
import net.iriscan.sdk.iris.record.iso.VerticalOrientation
import net.iriscan.sdk.iris.record.iso.getProperty
import net.iriscan.sdk.iris.record.iso.setProperty
import net.iriscan.sdk.testutil.hex
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IsoRecordSerializerTest {

    private val captured = Instant.parse("2026-10-31T12:34:56.789Z")

    private fun u16(bytes: ByteArray, at: Int) = ((bytes[at].toInt() and 0xFF) shl 8) or (bytes[at + 1].toInt() and 0xFF)
    private fun u32(bytes: ByteArray, at: Int) = (u16(bytes, at) shl 16) or u16(bytes, at + 2)

    // ---- ISO/IEC 19794-6 iris image records

    private fun irisHeader(eye: EyeLabel, quality: List<QualityBlock>) = IsoRecord19794_6Header(
        dateAndTime = captured,
        deviceTechnologyId = DeviceTechnology.CMOS_CCD,
        deviceVendorId = 0x1234,
        deviceTypeId = 0x5678,
        qualityBlocks = quality,
        representationNumber = 1,
        eyeLabel = eye,
        imageType = ImageType.IMAGE_TYPE_CROPPED,
        imageFormat = ImageFormat.IMAGEFORMAT_MONO_PNG,
        horizontalOrientation = HorizontalOrientation.HORZ_ORIENTATION_FLIPPED,
        verticalOrientation = VerticalOrientation.VERT_ORIENTATION_BASE,
        compressionHistory = CompressionHistory.PREVIOUS_COMPRESSION_LOSSLESS_OR_NONE,
        width = 640,
        height = 480,
        bitDepth = 8,
        range = Range.RANGE_UNASSIGNED,
        rollAngleOfEye = 0xFFFF,
        rollAngleUncertainty = 0xFFFF,
        smX = 300, lgX = 340, smY = 220, lgY = 260, smDiameter = 200, lgDiameter = 220,
    )

    private val irisRecord = IsoRecord19794_6(
        formatIdentifier = "IIR",
        formatVersion = "020",
        certificationFlag = 0,
        numberOfIrises = 2,
        numberOfEyesRepresented = 2,
        data = listOf(
            IsoRecord19794_6DataBlock(irisHeader(EyeLabel.SUBJECT_EYE_LABEL_RIGHT, listOf(QualityBlock(87, 0x0101, 0x0202))), ByteArray(100) { it.toByte() }),
            IsoRecord19794_6DataBlock(irisHeader(EyeLabel.SUBJECT_EYE_LABEL_LEFT, emptyList()), ByteArray(3) { 7 }),
        ),
    )

    @Test
    fun irisRecordRoundTrip() {
        val serializer = IsoRecord19794_6Serializer()
        val bytes = serializer.write(irisRecord)
        assertTrue(serializer.canRead(bytes))
        val back = serializer.read(bytes)
        assertEquals(irisRecord.copy(data = emptyList()), back.copy(data = emptyList()))
        assertEquals(irisRecord.data.size, back.data.size)
        for ((expected, actual) in irisRecord.data.zip(back.data)) {
            assertEquals(expected.header, actual.header)
            assertContentEquals(expected.image, actual.image)
        }
    }

    @Test
    fun irisRecordLayoutFollowsTheStandard() {
        val bytes = IsoRecord19794_6Serializer().write(irisRecord)
        assertEquals("IIR", bytes.copyOf(3).decodeToString())
        assertEquals(0, bytes[3].toInt(), "format identifier is zero-terminated")
        assertEquals(bytes.size, u32(bytes, 8), "record length")
        // first representation starts after the 16-byte general header
        assertEquals(irisRecord.data[0].size, u32(bytes, 16), "representation length")
        // capture time in UTC: 2026-10-31 12:34:56.789
        assertEquals(2026, u16(bytes, 20))
        assertContentEquals(byteArrayOf(10, 31, 12, 34, 56), bytes.copyOfRange(22, 27))
        assertEquals(789, u16(bytes, 27), "milliseconds")
        // quality block: score, algorithm vendor id, algorithm id
        assertEquals(1, bytes[34].toInt(), "number of quality blocks")
        assertEquals(87, bytes[35].toInt())
        assertEquals(0x0101, u16(bytes, 36), "quality algorithm vendor id")
        assertEquals(0x0202, u16(bytes, 38), "quality algorithm id")
    }

    @Test
    fun imagePropertiesBitFields() {
        // bits 0-1 horizontal, 2-3 vertical, 6-7 compression history, 4-5 reserved
        assertEquals(0b10_00_01_10, setProperty(hor = 2, ver = 1, com = 2))
        for (hor in 0..3) for (ver in 0..3) for (com in 0..3) {
            val packed = setProperty(hor, ver, com)
            assertEquals(0, packed and 0b0011_0000, "reserved bits stay 0")
            assertEquals(hor, getProperty(packed, Properties.HORIZONTAL_ORIENTATION))
            assertEquals(ver, getProperty(packed, Properties.VERTICAL_ORIENTATION))
            assertEquals(com, getProperty(packed, Properties.COMPRESSION_HISTORY))
        }
        assertEquals(1, getProperty(0b1111_0101, Properties.HORIZONTAL_ORIENTATION))
    }

    // ---- ISO/IEC 19794-2 finger minutiae records

    private fun fingerBlock(minutiaLength: Int, minutiae: List<IsoRecord19794_2MinutiaData>, extended: ByteArray) =
        IsoRecord19794_2DataBlock(
            header = IsoRecord19794_2Header(
                dateAndTime = captured,
                deviceTechnologyId = 1,
                deviceVendorId = 0x0A0B,
                deviceTypeId = 0x0C0D,
                qualityRecords = listOf(QualityRecord(60, 0x0101, 0x0202)),
                certificationRecords = listOf(),
                fingerPosition = 2,
                representationNumber = 0,
                spatialSamplingRateHoriz = 197,
                spatialSamplingRateVert = 197,
                impressionType = 0,
                width = 500,
                height = 550,
                minutiaeLength = minutiaLength,
                ridgeEndingType = 1,
                minutiaeNumber = minutiae.size,
            ),
            data = minutiae,
            extendedData = extended,
        )

    private val minutiae = listOf(
        IsoRecord19794_2MinutiaData(type = 1, minutiaX = 100, minutiaY = 200, angle = 45, quality = 80),
        IsoRecord19794_2MinutiaData(type = 2, minutiaX = 16383, minutiaY = 0, angle = 255, quality = 100),
    )

    @Test
    fun fingerRecordRoundTripWithMinutiaQuality() {
        val record = IsoRecord19794_2(
            formatIdentifier = "FMR", formatVersion = "030", certificationFlag = 0, numberOfFingers = 2,
            data = listOf(fingerBlock(6, minutiae, ByteArray(0)), fingerBlock(6, minutiae.take(1), byteArrayOf(1, 2, 3))),
        )
        val serializer = IsoRecord19794_2Serializer()
        val bytes = serializer.write(record)
        assertTrue(serializer.canRead(bytes))
        assertEquals(bytes.size, u32(bytes, 8), "record length")
        val back = serializer.read(bytes)
        assertEquals(record.copy(data = emptyList()), back.copy(data = emptyList()))
        for ((expected, actual) in record.data.zip(back.data)) {
            assertEquals(expected.header, actual.header)
            assertEquals(expected.data, actual.data)
            assertContentEquals(expected.extendedData, actual.extendedData)
        }
    }

    @Test
    fun fingerMinutiaeWithoutQualityReadAsNotReported() {
        val record = IsoRecord19794_2(
            formatIdentifier = "FMR", formatVersion = "030", certificationFlag = 0, numberOfFingers = 1,
            data = listOf(fingerBlock(5, minutiae, ByteArray(0))),
        )
        val back = IsoRecord19794_2Serializer().read(IsoRecord19794_2Serializer().write(record))
        assertEquals(minutiae.map { it.copy(quality = 254) }, back.data.single().data)
    }

    @Test
    fun fingerRecordWithCertifications() {
        val withCertification = fingerBlock(6, minutiae, ByteArray(0)).let {
            it.copy(header = it.header.copy(certificationRecords = listOf(CertificationRecord(0x0102, 3))))
        }
        val record = IsoRecord19794_2(
            formatIdentifier = "FMR", formatVersion = "030", certificationFlag = 1, numberOfFingers = 1,
            data = listOf(withCertification),
        )
        val bytes = IsoRecord19794_2Serializer().write(record)
        assertEquals(bytes.size, u32(bytes, 8), "record length")
        assertEquals(bytes.size - 15, u32(bytes, 15), "representation length includes the certification block")
        val back = IsoRecord19794_2Serializer().read(bytes)
        assertEquals(withCertification.header, back.data.single().header)
    }

    @Test
    fun minutiaWordBitFields() {
        // X word: type in bits 14-15, x in bits 0-13; Y word: reserved bits 14-15, y in bits 0-13
        assertEquals(0b10_00000001100100, setMinutia(2, 100))
        assertEquals(2, getMinutia(MINUTIA_TYPE, setMinutia(2, 100)))
        assertEquals(100, getMinutia(MINUTIA_POSITION, setMinutia(2, 100)))
        assertEquals(16383, getMinutia(MINUTIA_POSITION, 0xFFFF))
        assertEquals(0, getMinutia(3, 0xFFFF))
    }

    @Test
    fun recognisesOnlyItsOwnFormat() {
        val iris = IsoRecord19794_6Serializer().write(irisRecord)
        assertFalse(IsoRecord19794_2Serializer().canRead(iris))
        assertFalse(IsoRecord19794_6Serializer().canRead(ByteArray(10)))
    }

    // ---- io().readRecord and io().writeRecord

    private val io = InputOutputRecordOperationsImpl()

    /**
     * The right eye of [irisRecord], with 4 placeholder image bytes, as a record of its own. Written
     * out field by field from the 2011 layout, not by the serializer.
     */
    private val irisReference = hex(
        "49495200 30323000 0000004D 0001 00 01" + // "IIR", version "020", 77 bytes, 1 representation, not certified, 1 eye
            "0000003D 07EA 0A 1F 0C 22 38 0315" + // representation of 61 bytes, captured 2026-10-31 12:34:56.789 UTC
            "01 1234 5678 01 57 0101 0202" + // CMOS/CCD, vendor, device type; 1 quality block: score 87, algorithm vendor, algorithm
            "0001 01 03 0E 46" + // representation 1, right eye, cropped, PNG; flipped horizontally, base vertically, lossless
            "0280 01E0 08 0000 FFFF FFFF" + // 640 x 480, 8 bits, range unassigned, roll angle and its uncertainty undefined
            "012C 0154 00DC 0104 00C8 00DC" + // iris centre x 300..340, y 220..260, diameter 200..220
            "00000004 01020304", // image length, image
    )

    /** One finger with the two [minutiae], written out field by field from the 2011 layout. */
    private val fingerReference = hex(
        "464D5200 30333000 00000042 0001 00" + // "FMR", version "030", 66 bytes, 1 representation, not certified
            "00000033 07EA 0A 1F 0C 22 38 0315" + // representation of 51 bytes, captured 2026-10-31 12:34:56.789 UTC
            "01 0A0B 0C0D 01 3C 0101 0202" + // device technology, vendor, device type; 1 quality block: score 60, algorithm vendor, algorithm
            "02 00 00C5 00C5 00 01F4 0226" + // finger 2, representation 0, 197 x 197 pixels per cm, impression 0, 500 x 550
            "61 02" + // minutiae of 6 bytes (with quality), ridge ending type 1; 2 minutiae
            "4064 00C8 2D 50" + // type 1 at x 100, y 200, angle 45, quality 80
            "BFFF 0000 FF 64" + // type 2 at x 16383, y 0, angle 255, quality 100
            "0000", // no extended data
    )

    @Test
    fun readRecordReadsIrisImageRecords() {
        val record = assertIs<IsoRecord19794_6>(io.readRecord(irisReference))
        assertEquals("IIR", record.formatIdentifier)
        assertEquals("020", record.formatVersion)
        val image = record.data.single()
        assertEquals(irisHeader(EyeLabel.SUBJECT_EYE_LABEL_RIGHT, listOf(QualityBlock(87, 0x0101, 0x0202))), image.header)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), image.image)
    }

    @Test
    fun readRecordReadsFingerMinutiaeRecords() {
        val record = assertIs<IsoRecord19794_2>(io.readRecord(fingerReference))
        assertEquals("FMR", record.formatIdentifier)
        assertEquals("030", record.formatVersion)
        val finger = record.data.single()
        assertEquals(fingerBlock(6, minutiae, ByteArray(0)).header, finger.header)
        assertEquals(minutiae, finger.data)
        assertContentEquals(ByteArray(0), finger.extendedData)
    }

    @Test
    fun writeRecordGivesBackTheBytesThatWereRead() {
        assertContentEquals(irisReference, io.writeRecord(io.readRecord(irisReference)))
        assertContentEquals(fingerReference, io.writeRecord(io.readRecord(fingerReference)))
    }

    @Test
    fun readRecordRejectsDataOfOtherFormats() {
        assertFailsWith<UnknownFormatException> { io.readRecord(ByteArray(0)) }
        assertFailsWith<UnknownFormatException> { io.readRecord(hex(ImageFixtures.PNG_GRAY)) }
    }

    @Test
    fun readRecordReportsARecordThatIsCutShort() {
        assertFailsWith<IOException> { io.readRecord(irisReference.copyOf(irisReference.size - 1)) }
        assertFailsWith<IOException> { io.readRecord(fingerReference.copyOf(fingerReference.size - 1)) }
    }

    @Test
    fun writeRecordRejectsOtherRecords() {
        val appRecord = object : BiometricRecord {
            override val formatIdentifier = "IIR" // the iris identifier on a class the SDK does not know
            override val formatVersion = "020"
            override val type = BiometricType.IRIS
            override val certificationFlag: Byte = 0
        }
        assertFailsWith<UnknownFormatException> { io.writeRecord(appRecord) }
        // the serializer would write an empty signature, which readRecord does not recognize
        assertFailsWith<UnknownFormatException> { io.writeRecord(irisRecord.copy(formatIdentifier = "")) }
    }

    @Test
    fun serializersNameTheFormatIdentifierOfTheirRecords() {
        assertEquals("IIR", IsoRecord19794_6Serializer().formatIdentifier)
        assertEquals("FMR", IsoRecord19794_2Serializer().formatIdentifier)
    }
}
