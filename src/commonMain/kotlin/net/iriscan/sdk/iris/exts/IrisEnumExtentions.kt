package net.iriscan.sdk.iris.exts

/**
 * @author Anton Kurinnoy
 */
interface Identifiable {
    val value: Int
}

inline fun <reified T> findEnumById(value: Int): T where T : Enum<T>, T : Identifiable =
    enumValues<T>().find { it.value == value } ?: throw IllegalArgumentException("Enum not found")