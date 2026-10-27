package com.skystormer.skysmapshapes

/**
 * Work done every so many client ticks: [due] is asked once a tick and says yes on every
 * [ticks]th. With [dueFirst] the very first ask says yes too, then every [ticks]th after it.
 */
class Every(private val ticks: Int, dueFirst: Boolean = false) {

    private var count = if (dueFirst) ticks - 1 else 0

    /** Called once a tick: true when the work is due. */
    fun due(): Boolean {
        count = (count + 1) % ticks
        return count == 0
    }

    /** Makes the next [due] say yes, to do the work on the next tick rather than waiting. */
    fun soon() {
        count = ticks - 1
    }

    /** Counts from the start again, as after the work was just done some other way. */
    fun restart() {
        count = 0
    }
}
