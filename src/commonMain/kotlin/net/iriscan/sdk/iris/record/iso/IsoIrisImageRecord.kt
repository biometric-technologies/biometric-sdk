package net.iriscan.sdk.iris.record.iso

import net.iriscan.sdk.core.record.BiometricType
import net.iriscan.sdk.iris.record.IrisImageRecord

/**
 * @author Slava Gornostal
 */
data class IsoIrisImageRecord(
    override val formatIdentifier: String,
    override val formatVersion: String,
    override val type: BiometricType = BiometricType.IRIS,
    override val length: Long,
    override val certificationFlag: Byte,
    val numberOfIrises: Int,
    val numberOfEyesRepresented: Int,
    val data: List<IrisImageRecordDataBlock>
) : IrisImageRecord

data class IrisImageRecordDataBlock(val header: IrisImageRecordHeader, val data: IrisImageRecordData)

data class IrisImageRecordHeader(val length: Int)

data class IrisImageRecordData(val length: Int)