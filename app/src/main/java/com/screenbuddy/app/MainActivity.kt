package com.screenbuddy.app

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 100
        private const val REQUEST_CUSTOM_IMAGE = 101
        private const val BG = 0xFF0B1020.toInt()
        private const val SURFACE = 0xFF151D30.toInt()
        private const val SURFACE_ALT = 0xFF1C2940.toInt()
        private const val TEXT = 0xFFF4F7FC.toInt()
        private const val MUTED = 0xFF9BA9BF.toInt()
        private const val ACCENT = 0xFF79E3D1.toInt()
        private const val BORDER = 0xFF2A3852.toInt()
    }

    private lateinit var keyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var charSpinner: Spinner
    private lateinit var customNameInput: EditText
    private lateinit var customPersonalityInput: EditText
    private lateinit var customImageStatus: TextView
    private lateinit var touchThroughSwitch: Switch
    private lateinit var previewImage: ImageView
    private lateinit var selectedCharacterName: TextView
    private lateinit var selectedCharacterDescription: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != 0) setStroke(dp(1), stroke)
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }

    private fun vertical(gap: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (gap > 0) showDividers(LinearLayout.SHOW_DIVIDER_NONE)
    }

    private fun sectionCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(18))
        background = rounded(SURFACE, 20, BORDER)
    }

    private fun sectionTitle(title: String, subtitle: String? = null): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 16f, TEXT, true))
            if (!subtitle.isNullOrBlank()) {
                addView(text(subtitle, 12f, MUTED).apply {
                    setPadding(0, dp(4), 0, 0)
                })
            }
        }

    private fun fieldLabel(value: String): TextView = text(value, 12f, MUTED, true).apply {
        setPadding(0, dp(15), 0, dp(7))
    }

    private fun styledInput(value: String, hintText: String, password: Boolean = false): EditText =
        EditText(this).apply {
            setText(value)
            hint = hintText
            setHintTextColor(0xFF718099.toInt())
            setTextColor(TEXT)
            textSize = 14f
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = rounded(SURFACE_ALT, 12, BORDER)
            if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else setSingleLine(true)
        }

    private fun addGap(parent: LinearLayout, height: Int = 12) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun actionButton(title: String, primary: Boolean, onClick: () -> Unit): Button =
        Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(if (primary) 0xFF081A1C.toInt() else TEXT)
            background = rounded(if (primary) ACCENT else SURFACE_ALT, 14, if (primary) 0 else BORDER)
            setPadding(dp(16), dp(7), dp(16), dp(7))
            setOnClickListener { onClick() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(20))
            addView(page)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
        }
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brandMark = text("SB", 15f, BG, true).apply {
            gravity = Gravity.CENTER
            background = rounded(ACCENT, 12)
        }
        brandRow.addView(brandMark, LinearLayout.LayoutParams(dp(42), dp(42)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
            addView(text("SCREENBUDDY", 11f, ACCENT, true))
            addView(text("PIXEL COMPANION STUDIO", 9f, MUTED, true).apply {
                setPadding(0, dp(3), 0, 0)
            })
        }
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        brandRow.addView(text("v2.0", 11f, MUTED).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = rounded(SURFACE_ALT, 20)
        })
        header.addView(brandRow)
        header.addView(text("Your little buddy.\nA whole new look.", 29f, TEXT, true).apply {
            setPadding(0, dp(24), 0, dp(8))
            letterSpacing = -0.02f
        })
        header.addView(text("Customize a pixel companion that lives above your apps and reacts to your screen.", 14f, MUTED))
        page.addView(header)

        // Character showcase
        val hero = sectionCard().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(0xFF172B3A.toInt(), 22, 0xFF2D5660.toInt())
        }
        val heroInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("YOUR COMPANION", 10f, ACCENT, true))
            selectedCharacterName = text(Characters.all[0].name, 20f, TEXT, true)
            selectedCharacterName.setPadding(0, dp(7), 0, dp(4))
            addView(selectedCharacterName)
            selectedCharacterDescription = text("Ready to explore your screen.", 12f, 0xFFB7C8D4.toInt())
            addView(selectedCharacterDescription)
            addView(text("CUSTOMIZABLE • PIXEL ART • AI REACTIONS", 9f, MUTED, true).apply {
                setPadding(0, dp(12), 0, 0)
            })
        }
        hero.addView(heroInfo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        previewImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        hero.addView(previewImage, LinearLayout.LayoutParams(dp(112), dp(112)))
        page.addView(hero, pageMarginParams(22, 0, 22, 18))

        // Character settings
        val characterCard = sectionCard()
        characterCard.addView(sectionTitle("01  Character", "Choose a buddy or create one from your own image."))
        addGap(characterCard, 8)
        charSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                Characters.all.map { it.name } + "Custom image"
            )
            background = rounded(SURFACE_ALT, 12, BORDER)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        val savedId = prefs.getString("char", Characters.all[0].id)
        val selectedIndex = if (savedId == "custom") Characters.all.size
            else Characters.all.indexOfFirst { it.id == savedId }.coerceAtLeast(0)
        charSpinner.setSelection(selectedIndex)
        charSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refreshCharacterPreview(position)
            }
        }
        characterCard.addView(charSpinner, matchWrap())
        characterCard.addView(fieldLabel("CUSTOM CHARACTER IMAGE"))
        customImageStatus = text(
            if (prefs.getString("custom_image_uri", null).isNullOrBlank()) "No image selected yet"
            else "A custom image is saved on this device",
            12f, MUTED
        )
        characterCard.addView(customImageStatus)
        addGap(characterCard, 9)
        val imageActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        imageActions.addView(actionButton("Import image", false) { chooseCustomImage() },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        imageActions.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        imageActions.addView(actionButton("Clear", false) {
            getSharedPreferences("prefs", MODE_PRIVATE).edit().remove("custom_image_uri").apply()
            customImageStatus.text = "No image selected yet"
            refreshCharacterPreview(charSpinner.selectedItemPosition)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        characterCard.addView(imageActions)
        characterCard.addView(fieldLabel("CHARACTER NAME"))
        customNameInput = styledInput(prefs.getString("custom_name", "") ?: "", "Example: Pixel Fox")
        characterCard.addView(customNameInput, matchWrap())
        page.addView(characterCard, pageMarginParams(22, 0, 22, 14))

        // Personality
        val personalityCard = sectionCard()
        personalityCard.addView(sectionTitle("02  Personality", "Give your buddy a distinct voice and behavior."))
        personalityCard.addView(fieldLabel("CUSTOM PERSONALITY"))
        customPersonalityInput = styledInput(
            prefs.getString("custom_personality", "") ?: "",
            "Curious, funny, calm, encouraging...",
        ).apply {
            setSingleLine(false)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            maxLines = 7
        }
        personalityCard.addView(customPersonalityInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        page.addView(personalityCard, pageMarginParams(22, 0, 22, 14))

        // AI connection
        val aiCard = sectionCard()
        aiCard.addView(sectionTitle("03  AI connection", "Screen reactions are powered by your OpenRouter account."))
        aiCard.addView(fieldLabel("OPENROUTER API KEY"))
        keyInput = styledInput(prefs.getString("key", "") ?: "", "Paste your API key", true)
        aiCard.addView(keyInput, matchWrap())
        aiCard.addView(fieldLabel("VISION MODEL"))
        modelInput = styledInput(
            prefs.getString("model", OpenRouter.DEFAULT_MODEL) ?: OpenRouter.DEFAULT_MODEL,
            "Model must support image input"
        )
        aiCard.addView(modelInput, matchWrap())
        page.addView(aiCard, pageMarginParams(22, 0, 22, 14))

        // Behavior
        val behaviorCard = sectionCard()
        behaviorCard.addView(sectionTitle("04  Behavior", "Tune when your companion reacts and how it handles touch."))
        behaviorCard.addView(fieldLabel("AUTO-REACTION INTERVAL"))
        intervalInput = styledInput(prefs.getInt("interval", 30).toString(), "Seconds").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        behaviorCard.addView(intervalInput, LinearLayout.LayoutParams(dp(120), dp(50)))
        behaviorCard.addView(text("Set to 0 to turn off automatic reactions.", 11f, MUTED).apply {
            setPadding(0, dp(6), 0, 0)
        })
        val touchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(18), 0, 0)
        }
        val touchCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text("Touch-through mode", 14f, TEXT, true))
            addView(text("Taps pass through to the app underneath. Turn off to drag the buddy.", 11f, MUTED).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        touchRow.addView(touchCopy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        touchThroughSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("touch_through", true)
            buttonTintList = android.content.res.ColorStateList.valueOf(ACCENT)
        }
        touchRow.addView(touchThroughSwitch)
        behaviorCard.addView(touchRow)
        page.addView(behaviorCard, pageMarginParams(22, 0, 22, 14))

        // Start / stop actions
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(6), dp(22), dp(10))
        }
        actions.addView(actionButton("Start ScreenBuddy  →", true) { startBuddy() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        addGap(actions, 9)
        actions.addView(actionButton("Stop companion", false) {
            stopService(Intent(this@MainActivity, OverlayService::class.java))
            Toast.makeText(this@MainActivity, "ScreenBuddy stopped", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        actions.addView(text("PRIVACY NOTE", 10f, ACCENT, true).apply {
            setPadding(0, dp(20), 0, dp(5))
        })
        actions.addView(text(
            "Screen captures are sent to OpenRouter when a reaction is requested. Avoid showing passwords, private chats, or sensitive information. Your imported character image is pixelated on-device.",
            11f, MUTED
        ))
        actions.addView(text("SCREENBUDDY  •  MADE FOR YOUR SCREEN", 9f, 0xFF65738B.toInt(), true).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(8))
        })
        page.addView(actions)

        setContentView(scroll)
        refreshCharacterPreview(selectedIndex)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 2)
        }
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun pageMarginParams(left: Int, top: Int, right: Int, bottom: Int) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }

    private fun refreshCharacterPreview(position: Int) {
        if (!::previewImage.isInitialized) return
        if (position == Characters.all.size) {
            val uriText = getSharedPreferences("prefs", MODE_PRIVATE).getString("custom_image_uri", null)
            val bitmap = try {
                if (uriText.isNullOrBlank()) null
                else contentResolver.openInputStream(Uri.parse(uriText))?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) { null }
            if (bitmap != null) {
                val pixel = PixelArt.pixelate(bitmap, 16)
                if (!bitmap.isRecycled) bitmap.recycle()
                previewImage.setImageBitmap(PixelArt.scaleNearest(pixel, 5))
                pixel.recycle()
                selectedCharacterName.text = getSharedPreferences("prefs", MODE_PRIVATE)
                    .getString("custom_name", "")?.ifBlank { "Custom buddy" } ?: "Custom buddy"
                selectedCharacterDescription.text = "Your imported pixel character."
            } else {
                previewImage.setImageDrawable(null)
                selectedCharacterName.text = "Custom buddy"
                selectedCharacterDescription.text = "Import an image to preview your character."
            }
        } else {
            val character = Characters.all.getOrNull(position) ?: Characters.all[0]
            previewImage.setImageBitmap(PixelArt.render(character, 6, character.portrait ?: character.sprite))
            selectedCharacterName.text = character.name
            selectedCharacterDescription.text = character.greeting
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
            .putBoolean("touch_through", touchThroughSwitch.isChecked)
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
                    customImageStatus.text = "Custom image saved on this device"
                    refreshCharacterPreview(charSpinner.selectedItemPosition)
                } catch (_: Exception) {
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
