package de.phonezone

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible

/**
 * Sperrbildschirm.
 * - Ohne Extra: ganzes Handy gesperrt (App wird angeheftet).
 * - Mit [EXTRA_BLOCKED_APP]: eine gesperrte App wurde geöffnet.
 */
class LockActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BLOCKED_APP = "blocked_app"
    }

    private val blockedApp by lazy { intent.getStringExtra(EXTRA_BLOCKED_APP) }
    private val lockListener = ZoneLock.Listener { if (!ZoneLock.isActive) close() }
    private var pinRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ZoneLock.isActive) {
            finish()
            return
        }
        setContentView(R.layout.activity_lock)

        findViewById<TextView>(R.id.lock_title).text =
            blockedApp?.let { getString(R.string.lock_title_app, it) } ?: getString(R.string.active_title_phone)
        findViewById<TextView>(R.id.lock_hint).setText(ZoneLock.zoneType.exitHint)
        findViewById<Button>(R.id.emergency_button).setOnClickListener { ZoneLock.stop() }
        findViewById<Button>(R.id.close_button).apply {
            isVisible = blockedApp != null
            setOnClickListener { goHome() }
        }
        // Zurück entsperrt nicht: bei gesperrter App geht es zum Startbildschirm.
        onBackPressedDispatcher.addCallback(this) { if (blockedApp != null) goHome() }

        ZoneLock.addListener(lockListener)
    }

    override fun onResume() {
        super.onResume()
        // Ganzes Handy: App anheften, damit Home- und Zurück-Taste blockiert sind.
        if (blockedApp == null && !pinRequested) {
            pinRequested = true
            runCatching { startLockTask() }
        }
    }

    override fun onDestroy() {
        ZoneLock.removeListener(lockListener)
        super.onDestroy()
    }

    private fun close() {
        val activityManager = getSystemService(ActivityManager::class.java)
        if (activityManager.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            runCatching { stopLockTask() }
        }
        finish()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
