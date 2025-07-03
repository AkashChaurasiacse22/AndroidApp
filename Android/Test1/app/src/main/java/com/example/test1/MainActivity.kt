package com.example.test1

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startButton: Button = findViewById(R.id.startServiceButton)
        statusText = findViewById(R.id.statusText)

        startButton.setOnClickListener {
            val intent = Intent(this, MCPService::class.java)
            startService(intent)
            statusText.text = "Service started. Connecting to MCP..."
        }
    }
}
