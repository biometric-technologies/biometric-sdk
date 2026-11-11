package net.iriscan.sdk.utils

import platform.Foundation.NSLock

/**
 * Runs [block] while holding the lock.
 *
 * The lock is released in `finally`, so a block that throws does not keep every later caller
 * waiting, and on the thread that took it, which NSLock requires.
 */
internal inline fun <T> NSLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
