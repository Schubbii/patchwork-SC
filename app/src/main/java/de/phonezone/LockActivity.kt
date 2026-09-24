package de.phonezone

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Sperrbildschirm, solange das Handy im Bereich liegt.
 *
 * Liegt das Handy auf dem Tisch, sieht die Kamera nichts mehr. Deshalb wird hier
 * mit dem Beschleunigungssensor erkannt, wann das Handy wieder hochgenommen wird.
 */
class LockActivity : Activity(), SensorEventListener {

    companion object {
        private const val SETTLE_TIME_MS = 2000L     // so lange ruhig liegen, bevor überwacht wird
        private const val STILL_THRESHOLD = 0.4f     // m/s², darunter gilt das Handy als ruhig
        private const val MOVE_THRESHOLD = 2.5f      // m/s², darüber gilt es als hochgenommen
        private const val TILT_THRESHOLD_DEG = 20.0  // Kippwinkel, ab dem es als hochgenommen gilt
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var statusText: TextView

    private var gravity: FloatArray? = null
    private var restingGravity: FloatArray? = null
    private var stillSince = 0L
    private var lockTaskRequested = false
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock)
        statusText = findViewById(R.id.lock_status)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager

        // Bildschirm bleibt (gedimmt) an, damit die Bewegungserkennung weiterläuft.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0.05f }

        findViewById<Button>(R.id.emergency_button).setOnClickListener {
            unlock()
            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        // App anheften: Home- und Zurück-Taste sind dann blockiert.
        if (!lockTaskRequested) {
            lockTaskRequested = true
            try {
                startLockTask()
            } catch (e: Exception) {
                // Ohne Anheften bleibt nur der Sperrbildschirm.
            }
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    @Deprecated("Zurück-Taste ist im gesperrten Zustand deaktiviert")
    override fun onBackPressed() {
        // Absichtlich leer: Zurück entsperrt nicht.
    }

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values
        // Tiefpassfilter trennt Schwerkraft von Bewegung
        val g = gravity ?: values.copyOf(3).also { gravity = it }
        for (i in 0..2) g[i] = 0.8f * g[i] + 0.2f * values[i]
        val motion = magnitude(values[0] - g[0], values[1] - g[1], values[2] - g[2])

        val resting = restingGravity
        if (resting == null) {
            // Phase 1: warten, bis das Handy ruhig liegt
            val now = SystemClock.elapsedRealtime()
            if (motion > STILL_THRESHOLD) {
                stillSince = 0L
            } else if (stillSince == 0L) {
                stillSince = now
            } else if (now - stillSince > SETTLE_TIME_MS) {
                restingGravity = g.copyOf()
                statusText.setText(R.string.lock_active)
            }
            return
        }

        // Phase 2: hochgenommen = starke Bewegung oder deutlich gekippt
        if (motion > MOVE_THRESHOLD || angleDeg(g, resting) > TILT_THRESHOLD_DEG) {
            unlock()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun unlock() {
        if (unlocked) return
        unlocked = true
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                stopLockTask()
            } catch (e: Exception) {
                // ignorieren
            }
        }
        finish()
    }

    private fun magnitude(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    private fun angleDeg(a: FloatArray, b: FloatArray): Double {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val cos = (dot / (magnitude(a[0], a[1], a[2]) * magnitude(b[0], b[1], b[2]))).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble())
    }
}
