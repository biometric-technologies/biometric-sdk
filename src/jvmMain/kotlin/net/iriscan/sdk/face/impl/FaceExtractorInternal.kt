package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.*
import net.iriscan.sdk.core.utils.createImg
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgcodecs.imencode
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Point2f
import org.bytedeco.opencv.opencv_core.RectVector
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.math.atan2

/**
 * @author Slava Gornostal
 */
internal actual class FaceExtractorInternal {

    private val classifier: CascadeClassifier

    init {
        val classifierFile =
            File("${System.getProperty("java.io.tmpdir")}/.biometric-sdk/haarcascade_frontalface_alt.xml")
                .toPath()
        if (!Files.exists(classifierFile)) {
            val classifierXml = javaClass.getResourceAsStream("/haarcascade_frontalface_alt.xml")!!
            Files.createDirectories(classifierFile.parent)
            Files.copy(classifierXml, classifierFile)
        }
        classifier = CascadeClassifier(classifierFile.absolutePathString())
    }

    actual fun extract(image: Image, rotateOnWrongOrientation: Boolean, traceId: String?): Image? {
        val bufferedImage = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val data = image.colors.flatMap { listOf(it.red(), it.green(), it.blue()) }.toIntArray()
        bufferedImage.raster.setPixels(0, 0, image.width, image.height, data)
        val face = extractInternal(bufferedImage, rotateOnWrongOrientation) ?: return null
        return createImg(
            width = face.width,
            height = face.height,
            type = ImageColorType.RGB
        ) { x, y ->
            val rawColor = Color(face.getRGB(x, y))
            createColor(rawColor.red, rawColor.green, rawColor.blue)
        }
    }

    actual fun extract(image: NativeImage, rotateOnWrongOrientation: Boolean, traceId: String?): NativeImage? =
        extractInternal(image, rotateOnWrongOrientation)

    private fun extractInternal(image: BufferedImage, rotateOnWrongOrientation: Boolean): BufferedImage? {
        val data = (image.raster.dataBuffer as DataBufferByte).data
        val input = Mat(image.height, image.width, opencv_core.CV_8UC3, BytePointer(*data))
        val result = RectVector()
        classifier.detectMultiScale(input, result)
        val faces = result.get()
        if (faces.isEmpty()) {
            return null
        }
        val rect = faces[0]
        if (
            !(rect.x() in 1 until input.cols() && (rect.x() + rect.width()) in 1 until input.cols() &&
                    rect.y() in 1 until input.rows() && (rect.y() + rect.height()) in 1 until input.rows())
        ) {
            return null
        }
        val center = Point2f((rect.x() + rect.width() / 2.0).toFloat(), (rect.y() + rect.height() / 2.0).toFloat())
        val angle = atan2(rect.y().toDouble(), (rect.x() + rect.width()).toDouble()) * 180.0 / Math.PI
        val rotationMatrix = opencv_imgproc.getRotationMatrix2D(center, angle, 1.0)
        val rotatedImage = Mat()
        opencv_imgproc.warpAffine(input, rotatedImage, rotationMatrix, input.size())
        val bytes = BytePointer()
        imencode(".jpg", rotatedImage, bytes)
        val byteArray = bytes.asByteBuffer().array()
        return ImageIO.read(ByteArrayInputStream(byteArray))
    }

}