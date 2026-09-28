package app.moodiary.core.localization

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/** Resolve at use time: application/worker contexts also need AppCompat locales on Android 12. */
fun Context.localizedString(@StringRes id: Int, vararg args: Any): String =
    ContextCompat.getContextForLanguage(this).getString(id, *args)
