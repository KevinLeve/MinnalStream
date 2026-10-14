package com.kevinleve.minnalstream

import android.app.*
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class StreamService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    private var socket: Socket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        createNotificationChannel()
        val notification = Notification.Builder(this, "minnalstream_stream")
            .setContentTitle("MinnalStream is streaming")
            .setContentText("Tap Stop in MinnalStream to end screen sharing")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(71, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(71, notification)
        val resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("projectionData", Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra("projectionData")
        val ip = intent.getStringExtra("ip") ?: "192.168.137.1"
        val port = intent.getIntExtra("port", 5000)
        val fps = intent.getIntExtra("fps", 60).coerceIn(15, 120)
        val maxWidth = intent.getIntExtra("maxWidth", 1920)
        if (data == null || resultCode != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        Thread {
            try { startStreaming(resultCode, data, ip, port, fps, maxWidth) }
            catch (_: Exception) { stopSelf() }
        }.start()
        return START_NOT_STICKY
    }

    private fun startStreaming(resultCode: Int, data: Intent, ip: String, port: Int, fps: Int, maxWidth: Int) {
        // Connect to the receiver first, so the user sees a clear connection failure rather than a frozen stream.
        val s = Socket()
        s.tcpNoDelay = true
        s.connect(InetSocketAddress(ip, port), 5000)
        socket = s
        val output = BufferedOutputStream(s.getOutputStream(), 256 * 1024)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val sourceW = metrics.widthPixels
        val sourceH = metrics.heightPixels
        val scale = minOf(1.0, maxWidth.toDouble() / sourceW.toDouble())
        var width = (sourceW * scale).toInt().coerceAtLeast(320)
        var height = (sourceH * scale).toInt().coerceAtLeast(320)
        width -= width % 2; height -= height % 2

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (width >= 1920) 12_000_000 else 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (Build.VERSION.SDK_INT >= 29) setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
        }
        val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec = c
        c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = c.createInputSurface()
        c.start()
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { running.set(false); stopSelf() }
        }, null)
        virtualDisplay = projection?.createVirtualDisplay("MinnalStream", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
        running.set(true)
        val info = MediaCodec.BufferInfo()
        var wroteConfig = false
        while (running.get()) {
            val index = c.dequeueOutputBuffer(info, 10_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outFormat = c.outputFormat
                for (key in listOf("csd-0", "csd-1")) {
                    val csd = outFormat.getByteBuffer(key)
                    if (csd != null) {
                        val copy = csd.duplicate(); val bytes = ByteArray(copy.remaining()); copy.get(bytes)
                        output.write(toAnnexB(bytes))
                    }
                }
                output.flush(); wroteConfig = true
            } else if (index >= 0) {
                val buffer = c.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size); buffer.get(bytes)
                    if (!wroteConfig && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        output.write(toAnnexB(bytes)); output.flush(); wroteConfig = true
                    } else if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        output.write(toAnnexB(bytes))
                        if ((info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) output.flush()
                    }
                }
                c.releaseOutputBuffer(index, false)
            }
        }
        try { output.flush() } catch (_: Exception) {}
    }

    // Convert AVCC length-prefixed NAL units to Annex B start-code framing when necessary.
    private fun toAnnexB(input: ByteArray): ByteArray {
        if (input.size >= 4 && input[0] == 0.toByte() && input[1] == 0.toByte() &&
            (input[2] == 1.toByte() || (input[2] == 0.toByte() && input[3] == 1.toByte()))) return input
        val out = java.io.ByteArrayOutputStream(input.size + 32)
        var pos = 0
        while (pos + 4 <= input.size) {
            val len = ((input[pos].toInt() and 255) shl 24) or ((input[pos+1].toInt() and 255) shl 16) or
                ((input[pos+2].toInt() and 255) shl 8) or (input[pos+3].toInt() and 255)
            if (len <= 0 || pos + 4 + len > input.size) { out.reset(); out.write(byteArrayOf(0,0,0,1)); out.write(input); return out.toByteArray() }
            out.write(byteArrayOf(0,0,0,1)); out.write(input, pos + 4, len); pos += 4 + len
        }
        if (pos != input.size) { out.reset(); out.write(byteArrayOf(0,0,0,1)); out.write(input) }
        return out.toByteArray()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel("minnalstream_stream", "MinnalStream streaming", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running.set(false)
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        try { surface?.release() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
