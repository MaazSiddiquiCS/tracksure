package com.tracksure.android.bridgeupload.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.tracksure.android.bridgeupload.BridgeUploadConfig

/**
 * Checks if uploads are allowed on current network.
 */
class ConnectivityGate(
    private val context: Context,
    private val config: BridgeUploadConfig
) {
    fun canUploadNow(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: run {
                Log.w(TAG, "ConnectivityManager unavailable")
                return false
            }
        val network = cm.activeNetwork ?: run {
            Log.i(TAG, "No active network")
            return false
        }
        val caps = cm.getNetworkCapabilities(network) ?: run {
            Log.i(TAG, "No capabilities for active network")
            return false
        }

        val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        if (config.requireWifiTransport && !hasWifi) {
            Log.i(TAG, "Upload blocked: network is not Wi-Fi")
            return false
        }
        if (!hasInternet) {
            Log.i(TAG, "Upload blocked: network has no INTERNET capability")
            return false
        }
        if (config.requireValidatedNetwork && !isValidated) {
            Log.i(TAG, "Upload blocked: network is not VALIDATED")
            return false
        }

        Log.d(
            TAG,
            "Upload allowed wifi=$hasWifi internet=$hasInternet validated=$isValidated " +
                "requireWifi=${config.requireWifiTransport} requireValidated=${config.requireValidatedNetwork}"
        )
        return true
    }

    companion object {
        private const val TAG = "BridgeUpload"
    }
}


