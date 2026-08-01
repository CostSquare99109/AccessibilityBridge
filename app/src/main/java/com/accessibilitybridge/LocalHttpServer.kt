package com.accessibilitybridge

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import org.json.JSONObject
import org.json.JSONTokener

class LocalHttpServer(private val port: Int, private val service: AccessibilityBridgeService) {

    companion object {
        const val TAG = "LocalHttpServer"
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val executor = Executors.newCachedThreadPool()

    fun start() {
        if (isRunning) return
        executor.execute {
            try {
                serverSocket = ServerSocket(port)
                serverSocket?.setReuseAddress(true)
                isRunning = true
                Log.i(TAG, "HTTP server started on port $port")

                while (isRunning && !Thread.currentThread().isInterrupted) {
                    try {
                        val client = serverSocket?.accept()
                        client?.let {
                            executor.execute { handleClient(it) }
                        }
                    } catch (e: Exception) {
                        if (isRunning) Log.e(TAG, "Accept error", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server start error", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Server stop error", e)
        }
        executor.shutdown()
        Log.i(TAG, "HTTP server stopped")
    }

    private fun handleClient(socket: Socket) {
        var input: BufferedReader? = null
        var output: OutputStream? = null
        try {
            socket.soTimeout = 5000
            input = BufferedReader(InputStreamReader(socket.getInputStream()))
            output = socket.getOutputStream()

            val requestLine = input.readLine() ?: return
            val tokens = requestLine.split(" ")
            if (tokens.size < 2) return

            val method = tokens[0]
            val path = tokens[1]

            var contentLength = 0
            var line = input.readLine()
            while (line != null && line.isNotBlank()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                line = input.readLine()
            }

            var body = ""
            if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                input.read(charArray, 0, contentLength)
                body = String(charArray)
            }

            val response = when {
                method == "GET" && path == "/ui" -> handleGetUi()
                method == "GET" && path == "/status" -> handleGetStatus()
                method == "POST" && path == "/tap" -> handlePostTap(body)
                method == "POST" && path == "/text" -> handlePostText(body)
                method == "POST" && path == "/swipe" -> handlePostSwipe(body)
                method == "POST" && path == "/refresh" -> handlePostRefresh()
                else -> createResponse(404, JSONObject().put("error", "Not found").toString())
            }

            output.write(response.toByteArray())
            output.flush()

        } catch (e: Exception) {
            Log.e(TAG, "Client handling error", e)
        } finally {
            try { input?.close() } catch (e: Exception) {}
            try { output?.close() } catch (e: Exception) {}
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun handleGetUi(): String {
        val tree = service.getLatestUiTree()
        val lastUpdate = service.getLastTreeUpdate()
        val json = JSONObject().put("tree", JSONTokener(tree).nextValue()).put("timestamp", lastUpdate)
        return createResponse(200, json.toString())
    }

    private fun handleGetStatus(): String {
        val json = JSONObject().put("service", "running").put("port", port).put("lastTreeUpdate", service.getLastTreeUpdate())
        return createResponse(200, json.toString())
    }

    private fun handlePostTap(body: String): String {
        val json = try { JSONObject(body) } catch (e: Exception) {
            return createResponse(400, JSONObject().put("error", "Invalid JSON").toString())
        }

        val success = when {
            json.has("id") -> service.performTap(json.getInt("id"))
            json.has("x") && json.has("y") -> service.performTapByCoordinates(json.getDouble("x").toFloat(), json.getDouble("y").toFloat())
            else -> false
        }

        val response = JSONObject().put("success", success).put("action", "tap")
        return createResponse(if (success) 200 else 400, response.toString())
    }

    private fun handlePostText(body: String): String {
        val json = try { JSONObject(body) } catch (e: Exception) {
            return createResponse(400, JSONObject().put("error", "Invalid JSON").toString())
        }

        val nodeId = json.optInt("id", -1)
        val text = json.optString("text", "")
        if (nodeId == -1 || text.isBlank()) {
            return createResponse(400, JSONObject().put("error", "Missing id or text").toString())
        }

        val success = service.performText(nodeId, text)
        val response = JSONObject().put("success", success).put("action", "text")
        return createResponse(if (success) 200 else 400, response.toString())
    }

    private fun handlePostSwipe(body: String): String {
        val json = try { JSONObject(body) } catch (e: Exception) {
            return createResponse(400, JSONObject().put("error", "Invalid JSON").toString())
        }

        val startX = json.optDouble("startX", -1.0).toFloat()
        val startY = json.optDouble("startY", -1.0).toFloat()
        val endX = json.optDouble("endX", -1.0).toFloat()
        val endY = json.optDouble("endY", -1.0).toFloat()
        val duration = json.optInt("duration", 300)

        if (startX < 0 || startY < 0 || endX < 0 || endY < 0) {
            return createResponse(400, JSONObject().put("error", "Missing coordinates").toString())
        }

        val success = service.performSwipe(startX, startY, endX, endY, duration)
        val response = JSONObject().put("success", success).put("action", "swipe")
        return createResponse(if (success) 200 else 400, response.toString())
    }

    private fun handlePostRefresh(): String {
        service.refreshUiTree()
        val response = JSONObject().put("success", true).put("action", "refresh")
        return createResponse(200, response.toString())
    }

    private fun createResponse(statusCode: Int, body: String): String {
        return "HTTP/1.1 $statusCode OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                body
    }
}