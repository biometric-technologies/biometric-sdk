package net.iriscan.sdk.core

import android.content.Context
import net.iriscan.sdk.exception.SdkInitializationException

/**
 * @author Slava Gornostal
 */
actual typealias PlatformContext = Context

actual fun createContext(): PlatformContext =
    throw SdkInitializationException("Please provide application context via initialize method", null)