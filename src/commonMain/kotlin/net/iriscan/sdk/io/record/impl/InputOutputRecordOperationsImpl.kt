package net.iriscan.sdk.io.record.impl

import com.soywiz.korio.async.runBlockingNoJs
import com.soywiz.korio.file.VfsOpenMode
import com.soywiz.korio.file.std.localVfs
import com.soywiz.korio.stream.readAll
import net.iriscan.sdk.core.record.BiometricRecord
import net.iriscan.sdk.io.InputOutputRecordOperations
import net.iriscan.sdk.io.exception.IOException
import net.iriscan.sdk.io.exception.UnknownFormatException
import net.iriscan.sdk.io.record.BiometricRecordSerializer

/**
 * @author Slava Gornostal
 */
internal class InputOutputRecordOperationsImpl : InputOutputRecordOperations {

    private val serializers = listOf<BiometricRecordSerializer<BiometricRecord>>()

    override fun readRecord(data: ByteArray): BiometricRecord {
        val serializer = serializers.firstOrNull { it.canRead(data) }
            ?: throw UnknownFormatException("Unknown biometric format")
        return serializer.read(data)
    }

    override fun readRecord(filePath: String): BiometricRecord {
        val bytes = runBlockingNoJs {
            try {
                localVfs(filePath).open().readAll()
            } catch (th: Throwable) {
                throw IOException("Could not read file $filePath", th)
            }
        }
        return readRecord(bytes)
    }

    override fun writeRecord(filePath: String, record: BiometricRecord) {
        val bytes = writeAsByteArrayRecord(record)
        runBlockingNoJs {
            localVfs(filePath).open(VfsOpenMode.WRITE).write(bytes)
        }
    }

    override fun writeAsByteArrayRecord(record: BiometricRecord): ByteArray {
        val serializer = serializers.firstOrNull { it.formatIdentifier == record.formatIdentifier }
            ?: throw UnknownFormatException("Unsupported biometric format")
        return serializer.write(record)
    }
}