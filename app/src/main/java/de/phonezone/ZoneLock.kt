package de.phonezone

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.acos
import kotlin.math.sqrt

/** Aktueller Sperrzustand. Wird in der AR-Ansicht gestartet und beim Verlassen des Bereichs beendet. */
object ZoneLock {
    fun interface Listener {
        fun onLockChanged()
    }

    var isActive = false
        private set
    var zoneType = ZoneType.SPOT
        private set
    var lockMode = LockMode.FULL
        private set

    private var detector: ExitDetector? = null
    private val listeners = mutableListOf<Listener>()

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun start(context: Context, zoneType: ZoneType, lockMode: LockMode) {
        if (isActive) return
        isActive = true
        this.zoneType = zoneType
        this.lockMode = lockMode
        detector = ExitDetector(context.applicationContext, zoneType) { stop() }.also { it.start() }
        notifyListeners()
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        detector?.stop()
        detector = null
        notifyListeners()
    }

    private fun notifyListeners() = listeners.toList().forEach { it.onLockChanged() }
}

/**
 * Erkennt, dass das Handy den Bereich verlassen hat.
 * - Ablage: Das Handy wird hochgenommen (Beschleunigungssensor).
 * - Arbeitsplatz: Du gehst weg (Schrittzähler).
 * Beides sind Zustände statt kurzer Ereignisse. Sie werden also auch erkannt,
 * wenn Sensordaten bei ausgeschaltetem Display verspätet ankommen.
 */
private class ExitDetector(
    context: Context,
    private val zoneType: ZoneType,
    private val onExit: () -> Unit
) : SensorEventListener {

    companion object {
        private const val SETTLE_TIME_MS = 2000L     // so lange ruhig liegen, bevor überwacht wird
        private const val STILL_THRESHOLD = 0.4f     // m/s², darunter gilt das Handy als ruhig
        private const val MOVE_THRESHOLD = 2.5f      // m/s², darüber gilt es als hochgenommen
        private const val TILT_THRESHOLD_DEG = 20.0  // Kippwinkel, ab dem es als hochgenommen gilt
        private const val EXIT_STEPS = 10            // Schritte, ab denen der Arbeitsplatz verlassen ist
    }

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private var gravity: FloatArray? = null
    private var restingGravity: FloatArray? = null
    private var stillSince = 0L
    private var startSteps = -1f

    fun start() {
        val type = if (zoneType == ZoneType.SPOT) Sensor.TYPE_ACCELEROMETER else Sensor.TYPE_STEP_COUNTER
        sensorManager.getDefaultSensor(type)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() = sensorManager.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) onSteps(event.values[0]) else onAcceleration(event.values)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun onSteps(totalSteps: Float) {
        if (startSteps < 0) startSteps = totalSteps
        if (totalSteps - startSteps >= EXIT_STEPS) onExit()
    }

    private fun onAcceleration(values: FloatArray) {
        // Tiefpassfilter trennt Schwerkraft von Bewegung
        val g = gravity ?: values.copyOf(3).also { gravity = it }
        for (i in 0..2) g[i] = 0.8f * g[i] + 0.2f * values[i]
        val motion = magnitude(values[0] - g[0], values[1] - g[1], values[2] - g[2])

        val resting = restingGravity
        if (resting == null) {
            // Erst warten, bis das Handy ruhig liegt
            val now = SystemClock.elapsedRealtime()
            when {
                motion > STILL_THRESHOLD -> stillSince = 0L
                stillSince == 0L -> stillSince = now
                now - stillSince > SETTLE_TIME_MS -> restingGravity = g.copyOf()
            }
            return
        }

        if (motion > MOVE_THRESHOLD || angleDeg(g, resting) > TILT_THRESHOLD_DEG) onExit()
    }

    private fun magnitude(x: Float, y: Float, z: Float) = sqrt(x * x + y * y + z * z)

    private fun angleDeg(a: FloatArray, b: FloatArray): Double {
        val dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val cos = (dot / (magnitude(a[0], a[1], a[2]) * magnitude(b[0], b[1], b[2]))).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos).toDouble())
    }
}
