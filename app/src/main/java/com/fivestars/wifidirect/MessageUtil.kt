package com.fivestars.wifidirect


import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


object MessageUtil {
    private var socket = Socket()
    private var serverSocket = ServerSocket()
    private var job: Job? = null
    private const val SOCKET_TIMEOUT = 0
    val readChannel = BroadcastChannel<String>(1)
    private val dispatcher = newSingleThreadContext("CommunicationSocket")
    val stringBuffer = StringBuffer()

    fun openSocket() {
        job = CoroutineScope(dispatcher).launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            if (!serverSocket.isBound) {
                serverSocket.bind(InetSocketAddress(8988))
            }
            socket = serverSocket.accept()
            Log.d(MainActivity.TAG, "Server: Accepting connections")
            val inputStream = socket.getInputStream()
            // Keep listening to the InputStream while connected
            while (true) {
                try { // Read from the InputStream
                    bytes = inputStream.read(buffer)
                    stringBuffer.append(String(buffer,0, bytes))
                    readUntil()
                } catch (e: IOException) {
                    Log.e(
                        MainActivity.TAG,
                        "disconnected",
                        e
                    )

                    break
                }
            }
        }
    }


    private fun readUntil() {
        var data = ""
        val index: Int = stringBuffer.indexOf("\n", 0)
        if (index > -1) {
            data = stringBuffer.substring(0, index + "\n".length)
            stringBuffer.delete(0, index + "\n".length)
        }

        if (data.isNotEmpty()) {
            readChannel.offer(data.trim())
        }

    }


    fun closeSocket() {
        socket.close()
        job?.cancel()
    }

    fun connectToSocket(host: String, port: Int) {
        Log.d(MainActivity.TAG, "Opening client socket.")
        socket = Socket()
        CoroutineScope(dispatcher).launch {
            if (!socket.isBound) {
                socket.bind(null)
            }

            if (!socket.isConnected) {
                socket.connect(
                    InetSocketAddress(host, port),
                    SOCKET_TIMEOUT
                )
            }
            val buffer = ByteArray(1024)
            var bytes: Int
            val inputStream = socket.getInputStream()
            // Keep listening to the InputStream while connected
            while (true) {
                try { // Read from the InputStream
                    bytes = inputStream.read(buffer)
                    stringBuffer.append(String(buffer,0, bytes))
                    readUntil()
                } catch (e: IOException) {
                    Log.e(
                        MainActivity.TAG,
                        "disconnected",
                        e
                    )

                    break
                }
            }
        }
    }

    fun sendMessage(message: ByteArray) {
            try {
                Log.d(MainActivity.TAG, "Client socket is connected: " + socket.isConnected)
                val stream: OutputStream = socket.getOutputStream()
                stream.write(message)
                Log.d(MainActivity.TAG, "Client: Data written")
            } catch (e: IOException) {
                Log.e(MainActivity.TAG, e.message ?: "Exception opening and sending message on socket.")
                if (socket.isConnected) {
                    try {
                        socket.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
    }
}