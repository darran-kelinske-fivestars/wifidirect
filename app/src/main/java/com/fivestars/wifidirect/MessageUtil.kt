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
    val channel = BroadcastChannel<String>(1)
    private val dispatcher = newSingleThreadContext("CommunicationSocket")

    fun openSocket() {
        job = CoroutineScope(dispatcher).launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            if (!serverSocket.isBound) {
                serverSocket.bind(InetSocketAddress(8988))
            }
            socket = serverSocket.accept()
            val inputStream = socket.getInputStream()
            // Keep listening to the InputStream while connected
            while (true) {
                try { // Read from the InputStream
                    Log.d(MainActivity.TAG, "Server: Socket opened")

                    bytes = inputStream.read(buffer)
                    val readMessage = String(buffer,0, bytes)
                    channel.offer(readMessage)
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
                    Log.d(MainActivity.TAG, "Server: Socket opened")

                    bytes = inputStream.read(buffer)
                    val readMessage = String(buffer,0, bytes)
                    channel.offer(readMessage)
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

    fun sendMessage(message: String) {
            try {
                Log.d(MainActivity.TAG, "Client socket is connected: " + socket.isConnected)
                val stream: OutputStream = socket.getOutputStream()
                val byteArray = message.toByteArray()
                val len: Int = byteArray.size
                stream.write(byteArray, 0, len)
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