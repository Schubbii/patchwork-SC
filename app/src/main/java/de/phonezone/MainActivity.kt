package de.phonezone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButtonToggleGroup

/** Startseite: Bereich und Sperrmodus wählen, dann den Bereich per AR festlegen. */
class MainActivity : AppCompatActivity() {

    private val lockListener = ZoneLock.Listener { updateUi() }

    private lateinit var activeCard: View
    private lateinit var activeTitle: TextView
    private lateinit var activeHint: TextView
    private lateinit var setupGroup: View
    private lateinit var zoneHint: TextView
    private lateinit var modeHint: TextView
    private lateinit var pickAppsButton: Button
    private lateinit var serviceWarning: View
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        activeCard = findViewById(R.id.active_card)
        activeTitle = findViewById(R.id.active_title)
        activeHint = findViewById(R.id.active_hint)
        setupGroup = findViewById(R.id.setup_group)
        zoneHint = findViewById(R.id.zone_hint)
        modeHint = findViewById(R.id.mode_hint)
        pickAppsButton = findViewById(R.id.pick_apps_button)
        serviceWarning = findViewById(R.id.service_warning)
        startButton = findViewById(R.id.start_button)

        findViewById<MaterialButtonToggleGroup>(R.id.zone_toggle).apply {
            check(if (Prefs.zoneType(context) == ZoneType.SPOT) R.id.zone_spot else R.id.zone_area)
            addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                Prefs.setZoneType(context, if (id == R.id.zone_spot) ZoneType.SPOT else ZoneType.AREA)
                updateUi()
            }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.mode_toggle).apply {
            check(if (Prefs.lockMode(context) == LockMode.FULL) R.id.mode_full else R.id.mode_apps)
            addOnButtonCheckedListener { _, id, checked ->
                if (!checked) return@addOnButtonCheckedListener
                Prefs.setLockMode(context, if (id == R.id.mode_full) LockMode.FULL else LockMode.APPS)
                updateUi()
            }
        }

        pickAppsButton.setOnClickListener { startActivity(Intent(this, AppPickerActivity::class.java)) }
        findViewById<Button>(R.id.enable_service_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.emergency_button).setOnClickListener { ZoneLock.stop() }
        startButton.setOnClickListener { startActivity(Intent(this, ZoneActivity::class.java)) }

        ZoneLock.addListener(lockListener)
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    override fun onDestroy() {
        ZoneLock.removeListener(lockListener)
        super.onDestroy()
    }

    private fun updateUi() {
        val active = ZoneLock.isActive
        activeCard.isVisible = active
        setupGroup.isVisible = !active
        if (active) {
            activeTitle.setText(
                if (ZoneLock.lockMode == LockMode.FULL) R.string.active_title_phone else R.string.active_title_apps
            )
            activeHint.setText(ZoneLock.zoneType.exitHint)
            return
        }

        val appsMode = Prefs.lockMode(this) == LockMode.APPS
        val appCount = Prefs.blockedApps(this).size
        val serviceEnabled = AppBlockerService.isEnabled(this)

        zoneHint.setText(Prefs.zoneType(this).description)
        modeHint.setText(Prefs.lockMode(this).description)
        pickAppsButton.isVisible = appsMode
        pickAppsButton.text = getString(R.string.pick_apps, appCount)
        serviceWarning.isVisible = appsMode && !serviceEnabled
        startButton.isEnabled = !appsMode || (appCount > 0 && serviceEnabled)
    }
}
