package net.iriscan.sdk.iris

import net.iriscan.sdk.iris.record.IrisImageRecord
import net.iriscan.sdk.iris.record.IrisTemplateRecord

/**
 * @author Slava Gornostal
 */
interface IrisEncoder {
    /**
     * Encodes provided segmented record to biometric template record
     * */
    fun encode(sample: IrisImageRecord): IrisTemplateRecord
}