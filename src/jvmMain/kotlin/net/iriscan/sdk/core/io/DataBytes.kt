package net.iriscan.sdk.core.io

/**
 * @author Slava Gornostal
 */
actual typealias DataBytes = ByteArray

internal actual fun DataBytes.asByteArray(): ByteArray = this
internal actual fun ByteArray.asDataBytes(): DataBytes = this