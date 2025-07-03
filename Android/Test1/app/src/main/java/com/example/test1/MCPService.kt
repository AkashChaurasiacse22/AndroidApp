package com.example.test1

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MCPService : Service() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .build()

    private val handler = Handler()
    private val interval = 10_000L // 10 seconds
    private val mcpBaseUrl = "http://10.0.2.2:8000/mcp"

    private val requestList = listOf(
        JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "mobile_use_device")
                put("arguments", JSONObject().apply {
                    put("device", "emulator-5554")
                    put("deviceType", "android")
                })
            })
        },
        JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 2)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "mobile_launch_app")
                put("arguments", JSONObject().apply {
                    put("packageName", "com.android.chrome")
                })
            })
        },
        JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 3)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "mobile_launch_app")
                put("arguments", JSONObject().apply {
                    put("packageName", "com.android.settings")
                })
            })
        },
        JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 4)
            put("method", "tools/call")
            put("params", JSONObject().apply {
                put("name", "mobile_launch_app")
                put("arguments", JSONObject().apply {
                    put("packageName", "com.google.android.dialer")
                })
            })
        }
    )

    private var currentRequestIndex = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        currentRequestIndex = 0
        handler.post(runSequentialRequests)
        return START_STICKY
    }

    private val runSequentialRequests = object : Runnable {
        override fun run() {
            if (currentRequestIndex >= requestList.size) {
                stopSelf() // Stop service after 4 calls
                return
            }

            val json = requestList[currentRequestIndex]
            callMCPServer(json) { result ->
                Log.d("MCPService", "Call ${currentRequestIndex + 1} response: $result")
            }

            currentRequestIndex++
            handler.postDelayed(this, interval)
        }
    }

    private fun callMCPServer(params: JSONObject, callback: (String) -> Unit) {
        val body = RequestBody.create("application/json".toMediaTypeOrNull(), params.toString())
        val request = Request.Builder()
            .url("$mcpBaseUrl/")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback("Error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                callback(response.body?.string() ?: "No response")
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
