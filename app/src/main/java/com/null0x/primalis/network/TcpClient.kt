package com.null0x.primalis.network
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.net.Socket

class TcpClient(
    private val host: String,
    private val port: Int,
    private val scope: CoroutineScope
) {
    private var socket: Socket? = null
    private var writer: BufferedWriter? = null

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    fun connect() {
        scope.launch(Dispatchers.IO) {
            try {
                socket = Socket(host, port)
                val reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                writer = BufferedWriter(OutputStreamWriter(socket!!.getOutputStream()))

                while (isActive) {
                    val line = reader.readLine() ?: break
                    _incoming.emit(line)
                }
            } catch (e: Exception) {
                // trate/logue conforme necessário
            } finally {
                close()
            }
        }
    }

    suspend fun send(message: String) {
        withContext(Dispatchers.IO) {
            writer?.apply {
                write(message)
                newLine()
                flush()
            }
        }
    }

    fun close() {
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        socket = null
    }
}