package com.nautilus.ideas.utils

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Превращает время в миллисекундах в короткую подпись для списка.
 *
 * В базе дата лежит числом (не зависит ни от часового пояса, ни от
 * формата даты в системе), а показывать её пользователю нужно словами.
 */
object DateFormatter {

    private val russian = Locale.forLanguageTag("ru")

    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", russian)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", russian)
    private val time = DateTimeFormatter.ofPattern("HH:mm", russian)

    /** Например: «сегодня, 14:05», «вчера, 09:30», «12 сентября». */
    fun formatShort(millis: Long): String {
        val moment = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val date = moment.toLocalDate()
        val today = LocalDate.now()

        return when {
            date == today -> "сегодня, ${moment.format(time)}"
            date == today.minusDays(1) -> "вчера, ${moment.format(time)}"
            date.year == today.year -> date.format(dayMonth)
            else -> date.format(dayMonthYear)
        }
    }

    /** Полная дата со временем — для экрана детального просмотра. */
    fun formatFull(millis: Long): String {
        val moment = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        return "${moment.format(dayMonthYear)}, ${moment.format(time)}"
    }
}
