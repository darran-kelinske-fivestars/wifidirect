package com.fivestars.wifidirect

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), WifiP2pManager.ChannelListener,
    DeviceListFragment.DeviceActionListener {

    val PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION = 1001

    var manager: WifiP2pManager? = null
    var isWifiP2pEnabled = false
    var retryChannel = false

    val intentFilter = IntentFilter()
    var channel: WifiP2pManager.Channel? = null
    var receiver: BroadcastReceiver? = null
    var listFragment: DeviceListFragment? = null
    var detailsFragment: DeviceDetailFragment? = null


    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    fun setIsWifiP2pEnabled(isWifiP2pEnabled: Boolean) {
        this.isWifiP2pEnabled = isWifiP2pEnabled
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION -> if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Fine location permission is not granted!")
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // add necessary intent values to be matched.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager?
        channel = manager!!.initialize(this, mainLooper, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_CODE_ACCESS_FINE_LOCATION
                )
            }
        }

        detailsFragment = fragmentManager
            .findFragmentById(R.id.frag_detail) as DeviceDetailFragment
        listFragment = fragmentManager
            .findFragmentById(R.id.frag_list) as DeviceListFragment

        discover_and_connect_button.setOnClickListener {
            discoverPeers()
        }
    }

    /** register the BroadcastReceiver with the intent values to be matched  */
    override fun onResume() {
        super.onResume()
        receiver = channel?.let {
            WiFiDirectBroadcastReceiver(
                manager,
                it,
                this,
                detailsFragment,
                listFragment
            )
        }
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    fun resetData() {
        listFragment?.clearPeers()
        detailsFragment?.resetViews()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.action_items, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.atn_direct_discover -> {
                if (!isWifiP2pEnabled) {
                    Toast.makeText(
                        this@MainActivity, R.string.p2p_off_warning,
                        Toast.LENGTH_SHORT
                    ).show()
                    return true
                }
                listFragment?.onInitiateDiscovery()
                manager!!.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Toast.makeText(
                            this@MainActivity, "Discovery Initiated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onFailure(reasonCode: Int) {
                        Toast.makeText(
                            this@MainActivity, "Discovery Failed : $reasonCode",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun showDetails(device: WifiP2pDevice?) {
        if (device != null) {
            detailsFragment?.showDetails(device)
        }
    }

    override fun connect(config: WifiP2pConfig?) {
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(
                    this@MainActivity, "Connect failed. Retry.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun disconnect() {
        detailsFragment?.resetViews()
        manager!!.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onFailure(reasonCode: Int) {
                Log.d(TAG, "Disconnect failed. Reason :$reasonCode")
            }

            override fun onSuccess() {
                detailsFragment?.view!!.visibility = View.GONE
            }
        })
    }

    override fun onChannelDisconnected() {
        if (manager != null && !retryChannel) {
            Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show()
            resetData()
            retryChannel = true
            manager!!.initialize(this, mainLooper, this)
        } else {
            Toast.makeText(
                this,
                "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun cancelConnect() {
        val fragment = fragmentManager
            .findFragmentById(R.id.frag_list) as DeviceListFragment
        if (fragment.device == null
            || fragment.device!!.status == WifiP2pDevice.CONNECTED
        ) {
            disconnect()
        } else if (fragment.device!!.status == WifiP2pDevice.AVAILABLE
            || fragment.device!!.status == WifiP2pDevice.INVITED
        ) {
            manager?.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        this@MainActivity, "Aborting connection",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailure(reasonCode: Int) {
                    Toast.makeText(
                        this@MainActivity,
                        "Connect abort request failed. Reason Code: $reasonCode",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    private fun discoverPeers() {
        manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(
                    this@MainActivity, "Discovery Initiated",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onFailure(reasonCode: Int) {
                Toast.makeText(
                    this@MainActivity, "Discovery Failed : $reasonCode",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

    }

    companion object {
        const val TAG = "wifidirectdemo"
    }
}