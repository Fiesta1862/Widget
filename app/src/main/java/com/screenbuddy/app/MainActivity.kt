package com.screenbuddy.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var charSpinner: Spinner

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.LTGRAY)
        textSize = 13f
        setPadding(0, dp(14), 0, dp(2))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(30))
        }

        col.addView(TextView(this).apply {
            text = "ScreenBuddy"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        col.addView(TextView(this).apply {
            text = "A pixel buddy that floats over your apps and comments on what you're doing."
            setTextColor(Color.GRAY)
        })

        col.addView(label("OpenRouter API key"))
        keyInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
            hint = "sk-or-..."
        }
        col.addView(keyInput)

        col.addView(label("Model (must support images)"))
        modelInput = EditText(this).apply {
            setText(prefs.getString("model", OpenRouter.DEFAULT_MODEL))
            setSingleLine(true)
        }
        col.addView(modelInput)

        col.addView(label("Character"))
        charSpinner = Spinner(this)
        charSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Characters.all.map { it.name }
        )
        val savedId = prefs.getString("char", Characters.all[0].id)
        charSpinner.setSelection(Characters.all.indexOfFirst { it.id == savedId }.coerceAtLeast(0))
        col.addView(charSpinner)

        col.addView(label("Look at my screen every N seconds (0 = only when I tap the character)"))
        intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("interval", 30).toString())
        }
        col.addView(intervalInput)

        val start = Button(this).apply {
            text = "START BUDDY"
            setOnClickListener { start() }
        }
        col.addView(start, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        val stop = Button(this).apply {
            text = "STOP BUDDY"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        }
        col.addView(stop)

        col.addView(TextView(this).apply {
            text = "Tap the character = look at my screen now.\nTap the text box = hide it.\nLong-press the character = quit.\n\n" +
                "Screens are sent to OpenRouter, so don't use it while showing private info."
            setTextColor(Color.GRAY)
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(col) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    private fun start() {
        val key = keyInput.text.toString().trim()
        if (key.isEmpty()) {
            Toast.makeText(this, "Enter your OpenRouter API key first", Toast.LENGTH_LONG).show()
            return
        }
        getSharedPreferences("prefs", MODE_PRIVATE).edit()
            .putString("key", key)
            .putString("model", modelInput.text.toString().trim().ifEmpty { OpenRouter.DEFAULT_MODEL })
            .putString("char", Characters.all[charSpinner.selectedItemPosition].id)
            .putInt("interval", intervalInput.text.toString().toIntOrNull() ?: 30)
            .apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\", then press START again", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), 100)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            if (resultCode == RESULT_OK && data != null) {
                val i = Intent(this, OverlayService::class.java)
                    .putExtra("code", resultCode)
                    .putExtra("data", data)
                startForegroundService(i)
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Screen capture permission is needed so the buddy can see.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
