// Copyright 2011 Google Inc. All Rights Reserved.
package com.fivestars.wifidirect

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
object FileTransferService {

    private const val SOCKET_TIMEOUT = 5000

    fun sendFile(context: Context, fileUri: String, host: String, port: Int) {
            val socket = Socket()
            try {
                Log.d(MainActivity.TAG, "Opening client socket - ")
                socket.bind(null)
                socket.connect(
                    InetSocketAddress(host, port),
                    SOCKET_TIMEOUT
                )
                Log.d(MainActivity.TAG, "Client socket - " + socket.isConnected)
                val stream = socket.getOutputStream()
                val cr = context.contentResolver
                var `is`: InputStream? = null
                try {
                    `is` = cr.openInputStream(Uri.parse(fileUri))
                } catch (e: FileNotFoundException) {
                    Log.d(MainActivity.TAG, e.toString())
                }
                DeviceDetailFragment.Companion.copyFile(`is`, stream)
                Log.d(MainActivity.TAG, "Client: Data written")
            } catch (e: IOException) {
                Log.e(MainActivity.TAG, e.message)
            } finally {
                if (socket != null) {
                    if (socket.isConnected) {
                        try {
                            socket.close()
                        } catch (e: IOException) { // Give up
                            e.printStackTrace()
                        }
                    }
                }
            }
    }
}