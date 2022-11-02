package net.iriscan.sdk

import net.iriscan.sdk.face.FaceOperations
import net.iriscan.sdk.io.InputOutputOperations
import net.iriscan.sdk.iris.IrisOperations
import net.iriscan.sdk.qc.QualityControlOperations

/**
 * @author Slava Gornostal
 */
interface BiometricSdkOperations {
    fun io(): InputOutputOperations
    fun qualityControl(): QualityControlOperations
    fun iris(): IrisOperations
    fun face(): FaceOperations
}