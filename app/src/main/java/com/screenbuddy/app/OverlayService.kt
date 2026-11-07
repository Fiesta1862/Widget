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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import android.net.Uri
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
    private var customSprite: Bitmap? = null
    private var customName = ""
    private var customPersonality = ""
    private var customImageError = false
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
        val selectedCharacter = prefs.getString("char", null)
        character = Characters.byId(selectedCharacter)
        customName = if (selectedCharacter == "custom") prefs.getString("custom_name", "")?.trim().orEmpty() else ""
        customPersonality = prefs.getString("custom_personality", "")?.trim().orEmpty()
        if (selectedCharacter == "custom") {
            val imageUri = prefs.getString("custom_image_uri", null)
            customSprite = imageUri?.let { loadCustomSprite(it) }
            customImageError = customSprite == null
        }
        intervalSec = prefs.getInt("interval", 30)
        if (intervalSec in 1..9) intervalSec = 10

        bgThread = HandlerThread("screenbuddy-bg").also { it.start() }
        bg = Handler(bgThread!!.looper)
        blipTrack = makeBlip()

        buildOverlay()

        val code = intent.getIntExtra("code", Activity.RESULT_CANCELED)
        @Suppress("DEPRECATION")
        val data = intent.getParcelableExtra<Intent>("data")
        if (data != null && setupProjection(code, data)) {
            say(if (customSprite != null) {
                "Hey, I'm ${customName.ifBlank { "your custom buddy" }}. Let's go!"
            } else if (customImageError) {
                "I couldn't load that custom image, so I'm using the selected built-in character."
            } else character.greeting)
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
            askNow()
            if (intervalSec > 0) ui.postDelayed(this, intervalSec * 1000L)
        }
    }

    private fun systemPrompt(): String {
        val identity = if (customName.isNotBlank()) "Your character name is $customName. " else ""
        val persona = customPersonality.ifBlank { character.persona }
        return identity + persona + "\nRules: you are given a screenshot of the user's phone. React in ONE OR TWO short sentences (max 25 words). " +
            "Be spontaneous, playful, and context-aware: make a fresh observation, reaction, question, encouragement, or joke connected to what is actually visible. " +
            "Vary your wording and mood so you feel like a lively companion, not a repetitive screen narrator. " +
            "Sometimes use a light, playful apology as a random catchphrase, such as 'Oops, sorry for blocking your keys!', but never claim you really blocked anything. " +
            "Do not assume actions that are not visible. If the user is typing, comment briefly about the visible activity without asking them to stop. " +
            "No emojis, no markdown, no quotation marks. Never read out passwords, codes, or private messages. " +
            "Do not output control tokens." +
            (if (recent.isNotEmpty()) "\\nAvoid repeating these recent lines or their phrasing: " + recent.joinToString(" | ") else "")
    }

    private fun hasValidatedInternet(): Boolean {
        return try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { false }
    }

    private fun offlineLine(): String {
        val lines = listOf(
            "I'm still here. My online screen-reading is paused until the connection returns.",
            "Offline mode is on. I'll keep hanging out while you use your phone.",
            "No internet right now, but your pixel buddy is still active.",
            "I can still animate and stay on screen. Online reactions will return when you're connected."
        )
        return lines[(System.currentTimeMillis() / 1000L % lines.size).toInt()]
    }

    private fun askNow() {
        if (busy) return
        if (apiKey.isBlank() || !hasValidatedInternet()) {
            say(offlineLine())
            return
        }
        if (vd == null) { say("Screen viewing is unavailable. Your buddy is still here offline."); return }
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
                    while (recent.size > 5) recent.removeAt(0)
                    ui.post { say(reply); busy = false }
                } catch (e: Exception) {
                    val msg = (e.message ?: e.javaClass.simpleName).take(140)
                    ui.post { say("Error: $msg"); busy = false }
                }
            }
        }, 350)
    }

    /** Load an imported image safely and turn it into a small pixel-art sprite. */
    private fun loadCustomSprite(uriText: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriText)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 512) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return null
            val pixelSprite = PixelArt.pixelate(decoded, 16)
            if (decoded !== pixelSprite && !decoded.isRecycled) decoded.recycle()
            pixelSprite
        } catch (_: Exception) {
            null
        }
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
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val touchThrough = prefs.getBoolean("touch_through", true)

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
            setImageDrawable(customSprite?.let { crisp(PixelArt.scaleNearest(it, 4)) } ?: crisp(PixelArt.render(character, 4, character.portrait ?: character.sprite)))
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
        // The overlay is deliberately non-interactive: taps pass through to the app below.
        dialog = box

        val sprite = ImageView(this).apply {
            setImageDrawable(customSprite?.let { crisp(PixelArt.scaleNearest(it, 8)) } ?: crisp(PixelArt.render(character, 8)))
            scaleType = ImageView.ScaleType.FIT_XY
            // No touch listener: WindowManager marks the whole overlay not-touchable below.
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
        val characterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        characterRow.addView(sprite, LinearLayout.LayoutParams(dp(72), dp(72)))
        root.addView(characterRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.END; topMargin = dp(4) })
        overlayParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                (if (touchThrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
            PixelFormat.TRANSLUCENT
        )
        overlayParams.gravity = Gravity.BOTTOM or Gravity.END
        val screen = resources.displayMetrics
        val maxX = (screen.widthPixels - dp(88)).coerceAtLeast(0)
        val minY = 0
        val maxY = (screen.heightPixels - dp(88)).coerceAtLeast(0)
        // Start near the top-right; dragging can move the buddy across the full screen.
        overlayParams.x = prefs.getInt("overlay_x", dp(8)).coerceIn(0, maxX)
        overlayParams.y = prefs.getInt("overlay_y", maxY).coerceIn(minY, maxY)

        if (!touchThrough) {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var moved = false
            sprite.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = overlayParams.x
                        startY = overlayParams.y
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (kotlin.math.abs(dx) > dp(3) || kotlin.math.abs(dy) > dp(3) || moved) {
                            moved = true
                            val maxX = (screen.widthPixels - dp(88)).coerceAtLeast(0)
                            val maxY = (screen.heightPixels - dp(88)).coerceAtLeast(0)
                            overlayParams.x = (startX - dx.toInt()).coerceIn(0, maxX)
                            overlayParams.y = (startY - dy.toInt()).coerceIn(0, maxY)
                            try { wm.updateViewLayout(root, overlayParams) } catch (_: Exception) { }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (moved) {
                            prefs.edit()
                                .putInt("overlay_x", overlayParams.x)
                                .putInt("overlay_y", overlayParams.y)
                                .apply()
                        }
                        true
                    }
                    else -> true
                }
            }
        }
        try {
            wm.addView(root, overlayParams)
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
        try { customSprite?.recycle() } catch (e: Exception) { }
        bgThread?.quitSafely()
        super.onDestroy()
    }
}
