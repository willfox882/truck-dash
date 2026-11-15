package com.truckdash.ui

/** Clock-style value + unit label for stat tiles: "12:05" min:sec under an hour, "1:05" h:mm above. */
fun formatClock(totalSec: Long): Pair<String, String> =
    if (totalSec < 3600) "%d:%02d".format(totalSec / 60, totalSec % 60) to "min:sec"
    else "%d:%02d".format(totalSec / 3600, (totalSec % 3600) / 60) to "h:mm"

/** Human duration for list rows: "45 s", "12 min", "1 h 05 min". */
fun formatDurationShort(totalSec: Long): String = when {
    totalSec < 60 -> "$totalSec s"
    totalSec < 3600 -> "${(totalSec + 30) / 60} min"
    else -> "${totalSec / 3600} h %02d min".format((totalSec % 3600) / 60)
}
