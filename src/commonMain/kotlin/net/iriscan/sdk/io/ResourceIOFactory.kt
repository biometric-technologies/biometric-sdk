package net.iriscan.sdk.io

import net.iriscan.sdk.core.PlatformContext
import net.iriscan.sdk.core.createContext

/**
 * @author Slava Gornostal
 */
object ResourceIOFactory {
    private var instance: ResourceIO? = null
    fun initialize(context: PlatformContext?) {
        this.instance = ResourceIOImpl(context ?: createContext())
    }

    fun getInstance(): ResourceIO =
        this.instance ?: throw IllegalStateException("Resource helper was not initialized")

}