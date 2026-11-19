package net.iriscan.sdk.utils

import kotlinx.cinterop.*
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
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

/**
 * Copies the bytes into a new NSData, which owns the copy.
 *
 * The NSData must not point at the array itself (dataWithBytesNoCopy): the array is pinned only
 * inside [usePinned], and once no Kotlin code references it the garbage collector frees it and
 * reuses its memory for other objects. The iris and face templates the SDK returns on iOS, and
 * the result of `byteArrayToNsData`, are made here, so without the copy a template that the
 * application keeps could change after the next collection.
 */
fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), this.size.toULong())
}
