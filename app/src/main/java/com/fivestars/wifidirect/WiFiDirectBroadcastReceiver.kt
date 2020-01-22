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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Parcelable
import android.util.Log

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager?, private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity,
    private val connectionInfoListener: WifiP2pManager.ConnectionInfoListener?,
    private val peerListListener: PeerListListener?
) : BroadcastReceiver() {
    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION == action) { // UI update to indicate wifi p2p status.
            val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) { // Wifi Direct mode is enabled
                activity.setIsWifiP2pEnabled(true)
            } else {
                activity.setIsWifiP2pEnabled(false)
                activity.resetData()
            }
            Log.d(MainActivity.TAG, "P2P state changed - $state")
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
            manager?.requestPeers(
                channel, peerListListener
            )
            Log.d(MainActivity.TAG, "P2P peers changed")
        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION == action) {
            val networkInfo = intent
                .getParcelableExtra<Parcelable>(WifiP2pManager.EXTRA_NETWORK_INFO) as NetworkInfo
            if (networkInfo.isConnected) {
                manager?.requestConnectionInfo(channel, connectionInfoListener)
            } else {
                activity.resetData()
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION == action) {
            val fragment = activity.fragmentManager
                .findFragmentById(R.id.frag_list) as DeviceListFragment
            fragment.updateThisDevice(
                intent.getParcelableExtra<Parcelable>(
                    WifiP2pManager.EXTRA_WIFI_P2P_DEVICE
                ) as WifiP2pDevice
            )
        }
    }

}