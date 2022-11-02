package net.iriscan.sdk.iris.record.iso

import net.iriscan.sdk.core.record.BiometricRecordValidationResult
import net.iriscan.sdk.io.record.BiometricRecordSerializer

/**
 * @author Slava Gornostal
 */
class IsoIrisImageRecordSerializer : BiometricRecordSerializer<IsoIrisImageRecord> {
    override fun canRead(data: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun read(data: ByteArray): IsoIrisImageRecord {
        TODO("Not yet implemented")
    }

    override fun validate(data: ByteArray): BiometricRecordValidationResult {
        TODO("Not yet implemented")
    }

    override fun write(record: IsoIrisImageRecord): ByteArray {
        TODO("Not yet implemented")
    }
}