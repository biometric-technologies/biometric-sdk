package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.*
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Rect
import org.bytedeco.opencv.opencv_core.RectVector
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
import java.awt.image.DataBufferByte
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.io.path.absolutePathString

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

    actual fun extract(image: Image): Image? {
        val pixels = image.colors.flatMap {
            ByteBuffer.allocate(3).put(byteArrayOf(it.blue().toByte(), it.green().toByte(), it.red().toByte()))
                .array()
                .toList()
        }
            .toByteArray()
        val mat = Mat(image.height, image.width, opencv_core.CV_8UC3, BytePointer(*pixels))
        val faceRect = extractInternal(mat) ?: return null
        return image[faceRect.x()..faceRect.x() + faceRect.width(), faceRect.y()..faceRect.y() + faceRect.height()]
    }

    actual fun extract(image: NativeImage): NativeImage? {
        val data = (image.raster.dataBuffer as DataBufferByte).data
        val mat = Mat(image.height, image.width, opencv_core.CV_8UC3, BytePointer(*data))
        val faceRect = extractInternal(mat) ?: return null
        return image.getSubimage(faceRect.x(), faceRect.y(), faceRect.width(), faceRect.height())
    }

    private fun extractInternal(input: Mat): Rect? {
        val result = RectVector()
        classifier.detectMultiScale(input, result)
        val faces = result.get()
        if (faces.isEmpty()) {
            return null
        }
        val rect = faces[0]
        if (rect.x() in 1 until input.cols() && (rect.x() + rect.width()) in 1 until input.cols() &&
            rect.y() in 1 until input.rows() && (rect.y() + rect.height()) in 1 until input.rows()
        ) {
            return rect
        }
        return null
    }

}