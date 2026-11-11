package net.iriscan.sdk.iris.record.iso

import kotlinx.datetime.Instant
import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.core.record.BiometricType
import net.iriscan.sdk.iris.exts.Identifiable

/**
 * @author Slava Gornostal
 * @author Anton Kurinnoy
 */
data class IsoRecord19794_6(
    override val formatIdentifier: String,
    override val formatVersion: String,
    override val type: BiometricType = BiometricType.IRIS,
    override val certificationFlag: Byte,
    val numberOfIrises: Int,
    val numberOfEyesRepresented: Int,
    val data: List<IsoRecord19794_6DataBlock>
) : BiometricRecord

data class IsoRecord19794_6DataBlock(val header: IsoRecord19794_6Header, val image: ByteArray) {
    val size = header.size + image.size
}

data class IsoRecord19794_6Header(
    val dateAndTime: Instant,
    val deviceTechnologyId: DeviceTechnology,
    val deviceVendorId: Int,
    val deviceTypeId: Int,
    val qualityBlocks: List<QualityBlock>,
    val representationNumber: Int,
    val eyeLabel: EyeLabel,
    val imageType: ImageType,
    val imageFormat: ImageFormat,
    val horizontalOrientation: HorizontalOrientation,
    val verticalOrientation: VerticalOrientation,
    val compressionHistory: CompressionHistory,
    val width: Int,
    val height: Int,
    val bitDepth: Int,
    val range: Range,
    val rollAngleOfEye: Int,
    val rollAngleUncertainty: Int,
    val smX: Int,
    val lgX: Int,
    val smY: Int,
    val lgY: Int,
    val smDiameter: Int,
    val lgDiameter: Int,
) {
    /**
     * Bytes this header takes in a record: 51 bytes of fixed fields (representation length 4,
     * capture date and time 9, device technology 1, vendor 2, type 2, representation number 2,
     * eye label 1, image type 1, image format 1, properties 1, width 2, height 2, bit depth 1,
     * range 2, roll angle 2, roll uncertainty 2, iris center and diameter 12, image length 4),
     * 1 byte for the number of quality blocks and [QUALITY_BLOCK_BYTES] per quality block.
     */
    val size = qualityBlocks.size * QUALITY_BLOCK_BYTES + 1 + 51

    companion object {
        /** Quality score (1), quality algorithm vendor id (2), quality algorithm id (2). */
        const val QUALITY_BLOCK_BYTES = 5
    }
}

enum class DeviceTechnology(override val value: Int) : Identifiable { UNKNOWN(0), CMOS_CCD(1); }

data class QualityBlock(val qualityScore: Int, val qualityAlgorithmVendorId: Int, val qualityAlgorithmId: Int)

enum class EyeLabel(override val value: Int) : Identifiable {
    SUBJECT_EYE_LABEL_UNDEF(0),
    SUBJECT_EYE_LABEL_RIGHT(1),
    SUBJECT_EYE_LABEL_LEFT(2);
}

enum class ImageType(override val value: Int) : Identifiable {
    IMAGE_TYPE_UNCROPPED(1),
    IMAGE_TYPE_VGA(2),
    IMAGE_TYPE_CROPPED(3),
    IMAGE_TYPE_CROPPED_AND_MASKED(7);
}

enum class ImageFormat(override val value: Int) : Identifiable {
    IMAGEFORMAT_MONO_RAW(2),
    IMAGEFORMAT_MONO_JPEG2000(10),
    IMAGEFORMAT_MONO_PNG(14);
}

enum class Properties {
    HORIZONTAL_ORIENTATION,
    VERTICAL_ORIENTATION,
    COMPRESSION_HISTORY
}

enum class HorizontalOrientation(override val value: Int) : Identifiable {
    ORIENTATION_UNDEF(0),
    HORZ_ORIENTATION_BASE(1),
    HORZ_ORIENTATION_FLIPPED(2)
}

enum class VerticalOrientation(override val value: Int) : Identifiable {
    ORIENTATION_UNDEF(0),
    VERT_ORIENTATION_BASE(1),
    VERT_ORIENTATION_FLIPPED(2)
}

enum class CompressionHistory(override val value: Int) : Identifiable {
    PREVIOUS_COMPRESSION_UNDEF(0),
    PREVIOUS_COMPRESSION_LOSSLESS_OR_NONE(1),
    PREVIOUS_COMPRESSION_LOSSY(2)
}

enum class Range(override val value: Int) : Identifiable {
    RANGE_UNASSIGNED(0),
    RANGE_FAILED(1),
    RANGE_OVERFLOW(65534);
}