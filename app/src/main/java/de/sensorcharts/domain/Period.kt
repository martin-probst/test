package de.sensorcharts.domain

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Where the values for a period come from. */
enum class Resolution(
    /** PostgREST resource (table or view). */
    val resource: String,
    /** Column holding the timestamp. */
    val timeColumn: String,
    /** Column holding the value. */
    val valueColumn: String,
    /** Nominal distance between two values in minutes (used as chart x step). */
    val stepMinutes: Int,
) {
    RAW("event_value", "ts", "value", 5),
    HOURLY("event_hourly", "bucket", "avg_value", 60),
    DAILY("event_daily", "bucket", "avg_value", 1440),
}

/** A page size of the dashboard. */
enum class Period(
    val label: String,
    val resolution: Resolution,
    /** Distance between two labels on the x axis in minutes. */
    val labelIntervalMinutes: Int,
    private val axisPattern: String,
) {
    DAY("Tag", Resolution.RAW, 180, "HH:mm"),
    WEEK("Woche", Resolution.HOURLY, 1440, "EE dd."),
    MONTH("Monat", Resolution.HOURLY, 7 * 1440, "dd.MM."),
    YEAR("Jahr", Resolution.DAILY, 30 * 1440, "dd.MM."),
    ;

    val axisFormatter: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern(axisPattern, Locale.GERMAN) }

    /** Start of the period that contains [date]. */
    fun startOf(date: LocalDate): LocalDate = when (this) {
        DAY -> date
        WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        MONTH -> date.withDayOfMonth(1)
        YEAR -> date.withDayOfYear(1)
    }

    /** Start of the period [steps] pages after the one starting at [start]. */
    fun shift(start: LocalDate, steps: Long): LocalDate = when (this) {
        DAY -> start.plusDays(steps)
        WEEK -> start.plusWeeks(steps)
        MONTH -> start.plusMonths(steps)
        YEAR -> start.plusYears(steps)
    }
}

/** A concrete time window: one page of the dashboard. */
data class TimeWindow(
    val period: Period,
    val startDate: LocalDate,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val start: ZonedDateTime get() = startDate.atStartOfDay(zone)
    val end: ZonedDateTime get() = period.shift(startDate, 1).atStartOfDay(zone)

    /** Length of the window in minutes (respects DST changes). */
    val lengthMinutes: Long get() = Duration.between(start, end).toMinutes()

    fun next() = copy(startDate = period.shift(startDate, 1))
    fun previous() = copy(startDate = period.shift(startDate, -1))
    fun withPeriod(newPeriod: Period) = copy(period = newPeriod, startDate = newPeriod.startOf(startDate))

    /** True if the window contains today (no further pages to the right). */
    fun containsToday(): Boolean = !LocalDate.now(zone).isBefore(startDate) &&
        LocalDate.now(zone).isBefore(period.shift(startDate, 1))

    /** Human readable title such as "KW 47 · 17.11.–23.11.2026". */
    fun title(): String {
        val lastDay = period.shift(startDate, 1).minusDays(1)
        return when (period) {
            Period.DAY -> startDate.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", Locale.GERMAN))
            Period.WEEK -> {
                val week = startDate.get(WeekFields.ISO.weekOfWeekBasedYear())
                "KW $week · ${startDate.format(DAY_MONTH)}–${lastDay.format(FULL_DATE)}"
            }
            Period.MONTH -> startDate.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN))
            Period.YEAR -> startDate.year.toString()
        }
    }

    companion object {
        private val DAY_MONTH = DateTimeFormatter.ofPattern("dd.MM.")
        private val FULL_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        fun current(period: Period, zone: ZoneId = ZoneId.systemDefault()) =
            TimeWindow(period, period.startOf(LocalDate.now(zone)), zone)
    }
}
