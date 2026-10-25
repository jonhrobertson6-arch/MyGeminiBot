package com.mygeminibot

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var apiCountText: TextView
    private lateinit var logText: TextView
    private lateinit var apiKeyInput: EditText
    private var apiRequestCount = 0
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("bot_prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""

        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = "MyGeminiBot - პანელი"
            textSize = 22f
            setPadding(0, 0, 0, 24)
        }

        statusText = TextView(this).apply {
            text = "სტატუსი: მზადყოფნაში (Idle)"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }

        apiCountText = TextView(this).apply {
            text = "Gemini API მოთხოვნები: 0"
            textSize = 15f
            setPadding(0, 0, 0, 32)
        }

        val keyLabel = TextView(this).apply {
            text = "Gemini API გასაღები (Key):"
            textSize = 14f
        }

        apiKeyInput = EditText(this).apply {
            hint = "ჩასვით AIza... გასაღები აქ"
            setText(savedKey)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val saveKeyBtn = Button(this).apply {
            text = "გასაღების შენახვა"
            setOnClickListener {
                val key = apiKeyInput.text.toString().trim()
                prefs.edit().putString("gemini_api_key", key).apply()
                addLog("API გასაღები შენახულია ტელეფონში.")
                Toast.makeText(this@MainActivity, "შენახულია!", Toast.LENGTH_SHORT).show()
            }
        }

        val connectGeminiBtn = Button(this).apply {
            text = "Connect Gemini (კავშირის ტესტი)"
            setOnClickListener {
                val key = apiKeyInput.text.toString().trim()
                if (key.isEmpty()) {
                    addLog("შეცდომა: გთხოვთ, ჯერ ჩაწეროთ API გასაღები.")
                } else {
                    prefs.edit().putString("gemini_api_key", key).apply()
                    testGeminiConnection(key)
                }
            }
        }

        val logLabel = TextView(this).apply {
            text = "\nActivity Log (მოქმედებების ჟურნალი):"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        logText = TextView(this).apply {
            text = "აპლიკაცია ჩაირთო.\n"
            textSize = 13f
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setTextColor(Color.parseColor("#00FF88"))
            setPadding(24, 24, 24, 24)
        }

        layout.addView(title)
        layout.addView(statusText)
        layout.addView(apiCountText)
        layout.addView(keyLabel)
        layout.addView(apiKeyInput)
        layout.addView(saveKeyBtn)
        layout.addView(connectGeminiBtn)
        layout.addView(logLabel)
        layout.addView(logText)

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logText.append("[$time] $message\n")
    }

    private fun testGeminiConnection(apiKey: String) {
        statusText.text = "სტატუსი: უკავშირდება Gemini-ს..."
        addLog("იგზავნება სატესტო მოთხოვნა Gemini 2.5 Flash-თან...")

        mainScope.launch {
            val result = withContext(Dispatchers.IO) {
                callGeminiApi(apiKey, "მიპასუხე მხოლოდ ერთი წინადადებით ქართულად: მზად ვარ მუშაობისთვის!")
            }
            apiRequestCount++
            apiCountText.text = "Gemini API მოთხოვნები: $apiRequestCount"
            statusText.text = "სტატუსი: კავშირი შემოწმებულია"
            addLog("პასუხი: $result")
        }
    }

    private fun callGeminiApi(apiKey: String, prompt: String): String {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
            }

            val part = JSONObject().put("text", prompt)
            val parts = JSONArray().put(part)
            val content = JSONObject().put("parts", parts)
            val contents = JSONArray().put(content)
            val payload = JSONObject().put("contents", contents)

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseStr = stream.bufferedReader().use { it.readText() }

            if (code in 200..299) {
                val json = JSONObject(responseStr)
                json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
            } else {
                "API შეცდომა ($code): $responseStr"
            }
        } catch (e: Exception) {
            "ქსელის შეცდომა: ${e.localizedMessage}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
