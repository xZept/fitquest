// app/src/main/java/com/example/fitquest/utils/DayKey.kt
package com.example.fitquest.utils

import java.time.LocalDate
import java.time.ZoneId

fun dayKeyForMillis(ms: Long, zone: ZoneId = ZoneId.of("Asia/Manila")): Int {
    val d = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    return d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
}

fun localDateFromDayKey(dayKey: Int): LocalDate {
    val y = dayKey / 10000
    val m = (dayKey / 100) % 100
    val d = dayKey % 100
    return LocalDate.of(y, m, d)
}
