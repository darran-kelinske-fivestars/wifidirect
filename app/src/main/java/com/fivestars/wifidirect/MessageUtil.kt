// Copyright 2011 Google Inc. All Rights Reserved.
package com.fivestars.wifidirect


import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket


object MessageUtil {
    private val socket = Socket()
    private const val SOCKET_TIMEOUT = 0

    fun sendMessage(host: String, port: Int, message: String) {
            try {
                Log.d(MainActivity.TAG, "Opening client socket.")
                if (!socket.isBound) {
                    socket.bind(null)
                }

                if (!socket.isConnected) {
                    socket.connect(
                        InetSocketAddress(host, port),
                        SOCKET_TIMEOUT
                    )
                }
                Log.d(MainActivity.TAG, "Client socket - " + socket.isConnected)
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