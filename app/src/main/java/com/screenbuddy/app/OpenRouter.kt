package com.screenbuddy.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OpenRouter {
    const val DEFAULT_MODEL = "google/gemini-2.5-flash"

    /** Sends the screenshot (base64 JPEG) to OpenRouter. Throws on any failure. */
    fun ask(apiKey: String, model: String, system: String, jpegBase64: String): String {
        val userContent = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "Here is my screen right now. React in character."))
            .put(
                JSONObject().put("type", "image_url").put(
                    "image_url", JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")
                )
            )
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", userContent))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 150)
            .put("messages", messages)

        val conn = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("HTTP-Referer", "https://github.com/screenbuddy")
            conn.setRequestProperty("X-Title", "ScreenBuddy")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("HTTP $code ${text.take(160)}")

            val msg = JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val out = msg.optString("content", "").trim()
            if (out.isEmpty()) throw Exception("Empty reply (is the model vision-capable?)")
            return out
        } finally {
            conn.disconnect()
        }
    }
}
