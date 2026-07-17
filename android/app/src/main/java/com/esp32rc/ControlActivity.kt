package com.esp32rc

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.esp32rc.model.MotorCommand
import com.esp32rc.network.MjpegStreamer
import com.esp32rc.network.MotorClient
import com.esp32rc.ui.JoystickView

class ControlActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"
        private const val COMMAND_INTERVAL_MS = 100L
        private const val MIN_SEND_INTERVAL_MS = 20L
    }

    private lateinit var videoView: ImageView
    private lateinit var statusText: TextView
    private lateinit var joystick: JoystickView

    private var streamer: MjpegStreamer? = null
    private var motorClient: MotorClient? = null

    private val commandHandler = Handler(Looper.getMainLooper())
    private var pendingCommand = MotorCommand.STOP
    private var lastSentCommand: MotorCommand? = null
    private var lastCommandTime = 0L

    private val commandTicker = object : Runnable {
        override fun run() {
            sendPending(keepAlive = true)
            commandHandler.postDelayed(this, COMMAND_INTERVAL_MS)
        }
    }

    private fun sendPending(keepAlive: Boolean = false) {
        val command = pendingCommand
        val now = System.currentTimeMillis()
        val changed = command != lastSentCommand
        if (!changed && !keepAlive) return
        if (changed && !keepAlive && now - lastCommandTime < MIN_SEND_INTERVAL_MS) return
        motorClient?.sendCommand(command.left, command.right)
        lastSentCommand = command
        lastCommandTime = now
    }

    private val ip: String by lazy { intent.getStringExtra(EXTRA_IP) ?: "192.168.4.1" }
    private val port: Int by lazy { intent.getIntExtra(EXTRA_PORT, 80) }
    private val baseUrl: String by lazy { "http://$ip:$port" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)

        videoView = findViewById(R.id.videoView)
        statusText = findViewById(R.id.statusText)
        joystick = findViewById(R.id.joystick)

        hideSystemBars()

        joystick.onJoystickChanged = { x, y ->
            pendingCommand = MotorCommand.fromJoystick(x, y)
            sendPending()
        }
    }

    override fun onResume() {
        super.onResume()
        motorClient = MotorClient(baseUrl)
        startStream()
        commandHandler.post(commandTicker)
    }

    override fun onPause() {
        super.onPause()
        commandHandler.removeCallbacks(commandTicker)
        motorClient?.sendCommand(0, 0)
        stopStream()
    }

    override fun onStop() {
        super.onStop()
        motorClient?.shutdown()
        motorClient = null
    }

    private fun startStream() {
        statusText.visibility = View.VISIBLE
        statusText.text = getString(R.string.status_connecting)
        streamer = MjpegStreamer(
            url = "$baseUrl/stream",
            onFrame = { bitmap ->
                statusText.visibility = View.GONE
                videoView.setImageBitmap(bitmap)
            },
            onError = {
                statusText.visibility = View.VISIBLE
                statusText.text = getString(R.string.status_no_signal)
            }
        ).also { it.start() }
    }

    private fun stopStream() {
        streamer?.stopStream()
        streamer = null
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
