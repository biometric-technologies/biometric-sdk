package net.iriscan.sdk.qc

/**
 * @author Slava Gornostal
 */
interface QualityControlOperations {
    /**
     * Calculates quality of provided image
     * @return value from 1 to 100, where 1 is bad and 100 is good quality
     * */
    fun calculate(data: ByteArray): Int
}