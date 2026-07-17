package com.esp32rc.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MjpegStreamer(
    private val url: String,
    private val onFrame: (Bitmap) -> Unit,
    private val onError: (Exception) -> Unit = {}
) : Thread("MjpegStreamer") {

    @Volatile
    private var running = true
    private val mainHandler = Handler(Looper.getMainLooper())

    fun stopStream() {
        running = false
        interrupt()
    }

    override fun run() {
        while (running) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 5000
                }
                val contentType = connection.contentType ?: ""
                val boundary = parseBoundary(contentType)
                val input = BufferedInputStream(connection.inputStream, 16 * 1024)
                readStream(input, boundary)
            } catch (e: Exception) {
                if (running) {
                    mainHandler.post { onError(e) }
                    try {
                        sleep(1000)
                    } catch (_: InterruptedException) {
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun parseBoundary(contentType: String): String {
        val marker = "boundary="
        val index = contentType.indexOf(marker)
        if (index < 0) return "--"
        var boundary = contentType.substring(index + marker.length).trim()
        boundary = boundary.trim('"')
        if (!boundary.startsWith("--")) boundary = "--$boundary"
        return boundary
    }

    private fun readStream(input: InputStream, boundary: String) {
        while (running) {
            if (!skipToBoundary(input, boundary)) return
            val headers = readHeaders(input) ?: return
            val length = headers["content-length"]?.toIntOrNull()
            val jpeg = if (length != null && length > 0) {
                readFully(input, length) ?: return
            } else {
                readUntilBoundary(input, boundary) ?: return
            }
            val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
            mainHandler.post { if (running) onFrame(bitmap) }
        }
    }

    private fun skipToBoundary(input: InputStream, boundary: String): Boolean {
        val target = boundary.toByteArray()
        var matched = 0
        while (running) {
            val b = input.read()
            if (b < 0) return false
            if (b == target[matched].toInt()) {
                matched++
                if (matched == target.size) return true
            } else {
                matched = if (b == target[0].toInt()) 1 else 0
            }
        }
        return false
    }

    private fun readHeaders(input: InputStream): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        val line = StringBuilder()
        var blankCount = 0
        while (running) {
            val b = input.read()
            if (b < 0) return null
            when (b.toChar()) {
                '\r' -> {}
                '\n' -> {
                    val text = line.toString().trim()
                    line.setLength(0)
                    if (text.isEmpty()) {
                        blankCount++
                        if (headers.isNotEmpty() || blankCount > 1) return headers
                    } else {
                        val sep = text.indexOf(':')
                        if (sep > 0) {
                            headers[text.substring(0, sep).trim().lowercase()] =
                                text.substring(sep + 1).trim()
                        }
                    }
                }
                else -> line.append(b.toChar())
            }
        }
        return null
    }

    private fun readFully(input: InputStream, length: Int): ByteArray? {
        val data = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(data, offset, length - offset)
            if (read < 0) return null
            offset += read
        }
        return data
    }

    private fun readUntilBoundary(input: InputStream, boundary: String): ByteArray? {
        val target = boundary.toByteArray()
        val buffer = ByteArrayOutputStream(32 * 1024)
        var matched = 0
        while (running) {
            val b = input.read()
            if (b < 0) return null
            if (b == target[matched].toInt()) {
                matched++
                if (matched == target.size) {
                    val bytes = buffer.toByteArray()
                    return bytes.copyOf(bytes.size)
                }
            } else {
                if (matched > 0) {
                    buffer.write(target, 0, matched)
                    matched = if (b == target[0].toInt()) 1 else 0
                    if (matched == 1) continue
                }
                buffer.write(b)
            }
        }
        return null
    }
}
