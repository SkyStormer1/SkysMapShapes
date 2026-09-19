package com.skystormer.skysmapshapes

import org.slf4j.LoggerFactory

/** What the mod writes to the log: only things that went wrong. */
object Log {

    private val LOGGER = LoggerFactory.getLogger("skysmapshapes")

    fun info(message: String, vararg arguments: Any?) = LOGGER.info(prefix(message), *arguments)

    fun warn(message: String, vararg arguments: Any?) = LOGGER.warn(prefix(message), *arguments)

    fun error(message: String, cause: Throwable) = LOGGER.error(prefix(message), cause)

    private fun prefix(message: String) = "[Sky's Map Shapes] $message"
}
