package net.iriscan.sdk.utils

import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.dataWithBytesNoCopy
import platform.posix.memcpy

/**
 * @author Slava Gornostal
 */
fun NSData.toByteArray(): ByteArray = memScoped {
    val buffer = ByteArray(length.toInt())
    val dataPtr = bytes?.reinterpret<ByteVar>()
    buffer.usePinned { pinned -> memcpy(pinned.addressOf(0), dataPtr, length) }
    return buffer
}

fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytesNoCopy(pinned.addressOf(0), this.size.toULong(), false)
}