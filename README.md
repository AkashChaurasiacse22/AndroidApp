package com.example.test1

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MCPService : Service() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // SSE requires infinite read
        .build()

    private val executor = Executors.newSingleThreadExecutor()
    private val mcpUrl = "http://10.0.2.2:8000/mcp/"
    private var currentRequestId: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendJsonRequestThenListen()
        return START_STICKY
    }

    private fun sendJsonRequestThenListen() {
        currentRequestId = UUID.randomUUID().toString()

        val jsonRequest = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", currentRequestId)
            put("method", "tools/list")
            put("params", JSONObject())
        }

        val requestBody = RequestBody.create(
            "application/json".toMediaTypeOrNull(),
            jsonRequest.toString()
        )

        val postRequest = Request.Builder()
            .url(mcpUrl)
            .post(requestBody)
            .build()

        client.newCall(postRequest).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MCPService", "POST request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.d("MCPService", "POST succeeded with id: $currentRequestId. Starting SSE...")
                    listenToSSE()
                } else {
                    Log.e("MCPService", "POST request error: ${response.code}")
                }
            }
        })
    }

    private fun listenToSSE() {
        val getRequest = Request.Builder()
            .url(mcpUrl)
            .get()
            .build()

        executor.execute {
            try {
                client.newCall(getRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("MCPService", "SSE connection failed: $response")
                        return@execute
                    }

                    val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                    var line: String?
                    var event: String? = null
                    val dataBuilder = StringBuilder()

                    while (reader.readLine().also { line = it } != null) {
                        line = line?.trim()

                        if (line!!.startsWith("event:")) {
                            event = line!!.removePrefix("event:").trim()
                        } else if (line!!.startsWith("data:")) {
                            dataBuilder.append(line!!.removePrefix("data:").trim())
                        } else if (line!!.isEmpty()) {
                            // End of one SSE event block
                            val fullData = dataBuilder.toString()
                            if (event != null && fullData.isNotEmpty()) {
                                Log.d("SSE_RAW", "event=$event, data=$fullData")
                                handleIncomingSSE(event!!, fullData)
                            }
                            // Reset for next event
                            event = null
                            dataBuilder.setLength(0)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MCPService", "SSE Error: ${e.message}")
            }
        }
    }

    private fun handleIncomingSSE(event: String, data: String) {
        try {
            val json = JSONObject(data)
            val incomingId = json.optString("id", "")

            if (incomingId == currentRequestId) {
                Log.i("MCPService", "✅ Matched SSE [$event] for id=$incomingId: $data")
                // TODO: Handle the matched result here
            } else {
                Log.i("MCPService", "🔁 Ignored SSE [$event] for id=$incomingId (expected $currentRequestId)")
            }
        } catch (e: Exception) {
            Log.e("MCPService", "❌ Invalid SSE JSON data: $data")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
