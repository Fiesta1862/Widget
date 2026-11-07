package com.screenbuddy.app

import android.animation.ObjectAnimator
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream

class OverlayService : Service() {

    companion object {
        const val ACTION_STOP = "com.screenbuddy.STOP"
        const val CHANNEL = "screenbuddy"
    }

    private val ui = Handler(Looper.getMainLooper())
    private var bgThread: HandlerThread? = null
    private var bg: Handler? = null

    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var dialog: LinearLayout
    private lateinit var textView: TextView
    private lateinit var overlayParams: WindowManager.LayoutParams
    private lateinit var quietButton: TextView
    private var quietMode = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startWindowX = 0
    private var startWindowY = 0
    private var touchStartedAt = 0L
    private var dragging = false

    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var latest: Image? = null
    private val lock = Any()

    private var blipTrack: AudioTrack? = null
    private var typeJob: Runnable? = null

    private var started = false
    private var busy = false
    private var overlayAdded = false

    private var apiKey = ""
    private var model = OpenRouter.DEFAULT_MODEL
    private var intervalSec = 30
    private lateinit var character: Character
    private val recent = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (started || intent == null) return START_NOT_STICKY
        started = true

        startAsForeground()

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        apiKey = prefs.getString("key", "") ?: ""
        model = prefs.getString("model", OpenRouter.DEFAULT_MODEL) ?: OpenRouter.DEFAULT_MODEL
        character = Characters.byId(prefs.getString("char", null))
        intervalSec = prefs.getInt("interval", 30)
        quietMode = prefs.getBoolean("quiet_mode", false)
        if (intervalSec in 1..9) intervalSec = 10

        bgThread = HandlerThread("screenbuddy-bg").also { it.start() }
        bg = Handler(bgThread!!.looper)
        blipTrack = makeBlip()

        buildOverlay()

        val code = intent.getIntExtra("code", Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data")
        if (data != null && setupProjection(code, data)) {
            say(character.greeting)
            if (intervalSec > 0) ui.postDelayed(auto, intervalSec * 1000L)
        } else {
            say("I can't see your screen. Open the app and press START again.")
        }
        return START_NOT_STICKY
    }

    // ---------- foreground notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "ScreenBuddy", NotificationManager.IMPORTANCE_LOW)
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("ScreenBuddy is running")
            .setContentText("Tap Stop to close your buddy")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    "Stop", stopPi
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    // ---------- screen capture ----------

