package com.example.smartfishfeeder.util

import com.example.smartfishfeeder.data.model.FeedingSchedule
import java.util.Calendar

/** Parses "06:00 AM" / "08:00 PM" into total minutes since midnight (0-1439). */
fun parseFeedingTimeToMinutes(feedingTime: String): Int {
    val isPM = feedingTime.contains("PM", ignoreCase = true)
    val digitsOnly = feedingTime
        .replace("AM", "", ignoreCase = true)
        .replace("PM", "", ignoreCase = true)
        .trim()
    val parts = digitsOnly.split(":")
    val hourPart = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val minutePart = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    val hour24 = when {
        isPM && hourPart != 12 -> hourPart + 12
        !isPM && hourPart == 12 -> 0
        else -> hourPart
    }
    return hour24 * 60 + minutePart
}

/**
 * Finds the next upcoming ENABLED feeding time from a list of schedules,
 * based on the current time of day — not list insertion order. If every
 * enabled schedule's time has already passed today, wraps around to the
 * earliest enabled schedule (tomorrow's first feeding). Returns null if
 * there are no enabled schedules at all.
 */
fun List<FeedingSchedule>.nextFeedingTime(): String? {
    val enabled = this.filter { it.enabled }
    if (enabled.isEmpty()) return null

    val calendar = Calendar.getInstance()
    val nowMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

    val upcomingToday = enabled
        .filter { parseFeedingTimeToMinutes(it.feedingTime) >= nowMinutes }
        .minByOrNull { parseFeedingTimeToMinutes(it.feedingTime) }

    return upcomingToday?.feedingTime
        ?: enabled.minByOrNull { parseFeedingTimeToMinutes(it.feedingTime) }?.feedingTime
}