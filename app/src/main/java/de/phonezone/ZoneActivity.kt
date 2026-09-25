package de.phonezone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.Surface
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/**
 * AR-Ansicht: Fläche erkennen, Bereich per Tippen und Schieberegler festlegen
 * und erkennen, wann das Handy in den Bereich kommt.
 */
class ZoneActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    companion object {
        private const val SPOT_ENTER_HEIGHT = 0.08f  // Ablage: Kamera < 8 cm über der Fläche = abgelegt
        private const val SPOT_LOST_HEIGHT = 0.20f   // Ablage: Tracking knapp darüber verloren = abgelegt
        private const val AREA_MAX_HEIGHT = 2.5f     // Arbeitsplatz: bis 2,5 m über der Fläche …
        private const val AREA_MIN_HEIGHT = -1.5f    // … und bis 1,5 m darunter (falls die Fläche der Tisch ist)
    }

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusText: TextView
    private lateinit var zoneType: ZoneType

    @Volatile private var session: Session? = null
    private var installRequested = false
    private var permissionsRequested = false
    private var cameraTextureSet = false
    private var viewportChanged = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    private val background = BackgroundRenderer()
    private val zoneRenderer = ZoneRenderer()
    private val taps = ConcurrentLinkedQueue<FloatArray>()

    @Volatile private var halfSize = 0f
    private var zoneAnchor: Anchor? = null
    private var lastPosition: FloatArray? = null

    /** Erst "scharf", wenn das Handy einmal außerhalb des Bereichs war. */
    @Volatile private var armed = false
    private var locked = false
    private var lastStatus = 0

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zone)
        zoneType = Prefs.zoneType(this)
        statusText = findViewById(R.id.status_text)
        surfaceView = findViewById(R.id.surface_view)

        val sizeText = findViewById<TextView>(R.id.size_text)
        fun applySize(cm: Float) {
            halfSize = cm / 200f
            sizeText.text = formatSize(cm)
            armed = false // Bei neuer Größe muss das Handy erst wieder draußen sein
        }
        findViewById<Slider>(R.id.size_slider).apply {
            value = zoneType.defaultSizeCm
            setLabelFormatter { formatSize(it) }
            addOnChangeListener { _, cm, _ -> applySize(cm) }
        }
        applySize(zoneType.defaultSizeCm)

        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        surfaceView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                taps.offer(floatArrayOf(event.x, event.y))
                view.performClick()
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) ==
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED
                ) {
                    installRequested = true
                    return
                }
                if (!permissionsRequested) {
                    permissionsRequested = true
                    val missing = requiredPermissions().filter {
                        checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        requestPermissions(missing.toTypedArray(), 0)
                        return
                    }
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                session = Session(this).also { s ->
                    s.configure(Config(s).apply {
                        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                        focusMode = Config.FocusMode.AUTO
                    })
                }
                cameraTextureSet = false
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.ar_not_available) + "\n" + e.message, Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }

        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
            session = null
            return
        }
        surfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        // Reihenfolge wichtig: erst GL-Thread anhalten, dann die Session.
        surfaceView.onPause()
        session?.pause()
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    /** Kamera für AR, Schrittzähler zum Erkennen des Weggehens am Arbeitsplatz. */
    private fun requiredPermissions() = buildList {
        add(Manifest.permission.CAMERA)
        if (zoneType == ZoneType.AREA && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
    }

    // ---- OpenGL / AR-Schleife (läuft im GL-Thread) ----

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
        zoneRenderer.createOnGlThread()
        cameraTextureSet = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = session ?: return

        if (!cameraTextureSet) {
            session.setCameraTextureName(background.textureId)
            cameraTextureSet = true
        }
        if (viewportChanged) {
            session.setDisplayGeometry(Surface.ROTATION_0, viewportWidth, viewportHeight)
            viewportChanged = false
        }

        val frame = try {
            session.update()
        } catch (e: CameraNotAvailableException) {
            return
        }
        val camera = frame.camera

        handleTap(frame)
        background.draw(frame)
        if (locked) return

        val anchor = zoneAnchor
        if (camera.trackingState != TrackingState.TRACKING) {
            // Beim Ablegen verliert die Kamera oft die Sicht. War sie zuletzt knapp
            // über der Ablage, gilt das Handy als abgelegt.
            val last = lastPosition
            if (zoneType == ZoneType.SPOT && armed && last != null && isInZone(last, SPOT_LOST_HEIGHT)) {
                lock()
            } else {
                showStatus(R.string.status_tracking_lost)
            }
            return
        }

        if (anchor == null || anchor.trackingState != TrackingState.TRACKING) {
            lastPosition = null
            showStatus(if (hasHorizontalPlane(session)) R.string.status_tap_to_place else R.string.status_search_plane)
            return
        }

        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 50f)
        anchor.pose.toMatrix(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, halfSize, 1f, halfSize)
        zoneRenderer.draw(modelMatrix, viewMatrix, projectionMatrix)

        // Kameraposition relativ zum Bereich (y = Höhe über der Fläche)
        val position = anchor.pose.inverse().transformPoint(camera.pose.translation)
        lastPosition = position

        val enterHeight = if (zoneType == ZoneType.SPOT) SPOT_ENTER_HEIGHT else AREA_MAX_HEIGHT
        val exitMargin = if (zoneType == ZoneType.SPOT) 0.05f else 0.2f
        if (!armed && !isInZone(position, enterHeight + exitMargin, exitMargin)) {
            armed = true
        }
        if (armed && isInZone(position, enterHeight)) {
            lock()
            return
        }
        showStatus(
            when {
                !armed -> R.string.status_leave_zone
                zoneType == ZoneType.SPOT -> R.string.status_armed_spot
                else -> R.string.status_armed_area
            }
        )
    }

    private fun handleTap(frame: Frame) {
        val tap = taps.poll() ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return

        val hit = frame.hitTest(tap[0], tap[1]).firstOrNull { hit ->
            val plane = hit.trackable as? Plane
            plane != null &&
                plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                plane.isPoseInPolygon(hit.hitPose)
        } ?: return

        zoneAnchor?.detach()
        zoneAnchor = hit.createAnchor()
        armed = false
    }

    private fun hasHorizontalPlane(session: Session) =
        session.getAllTrackables(Plane::class.java).any {
            it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING
        }

    private fun isInZone(position: FloatArray, maxHeight: Float, margin: Float = 0f): Boolean {
        val half = halfSize + margin
        val minHeight = if (zoneType == ZoneType.SPOT) -0.1f else AREA_MIN_HEIGHT
        return abs(position[0]) < half && abs(position[2]) < half &&
            position[1] > minHeight - margin && position[1] < maxHeight
    }

    private fun lock() {
        locked = true
        runOnUiThread {
            val mode = Prefs.lockMode(this)
            ZoneLock.start(this, zoneType, mode)
            if (mode == LockMode.FULL) startActivity(Intent(this, LockActivity::class.java))
            finish()
        }
    }

    private fun showStatus(resId: Int) {
        if (resId == lastStatus) return
        lastStatus = resId
        runOnUiThread { statusText.setText(resId) }
    }

    private fun formatSize(cm: Float) =
        if (cm < 100) "${cm.toInt()} cm" else String.format(Locale.GERMANY, "%.1f m", cm / 100)
}