    private fun setupProjection(code: Int, data: Intent): Boolean {
        return try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = mpm.getMediaProjection(code, data) ?: return false
            projection = p
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    ui.post { stopSelf() }
                }
            }, ui)

            val dm = resources.displayMetrics
            val s = minOf(1f, 1280f / maxOf(dm.widthPixels, dm.heightPixels))
            val w = (dm.widthPixels * s).toInt().coerceAtLeast(2)
            val h = (dm.heightPixels * s).toInt().coerceAtLeast(2)

            val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
            r.setOnImageAvailableListener({ rd ->
                try {
                    val img = rd.acquireLatestImage()
                    if (img != null) {
                        synchronized(lock) {
                            try { latest?.close() } catch (e: Exception) { }
                            latest = img
                        }
                    }
                } catch (e: Exception) { }
            }, bg)
            reader = r

            vd = p.createVirtualDisplay(
                "screenbuddy", w, h, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, bg
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Converts the newest captured frame to a base64 JPEG. */
    private fun grab(): String? {
        synchronized(lock) {
            val img = latest ?: return null
            val plane = img.planes[0]
            val rowPixels = plane.rowStride / plane.pixelStride
            val full = Bitmap.createBitmap(rowPixels, img.height, Bitmap.Config.ARGB_8888)
            plane.buffer.rewind()
            full.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(full, 0, 0, img.width, img.height)
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 70, out)
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }

    // ---------- the brain ----------

    private val auto = object : Runnable {
        override fun run() {
            if (!quietMode) askNow()
            if (intervalSec > 0) ui.postDelayed(this, intervalSec * 1000L)
        }
    }

    private fun systemPrompt(): String =
        character.persona + "\nRules: you are given a screenshot of the user's phone. React to what they are " +
            "doing in ONE or TWO short sentences (max 25 words). Stay fully in character. No emojis, no markdown, " +
            "no quotation marks around the reply. Never read out passwords, codes or private messages." +
            (if (recent.isNotEmpty()) "\nDon't repeat these earlier lines: " + recent.joinToString(" | ") else "")

    private fun askNow() {
        if (busy) return
        if (apiKey.isBlank()) { say("Put your OpenRouter key in the app first."); return }
        if (vd == null) { say("I can't see your screen. Restart me from the app."); return }
        busy = true

        // hide ourselves so the buddy doesn't appear in its own screenshot
        root.visibility = View.INVISIBLE
        bg?.postDelayed({
            val b64 = try { grab() } catch (e: Exception) { null }
            ui.post {
                root.visibility = View.VISIBLE
                say("...")
            }
            if (b64 == null) {
                ui.post { say("My eyes are blurry. Screen capture returned nothing."); busy = false }
            } else {
                try {
                    val reply = OpenRouter.ask(apiKey, model, systemPrompt(), b64)
                    recent.add(reply)
                    while (recent.size > 3) recent.removeAt(0)
                    ui.post { say(reply); busy = false }
                } catch (e: Exception) {
                    val msg = (e.message ?: e.javaClass.simpleName).take(140)
                    ui.post { say("Error: $msg"); busy = false }
                }
            }
        }, 350)
    }

    // ---------- the look ----------

    private fun pixelFont(): Typeface = try {
        Typeface.createFromAsset(assets, "pixel.ttf")
    } catch (e: Exception) {
        Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private fun crisp(bmp: Bitmap): BitmapDrawable {
        val d = BitmapDrawable(resources, bmp)
        d.setFilterBitmap(false)
        return d
    }

    private fun buildOverlay() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                setStroke(dp(3), Color.WHITE)
            }
        }
        val portrait = ImageView(this).apply {
            setImageDrawable(crisp(PixelArt.render(character, 4, character.portrait ?: character.sprite)))
            scaleType = ImageView.ScaleType.FIT_XY
        }
        box.addView(portrait, LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(10) })

        textView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 17f
            typeface = pixelFont()
        }
        box.addView(textView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.visibility = View.GONE
        box.setOnClickListener { hideDialog() }
        dialog = box

        val sprite = ImageView(this).apply {
            setImageDrawable(crisp(PixelArt.render(character, 8)))
            scaleType = ImageView.ScaleType.FIT_XY
            setOnClickListener { askNow() }
            setOnLongClickListener { stopSelf(); true }
        }
        ObjectAnimator.ofFloat(sprite, "translationY", 0f, -dp(6).toFloat()).apply {
            duration = 650
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        val boxWidth = minOf(dp(300), resources.displayMetrics.widthPixels - dp(16))
        root.addView(box, LinearLayout.LayoutParams(boxWidth, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(sprite, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
            gravity = Gravity.END
            topMargin = dp(4)
        })

        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.x = dp(8)
        lp.y = dp(80)
        try {
            wm.addView(root, lp)
            overlayAdded = true
        } catch (e: Exception) {
            stopSelf()
        }
    }

    private val hideRunnable = Runnable { hideDialog() }

    private fun hideDialog() {
        typeJob?.let { ui.removeCallbacks(it) }
        ui.removeCallbacks(hideRunnable)
        dialog.visibility = View.GONE
    }

    /** Undertale-style typewriter text with a voice blip. */
    private fun say(msg: String) {
        typeJob?.let { ui.removeCallbacks(it) }
        ui.removeCallbacks(hideRunnable)
        dialog.visibility = View.VISIBLE
        val full = "* $msg"
        var i = 0
        textView.text = ""
        val job = object : Runnable {
            override fun run() {
                if (i <= full.length) {
                    textView.text = full.substring(0, i)
                    if (i > 0 && i % 2 == 0 && full[i - 1] != ' ') blip()
                    i++
                    ui.postDelayed(this, 35)
                } else {
                    ui.postDelayed(hideRunnable, 9000)
                }
            }
        }
        typeJob = job
        ui.post(job)
    }

    private fun makeBlip(): AudioTrack? = try {
        val sr = 22050
        val n = sr * 40 / 1000
        val hz = character.blipHz.toDouble()
        val pcm = ShortArray(n) { k ->
            val phase = (k * hz / sr) % 1.0
            (if (phase < 0.5) 2500 else -2500).toShort()
        }
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, n)
        t
    } catch (e: Exception) {
        null
    }

    private fun blip() {
        try {
            blipTrack?.let {
                it.stop()
                it.reloadStaticData()
                it.play()
            }
        } catch (e: Exception) { }
    }

    // ---------- cleanup ----------

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        try { if (overlayAdded) wm.removeView(root) } catch (e: Exception) { }
        try { vd?.release() } catch (e: Exception) { }
        try { reader?.close() } catch (e: Exception) { }
        synchronized(lock) {
            try { latest?.close() } catch (e: Exception) { }
            latest = null
        }
        try { projection?.stop() } catch (e: Exception) { }
        try { blipTrack?.release() } catch (e: Exception) { }
        bgThread?.quitSafely()
        super.onDestroy()
    }
}
