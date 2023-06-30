package net.iriscan.sdk.io

import net.iriscan.sdk.core.PlatformContext

/**
 * @author Slava Gornostal
 */
object ResourceIOFactory {
    private var instance: ResourceIO? = null
    fun initialize(context: PlatformContext) {
        this.instance = ResourceIOImpl(context)
    }

    fun getInstance(): ResourceIO =
        this.instance ?: throw IllegalStateException("Resource helper was not initialized")

}