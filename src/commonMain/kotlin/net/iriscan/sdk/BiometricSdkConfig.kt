package net.iriscan.sdk

import net.iriscan.sdk.iris.IrisEncodeProperties
import net.iriscan.sdk.iris.IrisExtractProperties
import net.iriscan.sdk.iris.IrisMatchProperties

/**
 * @author Slava Gornostal
 *
 * SDK configuration object
 */
class BiometricSdkConfig(val iris: IrisConfig) {
    companion object {
        fun builder() = Builder()
    }

    data class Builder(
        var irisConfig: IrisConfig? = null,
    ) {

        fun configureIris(
            extractor: IrisExtractProperties,
            encoder: IrisEncodeProperties,
            matcher: IrisMatchProperties
        ) =
            apply {
                this.irisConfig = IrisConfig(extractor, encoder, matcher)
            }

        fun build(): BiometricSdkConfig =
            BiometricSdkConfig(
                iris = irisConfig ?: IrisConfig(
                    IrisExtractProperties(),
                    IrisEncodeProperties(),
                    IrisMatchProperties()
                ),
            )
    }
}

data class IrisConfig(
    val extractor: IrisExtractProperties,
    val encoder: IrisEncodeProperties,
    val matcher: IrisMatchProperties,
)