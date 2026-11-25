package de.sensorcharts.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimeWindowTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun weekStartsOnMonday() {
        val window = TimeWindow(Period.WEEK, Period.WEEK.startOf(LocalDate.of(2026, 11, 26)), berlin)
        assertEquals(LocalDate.of(2026, 11, 23), window.startDate)
        assertEquals(LocalDate.of(2026, 11, 30), window.next().startDate)
        assertEquals(LocalDate.of(2026, 11, 16), window.previous().startDate)
    }

    @Test
    fun switchingPeriodKeepsDate() {
        val day = TimeWindow(Period.DAY, LocalDate.of(2025, 3, 14), berlin)
        assertEquals(LocalDate.of(2025, 3, 1), day.withPeriod(Period.MONTH).startDate)
        assertEquals(LocalDate.of(2025, 1, 1), day.withPeriod(Period.YEAR).startDate)
    }

    @Test
    fun lengthRespectsDaylightSavingTime() {
        // 30 March 2025: clocks go forward, the day has 23 hours.
        assertEquals(23 * 60L, TimeWindow(Period.DAY, LocalDate.of(2025, 3, 30), berlin).lengthMinutes)
        assertEquals(24 * 60L, TimeWindow(Period.DAY, LocalDate.of(2025, 3, 31), berlin).lengthMinutes)
    }

    @Test
    fun pastWindowDoesNotContainToday() {
        assertFalse(TimeWindow(Period.MONTH, LocalDate.of(2020, 1, 1), berlin).containsToday())
    }
}
