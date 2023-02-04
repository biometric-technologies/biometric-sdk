package net.iriscan.sdk.core.io

/**
 * @author Slava Gornostal
 */
expect class DataBytes

internal expect fun DataBytes.asByteArray(): ByteArray
internal expect fun ByteArray.asDataBytes(): DataBytes