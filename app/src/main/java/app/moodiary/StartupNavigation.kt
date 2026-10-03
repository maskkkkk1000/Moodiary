package app.moodiary

import android.content.Intent

/** Accept only known destinations; notification payloads never become arbitrary navigation routes. */
object StartupNavigation {
    const val EXTRA_DESTINATION = "moodiary.destination"
    const val EXTRA_TARGET_ID = "moodiary.target_id"
    fun destination(intent: Intent?): String? = when (intent?.getStringExtra(EXTRA_DESTINATION)) {
        "entry" -> "editor/0?date="
        "goals" -> "goals?target=${intent.getLongExtra(EXTRA_TARGET_ID, 0).coerceAtLeast(0)}"
        "binary_goals" -> "binary_goals"
        else -> null
    }
}

data class LaunchRequest(val sequence: Long, val destination: String?)
