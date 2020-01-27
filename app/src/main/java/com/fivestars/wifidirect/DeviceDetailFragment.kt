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
import android.bluetooth.BluetoothAdapter
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


/**
 * A fragment that manages a particular peer and allows interaction with device
 * i.e. setting up network connection and transferring data.
 */
class DeviceDetailFragment : Fragment(), ConnectionInfoListener {
    // Layout Views
    private var mConversationView: ListView? = null
    private var mOutEditText: EditText? = null
    private var mSendButton: Button? = null

    // Name of the connected device
    private val mConnectedDeviceName: String? = null
    // Array adapter for the conversation thread
    private var mConversationArrayAdapter: ArrayAdapter<String>? = null
    // String buffer for outgoing messages
    private var mOutStringBuffer: StringBuffer? = null
    // Local Bluetooth adapter
    private val mBluetoothAdapter: BluetoothAdapter? = null

    private var mContentView: View? = null
    private var device: WifiP2pDevice? = null
    private var info: WifiP2pInfo? = null
    var progressDialog: ProgressDialog? = null
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState ?: Bundle())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mContentView = inflater.inflate(R.layout.device_detail, null)
        mContentView?.findViewById<View>(R.id.btn_connect)
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
                    true //                        new DialogInterface.OnCancelListener() {
//
//                            @Override
//                            public void onCancel(DialogInterface dialog) {
//                                ((DeviceActionListener) getActivity()).cancelDisconnect();
//                            }
//                        }
                )
                (activity as DeviceActionListener).connect(config)
            }
        mContentView?.findViewById<View>(R.id.btn_disconnect)
            ?.setOnClickListener { (activity as DeviceActionListener).disconnect() }
        mContentView?.findViewById<View>(R.id.send_file_button)?.setOnClickListener {
            sendFile()
        }

        setupChat(mContentView!!)
        return mContentView
    }

    private fun sendFile() {

        GlobalScope.launch(Dispatchers.IO) {
            FileTransferService.sendFile(
                info!!.groupOwnerAddress.hostAddress,
                8988
            )
        }
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }
        this.info = info
        this.view!!.visibility = View.VISIBLE
        // The owner IP is now known.
        var view =
            mContentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.text = (resources.getString(R.string.group_owner_text)
                + if (info.isGroupOwner == true) resources.getString(R.string.yes) else resources.getString(
            R.string.no
        ))
        // InetAddress from WifiP2pInfo struct.
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.text = "Group Owner IP - " + info.groupOwnerAddress?.hostAddress
        // After the group negotiation, we assign the group owner as the file
// server. The file server is single threaded, single connection server
// socket.
        if (info.groupFormed && info.isGroupOwner) {
            // open up socket and listen for messages

            GlobalScope.launch(newSingleThreadContext("woot")) {
                val buffer = ByteArray(1024)
                var bytes: Int
                val serverSocket = ServerSocket(8988)
                val client = serverSocket.accept()
                val inputstream = client.getInputStream()
                // Keep listening to the InputStream while connected
                while (true) {
                    try { // Read from the InputStream
                        Log.d(TAG, "Server: Socket opened")

                        bytes = inputstream.read(buffer)
                        val readMessage = String(buffer,0, bytes)
                        withContext(Dispatchers.Main) {
                            mConversationArrayAdapter!!.add("Received:  $readMessage")
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

        } else if (info.groupFormed) { // The other device acts as the client. In this case, we enable the
// get file button.
            mContentView!!.findViewById<View>(R.id.send_file_button).visibility = View.VISIBLE
            (mContentView!!.findViewById<View>(R.id.status_text) as TextView).text = resources
                .getString(R.string.client_text)
        }
        // hide the connect button
        mContentView!!.findViewById<View>(R.id.btn_connect).visibility = View.GONE
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
            mContentView!!.findViewById<View>(R.id.device_address) as TextView
        view.text = device.deviceAddress
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.text = device.toString()
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    fun resetViews() {
        mContentView!!.findViewById<View>(R.id.btn_connect).visibility = View.VISIBLE
        var view =
            mContentView!!.findViewById<View>(R.id.device_address) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.device_info) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.group_owner) as TextView
        view.setText(R.string.empty)
        view = mContentView!!.findViewById<View>(R.id.status_text) as TextView
        view.setText(R.string.empty)
        mContentView!!.findViewById<View>(R.id.send_file_button).visibility = View.GONE
        view.visibility = View.GONE
    }

    private fun setupChat(contentView: View) {
        Log.d(TAG, "setupChat()")
        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = ArrayAdapter<String>(context, R.layout.message)
        val mConversationView = contentView.findViewById<View>(R.id.`in`) as ListView
        mConversationView.setAdapter(mConversationArrayAdapter)
        // Initialize the compose field with a listener for the return key
        val mOutEditText = contentView.findViewById<View>(R.id.edit_text_out) as EditText
        mOutEditText.setOnEditorActionListener(mWriteListener)
        // Initialize the send button with a listener that for click events
        val mSendButton = contentView.findViewById<View>(R.id.button_send) as Button
        mSendButton.setOnClickListener(View.OnClickListener {
            // Send a message using content of the edit text widget
            val view = contentView.findViewById<View>(R.id.edit_text_out) as TextView
            val message = view.text.toString()
            sendMessage(message)
        })
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = StringBuffer("")
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


    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private fun sendMessage(message: String) { // Check that we're actually connected before trying anything
        // Check that there's actually something to send
        if (message.isNotEmpty()) { // Get the message bytes and tell the BluetoothChatService to write
            val send = message.toByteArray()
//            mChatService.write(send)
            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer!!.setLength(0)
            mOutEditText!!.setText(mOutStringBuffer)
        }
    }

    companion object {
        protected const val CHOOSE_FILE_RESULT_CODE = 20
    }
}