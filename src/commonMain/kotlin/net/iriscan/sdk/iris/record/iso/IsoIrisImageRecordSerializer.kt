package net.iriscan.sdk.iris.record.iso

import net.iriscan.sdk.io.record.BiometricRecordSerializer
import net.iriscan.sdk.core.record.BiometricRecordValidationResult

/**
 * @author Slava Gornostal
 */
class IsoIrisImageRecordSerializer(override val formatIdentifier: String = "IIR") :
    BiometricRecordSerializer<IsoIrisImageRecord> {

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