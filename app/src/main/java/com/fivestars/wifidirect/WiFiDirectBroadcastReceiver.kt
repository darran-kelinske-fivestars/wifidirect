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

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager?, private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity,
    private val connectionInfoListener: WifiP2pManager.ConnectionInfoListener?,
    private val peerListListener: PeerListListener?
) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION == action) {
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