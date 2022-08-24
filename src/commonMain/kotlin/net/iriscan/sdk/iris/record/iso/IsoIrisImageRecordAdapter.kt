package net.iriscan.sdk.iris.record.iso

import net.iriscan.sdk.core.record.BiometricRecordAdapter
import net.iriscan.sdk.core.record.BiometricRecordValidationResult

/**
 * @author Slava Gornostal
 */
class IsoIrisImageRecordAdapter(override val formatIdentifier: String = "IIR") :
    BiometricRecordAdapter<IsoIrisImageRecord> {

    override fun deserialize(data: ByteArray): IsoIrisImageRecord {
        TODO("Not yet implemented")
    }

    override fun validate(data: ByteArray): BiometricRecordValidationResult {
        TODO("Not yet implemented")
    }

    override fun serialize(record: IsoIrisImageRecord): ByteArray {
        TODO("Not yet implemented")
    }
}