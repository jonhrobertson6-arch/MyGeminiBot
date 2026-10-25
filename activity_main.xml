package com.example.mygeminibot

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        startButton = findViewById(R.id.startButton)

        startButton.setOnClickListener {
            statusTextView.text = "ბოტი მუშაობს და აანალიზებს..."
        }
    }
}
