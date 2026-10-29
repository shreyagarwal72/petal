/*
 * Copyright (c) 2026 Petal Browser & Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package com.petal.browser.ui.theme

import java.time.LocalDate
import java.time.MonthDay

/**
 * Calendar events the app dresses up for. Each one comes back on the same dates every year and,
 * while it runs, takes over the background and theme unless overridden.
 *
 * @param start First day of the event.
 * @param end Last day of the event, inclusive.
 */
enum class SeasonalEvent(
    private val start: MonthDay,
    private val end: MonthDay
) {
    HALLOWEEN(
        start = MonthDay.of(10, 24),
        end = MonthDay.of(10, 31)
    );

    fun isRunningOn(day: MonthDay): Boolean =
        if (start <= end) day in start..end else day >= start || day <= end

    /** The last day of the run of this event that is on, or next to come, as of [today]. */
    fun endDate(today: LocalDate = LocalDate.now()): LocalDate {
        val thisYear = end.atYear(today.year)
        return if (thisYear < today) end.atYear(today.year + 1) else thisYear
    }

    companion object {
        /** The event running on [date], or null on an ordinary day. */
        fun on(date: LocalDate = LocalDate.now()): SeasonalEvent? {
            val day = MonthDay.from(date)
            return entries.firstOrNull { it.isRunningOn(day) }
        }

        /** Whether Halloween seasonal event is currently active (Oct 24 to Oct 31 night inclusive). */
        val isHalloweenActive: Boolean
            get() = on() == HALLOWEEN
    }
}
