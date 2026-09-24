package de.phonezone

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.Surface
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs

/**
 * AR-Ansicht: Tisch erkennen, Bereich per Tippen festlegen und erkennen,
 * wann das Handy in diesen Bereich gelegt wird.
 */
class MainActivity : Activity(), GLSurfaceView.Renderer {

    companion object {
        private const val ZONE_HALF_SIZE = 0.12f  // Bereich = 24 x 24 cm
        private const val ENTER_HEIGHT = 0.08f    // Kamera tiefer als 8 cm über dem Bereich = "abgelegt"
        private const val LOST_HEIGHT = 0.20f     // Tracking verloren knapp über dem Bereich = "abgelegt"
        private const val EXIT_MARGIN = 0.05f     // Abstand, ab dem das Handy sicher "draußen" ist
        private const val CAMERA_PERMISSION_REQUEST = 1
    }

    private lateinit var surfaceView: GLSurfaceView
    private lateinit var statusText: TextView

    @Volatile private var session: Session? = null
    private var installRequested = false
    private var cameraTextureSet = false
    private var viewportChanged = false
    private var viewportWidth = 0
    private var viewportHeight = 0

    private val background = BackgroundRenderer()
    private val zoneRenderer = ZoneRenderer(ZONE_HALF_SIZE)
    private val taps = ConcurrentLinkedQueue<FloatArray>()

    private var zoneAnchor: Anchor? = null
    private var lastLocalPosition: FloatArray? = null

    /** Erst "scharf", wenn das Handy einmal außerhalb des Bereichs war. */
    @Volatile private var armed = false
    private var lastStatus = -1

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status_text)
        surfaceView = findViewById(R.id.surface_view)

        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(this)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                taps.offer(floatArrayOf(e.x, e.y))
                return true
            }

            override fun onDown(e: MotionEvent) = true
        })
        surfaceView.setOnTouchListener { _, event -> gestures.onTouchEvent(event) }
    }

    override fun onResume() {
        super.onResume()
        // Nach dem Entsperren muss das Handy erst wieder aus dem Bereich heraus.
        armed = false
        lastLocalPosition = null

        if (session == null) {
            try {
                if (ArCoreApk.getInstance().requestInstall(this, !installRequested) ==
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED
                ) {
                    installRequested = true
                    return
                }
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
                    return
                }
                session = Session(this).also { s ->
                    val config = Config(s)
                    config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    config.focusMode = Config.FocusMode.AUTO
                    s.configure(config)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
            finish()
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

        val anchor = zoneAnchor
        if (anchor != null && anchor.trackingState == TrackingState.STOPPED) {
            zoneAnchor = null
        }

        if (camera.trackingState != TrackingState.TRACKING) {
            // Beim Ablegen verliert die Kamera oft die Sicht. War sie zuletzt knapp
            // über dem Bereich, gilt das Handy als abgelegt.
            val last = lastLocalPosition
            if (armed && last != null && isAboveZone(last, LOST_HEIGHT)) {
                lockPhone()
            } else {
                showStatus(R.string.status_tracking_lost)
            }
            return
        }

        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 50f)

        if (anchor == null || anchor.trackingState != TrackingState.TRACKING) {
            lastLocalPosition = null
            showStatus(if (hasHorizontalPlane(session)) R.string.status_tap_to_place else R.string.status_search_plane)
            return
        }

        anchor.pose.toMatrix(modelMatrix, 0)
        zoneRenderer.draw(modelMatrix, viewMatrix, projectionMatrix)

        // Kameraposition relativ zum Bereich (y = Höhe über dem Tisch)
        val local = anchor.pose.inverse().transformPoint(camera.pose.translation)
        lastLocalPosition = local

        if (!armed && isClearlyOutside(local)) {
            armed = true
        }
        if (armed && isAboveZone(local, ENTER_HEIGHT)) {
            lockPhone()
            return
        }
        showStatus(if (armed) R.string.status_armed else R.string.status_leave_zone)
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

    private fun isAboveZone(local: FloatArray, maxHeight: Float) =
        abs(local[0]) < ZONE_HALF_SIZE && abs(local[2]) < ZONE_HALF_SIZE &&
            local[1] > -0.1f && local[1] < maxHeight

    private fun isClearlyOutside(local: FloatArray) =
        local[1] > ENTER_HEIGHT + EXIT_MARGIN ||
            abs(local[0]) > ZONE_HALF_SIZE + EXIT_MARGIN ||
            abs(local[2]) > ZONE_HALF_SIZE + EXIT_MARGIN

    private fun lockPhone() {
        armed = false
        lastLocalPosition = null
        runOnUiThread { startActivity(Intent(this, LockActivity::class.java)) }
    }

    private fun showStatus(resId: Int) {
        if (resId == lastStatus) return
        lastStatus = resId
        runOnUiThread { statusText.setText(resId) }
    }
}
