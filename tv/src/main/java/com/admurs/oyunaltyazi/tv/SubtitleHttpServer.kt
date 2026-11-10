package com.admurs.oyunaltyazi.tv

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** Tek bir HTTP isteğinin bize gereken kısmı. */
data class HttpRequest(val method: String, val path: String, val body: String)

/**
 * Yerel ağdaki tabletten gelen altyazıları alan küçük HTTP sunucusu.
 * `GET /hello` → TV'yi tanıtır, `POST /subtitle` → JSON mesajı [onMessage]'a iletir.
 */
class SubtitleHttpServer(
    private val port: Int,
    private val onError: (String) -> Unit = {},
    private val onMessage: (String) -> Boolean,
) {
    @Volatile private var serverSocket: ServerSocket? = null

    fun start() {
        thread(name = "altyazi-http", isDaemon = true) {
            val server = try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            } catch (e: IOException) {
                val message = "Port $port açılamadı: ${e.message}"
                lastError = message
                onError(message)
                return@thread
            }
            serverSocket = server
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (e: IOException) {
                    break
                }
                thread(isDaemon = true) { handle(client) }
            }
        }
    }

    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 3000
                val request = HttpParser.read(BufferedInputStream(socket.getInputStream()))
                    ?: return
                val (status, body) = when {
                    request.method == "GET" && request.path == "/hello" ->
                        200 to """{"app":"oyunaltyazi-tv"}"""
                    request.method == "POST" && request.path == "/subtitle" ->
                        if (onMessage(request.body)) 200 to "{}" else 400 to """{"error":"bad message"}"""
                    else -> 404 to "{}"
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                val head = "HTTP/1.1 $status ${if (status == 200) "OK" else "Error"}\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                socket.getOutputStream().apply {
                    write(head.toByteArray(Charsets.US_ASCII))
                    write(bytes)
                    flush()
                }
            } catch (_: IOException) {
            }
        }
    }

    companion object {
        @Volatile var lastError: String? = null
    }
}

/** HTTP isteğini okur. Ağdan bağımsız olduğu için birim testlerle denenir. */
object HttpParser {
    private const val MAX_HEAD = 16 * 1024
    private const val MAX_BODY = 64 * 1024

    fun read(input: InputStream): HttpRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val parts = lines.first().split(" ")
        if (parts.size < 2) return null
        val length = lines.drop(1)
            .firstOrNull { it.lowercase().startsWith("content-length:") }
            ?.substringAfter(":")?.trim()?.toIntOrNull()
            ?: 0
        if (length < 0 || length > MAX_BODY) return null
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        return HttpRequest(parts[0], parts[1].substringBefore('?'), String(body, 0, read, Charsets.UTF_8))
    }

    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        while (buffer.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            buffer.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
            if (matched == 4) {
                val text = buffer.toString(Charsets.US_ASCII.name())
                return text.substring(0, text.length - 4)
            }
        }
        return null
    }
}
