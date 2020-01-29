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
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect


open class DeviceDetailFragment : Fragment(), ConnectionInfoListener {
    private var outEditText: EditText? = null
    private var conversationArrayAdapter: ArrayAdapter<String>? = null
    private var outStringBuffer: StringBuffer? = null

    private var contentView: View? = null
    private var info: WifiP2pInfo? = null
    private var progressDialog: ProgressDialog? = null
    private var job: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        contentView = inflater.inflate(R.layout.device_detail, null)
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
        if (info.groupFormed && info.isGroupOwner) {
            MessageUtil.openSocket()
        } else {
            MessageUtil.connectToSocket(
                info.groupOwnerAddress.hostAddress,
                8988)
        }
        job = CoroutineScope(Dispatchers.Main).launch {
            MessageUtil.channel.asFlow().collect {
                conversationArrayAdapter?.add("Them: $it")
            }
        }
    }

    /**
     * Clears the UI fields after a disconnect or direct mode disable operation.
     */
    fun resetViews() {
        job?.cancel()
        view?.visibility = View.GONE
    }

    private fun setupChat(contentView: View) {
        Log.d(TAG, "setupChat()")
        conversationArrayAdapter = ArrayAdapter(context, R.layout.message)
        val mConversationView = contentView.findViewById<View>(R.id.`in`) as ListView
        mConversationView.adapter = conversationArrayAdapter
        outEditText = contentView.findViewById<View>(R.id.edit_text_out) as EditText
        outEditText?.setOnEditorActionListener(mWriteListener)
        val sendButton = contentView.findViewById<View>(R.id.button_send) as Button
        sendButton.visibility = View.VISIBLE
        sendButton.setOnClickListener {
            val view = contentView.findViewById<View>(R.id.edit_text_out) as TextView
            val message = view.text.toString()
            sendMessage(message)
        }
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
            CoroutineScope(Dispatchers.IO).launch {
                MessageUtil.sendMessage(
                    message
                )
                withContext(Dispatchers.Main) {
                    conversationArrayAdapter?.add("Me: $message")
                }
            }
            outStringBuffer!!.setLength(0)
            outEditText?.setText(outStringBuffer)
        }
    }
}