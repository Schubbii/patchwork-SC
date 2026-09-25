package de.phonezone

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.content.edit

enum class ZoneType(@StringRes val description: Int, @StringRes val exitHint: Int, val defaultSizeCm: Float) {
    /** Kleiner Bereich, in den das Handy gelegt wird. */
    SPOT(R.string.zone_spot_description, R.string.exit_hint_spot, 30f),

    /** Großer Bereich, in dem du dich aufhältst (z. B. Schreibtisch). */
    AREA(R.string.zone_area_description, R.string.exit_hint_area, 150f)
}

enum class LockMode(@StringRes val description: Int) {
    FULL(R.string.mode_full_description),
    APPS(R.string.mode_apps_description)
}

/** Gespeicherte Einstellungen. */
object Prefs {
    private const val KEY_ZONE_TYPE = "zone_type"
    private const val KEY_LOCK_MODE = "lock_mode"
    private const val KEY_BLOCKED_APPS = "blocked_apps"

    private fun prefs(context: Context) = context.getSharedPreferences("phonezone", Context.MODE_PRIVATE)

    fun zoneType(context: Context) =
        ZoneType.valueOf(prefs(context).getString(KEY_ZONE_TYPE, null) ?: ZoneType.SPOT.name)

    fun setZoneType(context: Context, type: ZoneType) =
        prefs(context).edit { putString(KEY_ZONE_TYPE, type.name) }

    fun lockMode(context: Context) =
        LockMode.valueOf(prefs(context).getString(KEY_LOCK_MODE, null) ?: LockMode.FULL.name)

    fun setLockMode(context: Context, mode: LockMode) =
        prefs(context).edit { putString(KEY_LOCK_MODE, mode.name) }

    fun blockedApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_BLOCKED_APPS, null)?.toSet() ?: emptySet()

    fun setBlockedApps(context: Context, apps: Set<String>) =
        prefs(context).edit { putStringSet(KEY_BLOCKED_APPS, apps.toSet()) }
}
