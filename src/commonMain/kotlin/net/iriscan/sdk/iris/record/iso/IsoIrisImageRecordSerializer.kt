package net.iriscan.sdk.iris.record.iso

import net.iriscan.sdk.io.record.BiometricRecordSerializer

/**
 * @author Slava Gornostal
 */
class IsoIrisImageRecordSerializer : BiometricRecordSerializer<IsoIrisImageRecord> {
    override val formatIdentifier: String get() = ""
    override fun canRead(data: ByteArray): Boolean {
        TODO("Not yet implemented")
    }

    override fun read(data: ByteArray): IsoIrisImageRecord {
        TODO("Not yet implemented")
    }

    override fun write(record: IsoIrisImageRecord): ByteArray {
        TODO("Not yet implemented")
    }
}