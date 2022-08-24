package net.iriscan.sdk.iris

import net.iriscan.sdk.iris.record.IrisImageRecord

/**
 * @author Slava Gornostal
 */
interface IrisExtractor {
    /**
     * Extracts biometric data from raw image
     * */
    fun extract(sample: IrisImageRecord): IrisImageRecord
}