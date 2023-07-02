package net.iriscan.sdk.core

/**
 * @author Slava Gornostal
 */
actual abstract class PlatformContext

actual fun createContext() = object : PlatformContext() {}