package com.hackathon.powerguard.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats a timestamp into a readable date/time string.
 *
 * @param timestamp The timestamp in milliseconds
 * @param pattern The date format pattern
 * @return Formatted date/time string
 */
fun formatTimestamp(timestamp: Long, pattern: String): String {
    val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

/**
 * Returns a human-readable relative time string.
 *
 * @param timestamp The timestamp in milliseconds
 * @return Human-readable relative time (e.g., "5 minutes ago", "2 hours ago")
 */
fun getRelativeTimeSpanString(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
        diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
        else -> formatTimestamp(timestamp, "MMM d, yyyy")
    }
}
