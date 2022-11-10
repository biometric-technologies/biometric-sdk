package net.iriscan.sdk.core.image

/**
 * @author Slava Gornostal
 *
 * Image color type
 *
 * RGB - 0x000000..0xffffff
 * GRAY - 0x00..0xff
 * BINARY - 0..1
 *
 * @see Color
 */
enum class ImageColorType {
    RGB,
    GRAY,
    BINARY
}