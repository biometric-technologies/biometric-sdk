package net.iriscan.sdk.core.utils

import kotlin.random.Random

/**
 * @author Slava Gornostal
 */
fun generateRandomAlphaNumericString(length: Int): String {
    val source = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val random = Random.Default
    val sb = StringBuilder(length)
    for (i in 0 until length) {
        val index = random.nextInt(source.length)
        sb.append(source[index])
    }
    return sb.toString()
}
