package com.esp32rc.network

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class MotorClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    private val sequence = AtomicLong(System.currentTimeMillis())

    fun sendCommand(left: Int, right: Int) {
        val seq = sequence.incrementAndGet()
        val url = "$baseUrl/control?l=$left&r=$right&s=$seq"
        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w("RC-CAR", "control FAILED $url: $e")
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
