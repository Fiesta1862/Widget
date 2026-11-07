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
import android.view.Gravity
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

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 100
        private const val REQUEST_CUSTOM_IMAGE = 101
    }

    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var charSpinner: Spinner
    private lateinit var customNameInput: EditText
    private lateinit var customPersonalityInput: EditText
    private lateinit var customImageStatus: TextView

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
            setPadding(dp(20), dp(32), dp(20), dp(30))
        }

        col.addView(TextView(this).apply {
            text = "ScreenBuddy"
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        col.addView(TextView(this).apply {
            text = "A pixel companion that floats over your apps and reacts to what you're doing."
            setTextColor(Color.GRAY)
        })

        col.addView(label("OpenRouter API key"))
        keyInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("key", ""))
            hint = "sk-or-..."
        }
        col.addView(keyInput)

        col.addView(label("Vision model"))
        modelInput = EditText(this).apply {
            setText(prefs.getString("model", OpenRouter.DEFAULT_MODEL))
            setSingleLine(true)
            hint = "Model must support images"
        }
        col.addView(modelInput)

        col.addView(label("Character"))
        charSpinner = Spinner(this)
        val characterNames = Characters.all.map { it.name } + "Custom image"
        charSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, characterNames
        )
        val savedId = prefs.getString("char", Characters.all[0].id)
        val selectedIndex = if (savedId == "custom") Characters.all.size
            else Characters.all.indexOfFirst { it.id == savedId }.coerceAtLeast(0)
        charSpinner.setSelection(selectedIndex)
        col.addView(charSpinner)

        col.addView(label("Custom character image"))
        customImageStatus = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            text = if (prefs.getString("custom_image_uri", null).isNullOrBlank())
                "No custom image selected"
            else "Custom image saved on this device"
        }
        col.addView(customImageStatus)

        col.addView(Button(this).apply {
            text = "IMPORT IMAGE FROM PHONE"
            setOnClickListener { chooseCustomImage() }
        })
        col.addView(Button(this).apply {
            text = "CLEAR CUSTOM IMAGE"
            setOnClickListener {
                getSharedPreferences("prefs", MODE_PRIVATE).edit()
                    .remove("custom_image_uri").apply()
                customImageStatus.text = "No custom image selected"
            }
        })

        col.addView(label("Custom character name"))
        customNameInput = EditText(this).apply {
            setText(prefs.getString("custom_name", ""))
            hint = "Example: Pixel Fox"
            setSingleLine(true)
        }
        col.addView(customNameInput)

        col.addView(label("Custom personality"))
        customPersonalityInput = EditText(this).apply {
            setText(prefs.getString("custom_personality", ""))
            hint = "Describe how your buddy speaks, behaves, and reacts..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            minLines = 3
            maxLines = 6
        }
        col.addView(customPersonalityInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        col.addView(label("Automatic screen reactions (seconds; 0 = tap character manually)"))
        intervalInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("interval", 30).toString())
        }
        col.addView(intervalInput)

        col.addView(Button(this).apply {
            text = "START BUDDY"
            setOnClickListener { startBuddy() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(24) })

        col.addView(Button(this).apply {
            text = "STOP BUDDY"
            setOnClickListener { stopService(Intent(this@MainActivity, OverlayService::class.java)) }
        })

        col.addView(TextView(this).apply {
            text = "Import an image to make a custom pixel character. The image is converted on your device. " +
                "Write a personality to customize the AI's voice and behavior. Screen captures are sent to OpenRouter " +
                "when the buddy reacts, so avoid displaying private information."
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

    private fun chooseCustomImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CUSTOM_IMAGE)
    }

    private fun startBuddy() {
        val key = keyInput.text.toString().trim()
        if (key.isEmpty()) {
            Toast.makeText(this, "Enter your OpenRouter key first", Toast.LENGTH_LONG).show()
            return
        }
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val selectedIndex = charSpinner.selectedItemPosition
        val selectedId = if (selectedIndex == Characters.all.size) "custom"
            else Characters.all.getOrNull(selectedIndex)?.id ?: Characters.all[0].id
        if (selectedId == "custom" && prefs.getString("custom_image_uri", null).isNullOrBlank()) {
            Toast.makeText(this, "Import a custom character image first", Toast.LENGTH_LONG).show()
            return
        }
        val interval = (intervalInput.text.toString().toIntOrNull() ?: 30).coerceIn(0, 3600)
        prefs.edit()
            .putString("key", key)
            .putString("model", modelInput.text.toString().trim().ifEmpty { OpenRouter.DEFAULT_MODEL })
            .putString("char", selectedId)
            .putString("custom_name", customNameInput.text.toString().trim().take(32))
            .putString("custom_personality", customPersonalityInput.text.toString().trim().take(1200))
            .putInt("interval", interval)
            .apply()

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow Display over other apps, then press START again", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CUSTOM_IMAGE) {
            if (resultCode == RESULT_OK && data?.data != null) {
                val uri = data.data!!
                try {
                    val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(uri, takeFlags)
                    getSharedPreferences("prefs", MODE_PRIVATE).edit()
                        .putString("custom_image_uri", uri.toString()).apply()
                    customImageStatus.text = "Custom image saved: ${uri.lastPathSegment ?: "image"}"
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not keep access to that image. Try another image.", Toast.LENGTH_LONG).show()
                }
            }
            return
        }
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val intent = Intent(this, OverlayService::class.java)
                    .putExtra("code", resultCode)
                    .putExtra("data", data)
                startForegroundService(intent)
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Screen capture permission is needed to start the buddy.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
