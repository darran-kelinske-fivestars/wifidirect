/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.fivestars.wifidirect

import android.app.Fragment
import android.app.ProgressDialog
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fivestars.wifidirect.model.TestMessage
import com.fivestars.wifidirect.DeviceListFragment.DeviceActionListener
import com.fivestars.wifidirect.MainActivity.Companion.TAG
import com.fivestars.wifidirect.model.MessageType
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.android.synthetic.main.device_detail.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import java.util.*
import java.util.concurrent.atomic.AtomicLong


open class DeviceDetailFragment : Fragment(), ConnectionInfoListener {

    private var contentView: View? = null
    private var info: WifiP2pInfo? = null
    private var progressDialog: ProgressDialog? = null
    private var job: Job? = null
    private val moshi: Moshi = Moshi.Builder()
        .build()
    private val adapter: JsonAdapter<TestMessage> = moshi.adapter(TestMessage::class.java)
    private var currentMessage: TestMessage? = null
    private var totalBytesSent: AtomicLong = AtomicLong(0)
    private var totalBytesReceived: AtomicLong = AtomicLong(0)
    private var startTime: AtomicLong = AtomicLong(0)
    private var byteArrayPayload = ByteArray(256)
    private var readJob = Job()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        contentView = inflater.inflate(R.layout.device_detail, null)
        contentView?.findViewById<View>(R.id.btn_disconnect)
            ?.setOnClickListener { (activity as DeviceActionListener).disconnect() }

        return contentView
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        CoroutineScope(Dispatchers.Default + readJob).launch {
            while (true) {
                val totalTimeInSeconds: Long = ((Date().time - startTime.get())) / 1000
                try {
                    withContext(Dispatchers.Main) {

                        var totalBytesSentSecond: Long = 0

                        if (totalBytesSent.get() != 0L) {
                            totalBytesSentSecond = (totalBytesSent.get() / totalTimeInSeconds)
                        }

                        var totalBytesReceivedSecond: Long = 0

                        if (totalBytesReceived.get() != 0L) {
                            totalBytesReceivedSecond = (totalBytesReceived.get() / totalTimeInSeconds)
                        }
                        text_view_status.text =
                            "Total bytes sent: $totalBytesSent \nBytes/second sent: $totalBytesSentSecond\nTotal bytes received: $totalBytesReceived\nBytes/second received: $totalBytesReceivedSecond"
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "The divide by zero" + e)
                }
                Thread.sleep(2000)
            }
        }

        button_bidirectional.setOnClickListener {
            setPayLoadSizeAndStartTime()
            randomizeAndSendMessage(MessageType.BIDIRECTIONAL)

        }

        button_unidirectional.setOnClickListener {
            setPayLoadSizeAndStartTime()

            CoroutineScope(newSingleThreadContext("uni")+ readJob).launch {
                // YOLO
                while (true) {
                    randomizeAndSendMessage(MessageType.UNIDIRECTIONAL)
                }
            }
        }
    }

    private fun randomizeAndSendMessage(messageType: MessageType) {
        currentMessage = TestMessage(
            Date().time,
            messageType,
            String(byteArrayPayload)
        )
        sendMessage(adapter.toJson(currentMessage))
    }

    private fun setPayLoadSizeAndStartTime() {
        val payloadSize = edit_text_payload_size.text.toString()
        edit_text_payload_size.isEnabled = false
        byteArrayPayload = ByteArray(payloadSize.toInt())
        startTime = AtomicLong(Date().time)
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
        this.info = info
        this.view?.visibility = View.VISIBLE
        if (info.groupFormed && info.isGroupOwner) {
            MessageUtil.openSocket()
        } else {
            MessageUtil.connectToSocket(
                info.groupOwnerAddress.hostAddress,
                8988)
        }

        CoroutineScope(newFixedThreadPoolContext(1, "uno") + readJob).launch {
            MessageUtil.readChannel.asFlow().collect {
                totalBytesReceived.getAndAdd(it.toByteArray().size.toLong())
                val parsedMessage: TestMessage?
                try {
                    parsedMessage = adapter.fromJson(it)
                } catch (e: java.lang.Exception) {
                    Log.e(TAG, e.toString() + "data was: it")
                    return@collect
                }
                if (startTime.get() == 0L) {
                    startTime = AtomicLong(Date().time)
                }
                // Send the response message if we are the receiver app
                if (currentMessage == null && parsedMessage?.messageType == MessageType.BIDIRECTIONAL) {
                    sendMessage(it)
                } else {
                    parsedMessage?.run {
                        // If the time on the incoming message matches our last sent message, then we received the "ACK"
                        // Send another message to keep the data flow going
                        if (time == currentMessage?.time && messageType == MessageType.BIDIRECTIONAL) {
                            currentMessage = TestMessage(
                                Date().time, MessageType.BIDIRECTIONAL, String(
                                    byteArrayPayload
                                )
                            )
                            sendMessage(adapter.toJson(currentMessage))
                        }
                    }
                }
            }
        }
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    fun resetViews() {
        view?.visibility = View.GONE
    }

    private fun sendMessage(message: String) {
        if (message.isNotEmpty()) {
            val messageByteArray = (message +"\n").toByteArray()
            totalBytesSent.getAndAdd(messageByteArray.size.toLong())
            CoroutineScope(Dispatchers.IO).launch {
                MessageUtil.sendMessage(
                    messageByteArray
                )
            }
        }
    }
}