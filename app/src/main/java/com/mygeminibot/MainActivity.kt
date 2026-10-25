
package com.mygeminibot

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var apiKeyInput: EditText
    private lateinit var statusText: TextView
    private lateinit var logText: TextView
    private var selectedImage: Uri? = null

    private val prefs by lazy {
        getSharedPreferences("bot_settings", MODE_PRIVATE)
    }

    companion object {
        private const val PICK_IMAGE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 24)
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun addView(view: android.view.View) {
            content.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        fun button(label: String, action: () -> Unit) {
            addView(Button(this).apply {
                text = label
                setOnClickListener { action() }
            })
        }

        addView(TextView(this).apply {
            text = "MyGeminiBot"
            textSize = 26f
        })

        addView(TextView(this).apply {
            text = "Gemini API გასაღები"
            textSize = 16f
        })

        apiKeyInput = EditText(this).apply {
            hint = "ჩასვი API გასაღები"
            singleLine = true
            setText(prefs.getString("api_key", ""))
        }
        addView(apiKeyInput)

        button("გასაღების შენახვა") {
            val key = apiKeyInput.text.toString().trim()
            if (key.isBlank()) {
                status("API გასაღები ცარიელია.")
            } else {
                prefs.edit().putString("api_key", key).apply()
                status("გასაღები შენახულია ამ ტელეფონზე.")
            }
        }

        statusText = TextView(this).apply {
            textSize = 16f
            text = "მზადაა."
        }
        addView(statusText)

        button("Gemini-ს კავშირის შემოწმება") {
            runGeminiTest()
        }

        button("სურათის არჩევა") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, PICK_IMAGE)
        }

        button("არჩეული სურათის გაანალიზება") {
            analyzeSelectedImage()
        }

        button("Stop — зогсоох") {
            status("Хүсэлт зогссон. Идэвхтэй хүсэлтийг цуцлахын тулд дахин эхлүүл.")
        }

        addView(TextView(this).apply {
            text = "პროცესის ჟურნალი"
            textSize = 18f
        })

        logText = TextView(this).apply {
            text = ""
            textSize = 14f
        }
        addView(logText)

        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)
    }

    private fun getClient(): GeminiClient? {
        val key = apiKeyInput.text.toString().trim()
        if (key.isBlank()) {
            status("ჯერ შეიყვანე API გასაღები.")
            return null
        }
        return GeminiClient(key)
    }

    private fun runGeminiTest() {
        val client = getClient() ?: return
        status("Gemini-ს ვუკავშირდები...")

        lifecycleScope.launch {
            try {
                val answer = client.askText(
                    "Reply with exactly: CONNECTION_OK"
                )
                status("კავშირი წარმატებულია: $answer")
            } catch (e: Exception) {
                status("შეცდომა: ${e.message}")
            }
        }
    }

    private fun analyzeSelectedImage() {
        val uri = selectedImage
        if (uri == null) {
            status("ჯერ აირჩიე სურათი.")
            return
        }

        val client = getClient() ?: return
        status("სურათი იგზავნება Gemini-სთან...")

        lifecycleScope.launch {
            try {
                val bitmap = contentResolver.openInputStream(uri).use {
                    BitmapFactory.decodeStream(it)
                } ?: throw IllegalStateException(
                    "სურათის წაკითხვა ვერ მოხერხდა."
                )

                try {
                    val answer = client.analyzeScreenshot(
                        """
                        Describe this screenshot clearly.
                        If it shows a survey list, list the visible survey
                        options without inventing missing details.
                        If it shows a question, transcribe the question
                        and visible answer choices. Do not guess personal
                        information about the user.
                        """.trimIndent(),
                        bitmap
                    )
                    status(answer)
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                status("შეცდომა: ${e.message}")
            }
        }
    }

    @Deprecated("Use Activity Result APIs in a future revision")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            selectedImage = data?.data
            status(
                if (selectedImage != null) {
                    "სურათი არჩეულია. შეგიძლია გააანალიზო."
                } else {
                    "სურათი არ აგირჩევია."
                }
            )
        }
    }

    private fun status(message: String) {
        if (::statusText.isInitialized) statusText.text = message
        if (::logText.isInitialized) {
            logText.append("\n• $message")
        }
    }
}
