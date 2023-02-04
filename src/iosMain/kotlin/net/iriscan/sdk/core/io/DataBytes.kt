package net.iriscan.sdk.core.io

import net.iriscan.sdk.utils.toByteArray
import net.iriscan.sdk.utils.toNSData
import platform.Foundation.NSData

/**
 * @author Slava Gornostal
 */
actual typealias DataBytes = NSData

internal actual fun DataBytes.asByteArray(): ByteArray = this.toByteArray()
internal actual fun ByteArray.asDataBytes(): DataBytes = this.toNSData()