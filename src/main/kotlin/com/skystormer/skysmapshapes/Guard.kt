package com.skystormer.skysmapshapes

/**
 * An optional part that switches itself off, until the game restarts, the first time it fails.
 * The hooks into other mods are by reflection and can break when those mods change; that should
 * cost the one feature, said once in the log, not the game.
 *
 * [off] finishes the log line: "Could not <doing>; [off] until the game restarts".
 */
class Guard(private val off: String) {

    /** Whether it has failed, and so is off. */
    var broken = false
        private set

    /**
     * Runs [block] and gives its answer; gives [default] instead when already off, or when [block]
     * fails, which switches it off and says why, [doing] being what it was doing.
     */
    inline fun <T> run(doing: String, default: T, block: () -> T): T {
        if (broken) return default
        return try {
            block()
        } catch (e: Throwable) {
            fail(doing, e)
            default
        }
    }

    /** Switches it off without a failure to report, as when what it reads could not be found at all. */
    fun stop() {
        broken = true
    }

    @PublishedApi
    internal fun fail(doing: String, e: Throwable) {
        broken = true
        Log.error("Could not $doing; $off until the game restarts", e)
    }
}
