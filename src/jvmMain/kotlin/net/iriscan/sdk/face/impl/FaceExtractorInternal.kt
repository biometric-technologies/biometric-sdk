package net.iriscan.sdk.face.impl

import net.iriscan.sdk.core.image.Image
import net.iriscan.sdk.core.image.blue
import net.iriscan.sdk.core.image.green
import net.iriscan.sdk.core.image.red
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.RectVector
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier
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
            Files.createFile(classifierFile)
            Files.copy(classifierXml, classifierFile)
        }
        classifier = CascadeClassifier(classifierFile.absolutePathString())
    }

    actual fun extract(image: Image): Image {
        val pixels = image.colors.flatMap {
            ByteBuffer.allocate(3).put(byteArrayOf(it.blue().toByte(), it.green().toByte(), it.red().toByte()))
                .array()
                .toList()
        }
            .toByteArray()
        val mat = Mat(image.height, image.width, opencv_core.CV_8UC3, BytePointer(*pixels))
        val result = RectVector()
        classifier.detectMultiScale(mat, result)
        val faces = result.get()
        if (faces.isEmpty()) {
            return image
        }
        val faceRect = faces[0]
        return image[faceRect.x()..faceRect.x() + faceRect.width(), faceRect.y()..faceRect.y() + faceRect.height()]
    }
}