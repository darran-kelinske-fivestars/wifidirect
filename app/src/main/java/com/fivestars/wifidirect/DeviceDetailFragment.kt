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
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import android.widget.TextView.OnEditorActionListener
import com.fivestars.wifidirect.DeviceListFragment.DeviceActionListener
import com.fivestars.wifidirect.MainActivity.Companion.TAG
import kotlinx.coroutines.*
import java.io.IOException
import java.net.ServerSocket


open class DeviceDetailFragment : Fragment(), ConnectionInfoListener {
    private var outEditText: EditText? = null
    private var conversationArrayAdapter: ArrayAdapter<String>? = null
    private var outStringBuffer: StringBuffer? = null

    private var contentView: View? = null
    private var device: WifiP2pDevice? = null
    private var info: WifiP2pInfo? = null
    private var progressDialog: ProgressDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        contentView = inflater.inflate(R.layout.device_detail, null)
        contentView?.findViewById<View>(R.id.btn_connect)
            ?.setOnClickListener {
                val config = WifiP2pConfig()
                config.deviceAddress = device!!.deviceAddress
                config.wps.setup = WpsInfo.PBC
                if (progressDialog != null && progressDialog!!.isShowing) {
                    progressDialog!!.dismiss()
                }
                progressDialog = ProgressDialog.show(
                    activity,
                    "Press back to cancel",
                    "Connecting to :" + device!!.deviceAddress,
                    true,
                    true
                    ) { (activity as DeviceActionListener).cancelConnect() }
                (activity as DeviceActionListener).connect(config)
            }
        contentView?.findViewById<View>(R.id.btn_disconnect)
            ?.setOnClickListener { (activity as DeviceActionListener).disconnect() }

        setupChat(contentView!!)
        return contentView
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
        this.info = info
        this.view?.visibility = View.VISIBLE
        var view =
            contentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.text = (resources.getString(R.string.group_owner_text)
                + if (info.isGroupOwner == true) resources.getString(R.string.yes) else resources.getString(
            R.string.no
        ))
        view = contentView!!.findViewById<View>(R.id.device_info) as TextView
        view.text = "Group Owner IP - " + info.groupOwnerAddress?.hostAddress

        if (info.groupFormed && info.isGroupOwner) {
            GlobalScope.launch(newSingleThreadContext("IncomingSocket")) {
                val buffer = ByteArray(1024)
                var bytes: Int
                val serverSocket = ServerSocket(8988)
                val client = serverSocket.accept()
                val inputStream = client.getInputStream()
                // Keep listening to the InputStream while connected
                while (true) {
                    try { // Read from the InputStream
                        Log.d(TAG, "Server: Socket opened")

                        bytes = inputStream.read(buffer)
                        val readMessage = String(buffer,0, bytes)
                        withContext(Dispatchers.Main) {
                            conversationArrayAdapter!!.add("Received:  $readMessage")
                        }
                    } catch (e: IOException) {
                        Log.e(
                            TAG,
                            "disconnected",
                            e
                        )

                        break
                    }
                }
            }

        } else if (info.groupFormed) {
            contentView!!.findViewById<View>(R.id.button_send).visibility = View.VISIBLE
            (contentView!!.findViewById<View>(R.id.status_text) as TextView).text = resources
                .getString(R.string.client_text)
        }
        // hide the connect button
        contentView!!.findViewById<View>(R.id.btn_connect).visibility = View.GONE
    }

    /**
     * Updates the UI with device data
     *
     * @param device the device to be displayed
     */
    fun showDetails(device: WifiP2pDevice) {
        this.device = device
        this.view!!.visibility = View.VISIBLE
        var view =
            contentView!!.findViewById<View>(R.id.device_address) as TextView
        view.text = device.deviceAddress
        view = contentView!!.findViewById<View>(R.id.device_info) as TextView
        view.text = device.toString()
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    fun resetViews() {
        contentView!!.findViewById<View>(R.id.btn_connect).visibility = View.VISIBLE
        var view =
            contentView!!.findViewById<View>(R.id.device_address) as TextView
        view.setText(R.string.empty)
        view = contentView!!.findViewById<View>(R.id.device_info) as TextView
        view.setText(R.string.empty)
        view = contentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.setText(R.string.empty)
        view = contentView!!.findViewById<View>(R.id.status_text) as TextView
        view.setText(R.string.empty)
        contentView!!.findViewById<View>(R.id.button_send).visibility = View.GONE
        view.visibility = View.GONE
    }

    private fun setupChat(contentView: View) {
        Log.d(TAG, "setupChat()")
        conversationArrayAdapter = ArrayAdapter<String>(context, R.layout.message)
        val mConversationView = contentView.findViewById<View>(R.id.`in`) as ListView
        mConversationView.adapter = conversationArrayAdapter
        val mOutEditText = contentView.findViewById<View>(R.id.edit_text_out) as EditText
        mOutEditText.setOnEditorActionListener(mWriteListener)
        val mSendButton = contentView.findViewById<View>(R.id.button_send) as Button
        mSendButton.setOnClickListener(View.OnClickListener {
            val view = contentView.findViewById<View>(R.id.edit_text_out) as TextView
            val message = view.text.toString()
            sendMessage(message)
        })
        outStringBuffer = StringBuffer("")
    }

    // The action listener for the EditText widget, to listen for the return key
    private val mWriteListener =
        OnEditorActionListener { view, actionId, event ->
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.action == KeyEvent.ACTION_UP) {
                val message = view.text.toString()
                sendMessage(message)
            }
            true
        }

    private fun sendMessage(message: String) {
        if (message.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.IO) {
                MessageUtil.sendMessage(
                    info!!.groupOwnerAddress.hostAddress,
                    8988,
                    message
                )
                conversationArrayAdapter?.add("Me: $message")
            }
            outStringBuffer!!.setLength(0)
            outEditText!!.setText(outStringBuffer)
        }
    }
}