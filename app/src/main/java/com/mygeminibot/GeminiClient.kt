
package com.mygeminibot

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class GeminiClient(private val apiKey: String) {

    companion object {
        private const val MODEL = "gemini-3.8-flash"
        private const val CONNECT_TIMEOUT = 30_000
        private const val READ_TIMEOUT = 120_000
    }

    suspend fun askText(prompt: String): String =
        request(prompt, null)

    suspend fun analyzeScreenshot(
        prompt: String,
        bitmap: Bitmap
    ): String = request(prompt, bitmap)

    private suspend fun request(
        prompt: String,
        bitmap: Bitmap?
    ): String = withContext(Dispatchers.IO) {

        require(apiKey.isNotBlank()) {
            "ჯერ შეიყვანე Gemini API გასაღები."
        }

        val endpoint =
            "https://generativelanguage.googleapis.com/" +
            "v1beta/models/$MODEL:generateContent"

        var connection: HttpURLConnection? = null

        try {
            connection = (URL(endpoint).openConnection()
                as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                doOutput = true
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )
                setRequestProperty("x-goog-api-key", apiKey)
            }

            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt))

            if (bitmap != null) {
                val image = Bitmap.createScaledBitmap(
                    bitmap,
                    minOf(bitmap.width, 1280),
                    minOf(bitmap.height, 1280),
                    true
                )
                val bytes = java.io.ByteArrayOutputStream()

                try {
                    image.compress(
                        Bitmap.CompressFormat.JPEG,
                        80,
                        bytes
                    )
                } finally {
                    if (image !== bitmap) image.recycle()
                }

                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put(
                                "data",
                                Base64.encodeToString(
                                    bytes.toByteArray(),
                                    Base64.NO_WRAP
                                )
                            )
                    )
                )
            }

            val body = JSONObject().put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", parts)
                )
            ).put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.2)
                    .put("maxOutputTokens", 1024)
            )

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val raw = stream?.bufferedReader()?.use {
                it.readText()
            }.orEmpty()

            if (code !in 200..299) {
                val message = try {
                    JSONObject(raw)
                        .optJSONObject("error")
                        ?.optString("message")
                        .orEmpty()
                } catch (_: Exception) {
                    ""
                }

                throw IOException(
                    "Gemini API შეცდომა ($code): " +
                    message.ifBlank { raw.take(400) }
                )
            }

            val root = JSONObject(raw)
            val candidates = root.optJSONArray("candidates")
                ?: throw IOException(
                    "Gemini-ის პასუხი არ მოიძებნა: ${raw.take(300)}"
                )

            val content = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?: throw IOException("Gemini-ის პასუხი ცარიელია.")

            val responseParts = content.optJSONArray("parts")
                ?: throw IOException("პასუხის ტექსტი არ მოიძებნა.")

            val answer = (0 until responseParts.length())
                .mapNotNull {
                    responseParts.optJSONObject(it)?.optString("text")
                }
                .filter { it.isNotBlank() }
                .joinToString("\n")

            answer.ifBlank {
                throw IOException("Gemini-მ ტექსტური პასუხი არ დააბრუნა.")
            }
        } catch (e: SocketTimeoutException) {
            throw IOException(
                "Gemini-ის პასუხს ძალიან დიდი დრო დასჭირდა. სცადე ხელახლა.",
                e
            )
        } finally {
            connection?.disconnect()
        }
    }
}
