package net.iriscan.sdk.core.utils

import io.github.aakira.napier.LogLevel
import io.github.aakira.napier.Napier

/**
 * @author Slava Gornostal
 */
fun generateTraceID(): String? =
    if (Napier.isEnable(LogLevel.DEBUG, null))
        generateRandomAlphaNumericString(5) // TODO: optimize, reduce collision chance
    else
        null