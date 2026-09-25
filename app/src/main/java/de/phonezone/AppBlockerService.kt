package de.phonezone

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/** Bedienungshilfe, die im Modus "Nur Apps" gesperrte Apps beim Öffnen überdeckt. */
class AppBlockerService : AccessibilityService() {

    companion object {
        fun isEnabled(context: Context): Boolean {
            val self = ComponentName(context, AppBlockerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!ZoneLock.isActive || ZoneLock.lockMode != LockMode.APPS) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in Prefs.blockedApps(this)) return

        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        startActivity(
            Intent(this, LockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(LockActivity.EXTRA_BLOCKED_APP, label)
        )
    }

    override fun onInterrupt() = Unit
}
