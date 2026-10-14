package com.kevinleve.minnalstream

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var ipInput: EditText
    private lateinit var portInput: EditText
    private lateinit var fpsSpinner: Spinner
    private lateinit var sizeSpinner: Spinner
    private lateinit var status: TextView
    private val captureRequest = 4210

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF0F141F.toInt())
        }
        fun label(text: String, size: Float = 15f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(0xFFF0F5FC.toInt())
            setPadding(0, pad / 3, 0, pad / 3)
        }
        val title = label("MinnalStream", 28f).apply { setTypeface(null, android.graphics.Typeface.BOLD) }
        root.addView(title)
        root.addView(label("Wireless game streaming to your Windows hotspot"))
        root.addView(label("Laptop hotspot IP address"))
        ipInput = EditText(this).apply {
            setSingleLine(true); setText("192.168.137.1"); textSize = 17f
            setTextColor(0xFF111827.toInt()); setHintTextColor(0xFF64748B.toInt()); setHint("e.g. 192.168.137.1")
        }
        root.addView(ipInput, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(label("Receiver port"))
        portInput = EditText(this).apply {
            setSingleLine(true); setText("5000"); inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFF111827.toInt()); textSize = 17f
        }
        root.addView(portInput)
        root.addView(label("Frame rate target"))
        fpsSpinner = Spinner(this)
        fpsSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("60 FPS (recommended)", "120 FPS (if supported)"))
        root.addView(fpsSpinner)
        root.addView(label("Video size"))
        sizeSpinner = Spinner(this)
        sizeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("1080p", "720p (lower latency)"))
        root.addView(sizeSpinner)
        val start = Button(this).apply {
            text = "Start wireless streaming"
            setOnClickListener { requestCapture() }
        }
        root.addView(start, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = pad })
        val stop = Button(this).apply {
            text = "Stop streaming"
            setOnClickListener { stopService(Intent(this@MainActivity, StreamService::class.java)); status.text = "Streaming stopped." }
        }
        root.addView(stop)
        status = label("Setup: connect this phone to your laptop hotspot, open the Windows receiver, then tap Start. Android will ask permission to share your screen.")
        status.textSize = 14f
        root.addView(status)
        root.addView(label("No USB or Developer Options required. Screen sharing requires Android's permission prompt. Audio is not included in this preview.", 12f))
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun requestCapture() {
        val ip = ipInput.text.toString().trim()
        val port = portInput.text.toString().trim().toIntOrNull()
        if (ip.isBlank() || port == null || port !in 1..65535) {
            Toast.makeText(this, "Enter a valid laptop IP address and port", Toast.LENGTH_LONG).show(); return
        }
        val fps = if (fpsSpinner.selectedItemPosition == 1) 120 else 60
        val maxWidth = if (sizeSpinner.selectedItemPosition == 1) 1280 else 1920
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mgr.createScreenCaptureIntent()
        startActivityForResult(intent, captureRequest)
        getPreferences(MODE_PRIVATE).edit().putString("ip", ip).putInt("port", port).putInt("fps", fps).putInt("maxWidth", maxWidth).apply()
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequest) return
        if (resultCode != RESULT_OK || data == null) {
            status.text = "Screen capture permission was not granted."; return
        }
        val prefs = getPreferences(MODE_PRIVATE)
        val service = Intent(this, StreamService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("projectionData", data)
            putExtra("ip", prefs.getString("ip", "192.168.137.1"))
            putExtra("port", prefs.getInt("port", 5000))
            putExtra("fps", prefs.getInt("fps", 60))
            putExtra("maxWidth", prefs.getInt("maxWidth", 1920))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
        status.text = "Streaming service starting. Keep the screen-sharing notification active."
    }
}
