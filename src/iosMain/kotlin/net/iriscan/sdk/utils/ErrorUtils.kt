package net.iriscan.sdk.utils

import kotlinx.cinterop.*
import platform.Foundation.NSError

/**
 * @author Slava Gornostal
 */
internal fun <T> throwError(block: (errorPointer: CPointer<ObjCObjectVar<NSError?>>) -> T): T =
    memScoped {
        val errorPointer: CPointer<ObjCObjectVar<NSError?>> = alloc<ObjCObjectVar<NSError?>>().ptr
        val result: T = block(errorPointer)
        val error: NSError? = errorPointer.pointed.value
        if (error != null) {
            throw NSErrorException(error)
        } else {
            return result
        }
    }

class NSErrorException(nsError: NSError) : Exception(nsError.toString())