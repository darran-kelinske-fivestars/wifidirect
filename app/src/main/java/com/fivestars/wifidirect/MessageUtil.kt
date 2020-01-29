// Copyright 2011 Google Inc. All Rights Reserved.
package com.fivestars.wifidirect


import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket


object MessageUtil {
    private val socket = Socket()
    private const val SOCKET_TIMEOUT = 0
    public val channel = BroadcastChannel<String>(1)

    fun openSocket() {
        GlobalScope.launch(newSingleThreadContext("CommunicationSocket")) {
            val buffer = ByteArray(1024)
            var bytes: Int
            val serverSocket = ServerSocket(8988)
            val client = serverSocket.accept()
            val inputStream = client.getInputStream()
            // Keep listening to the InputStream while connected
            while (true) {
                try { // Read from the InputStream
                    Log.d(MainActivity.TAG, "Server: Socket opened")

                    bytes = inputStream.read(buffer)
                    val readMessage: String = String(buffer,0, bytes)
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

    fun connectToSocket(host: String, port: Int) {
        Log.d(MainActivity.TAG, "Opening client socket.")
        GlobalScope.launch(newSingleThreadContext("CommunicationSocket")) {
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
                    val readMessage: String = String(buffer,0, bytes)
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
                val stream = socket.getOutputStream()
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