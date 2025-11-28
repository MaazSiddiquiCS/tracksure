package com.tracksure.android.ui

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tracksure.android.mesh.PeerInfo

class MapState {

    // --- MAP DATA ---
    private val _peerLocations = MutableLiveData<Map<String, PeerInfo>>(emptyMap())
    val peerLocations: LiveData<Map<String, PeerInfo>> = _peerLocations

    private val _myLocation = MutableLiveData<Location?>(null)
    val myLocation: LiveData<Location?> = _myLocation

    // --- LEGACY / SHARED STATE (Keep these to avoid breaking other dependencies temporarily) ---
    private val _connectedPeers = MutableLiveData<List<String>>(emptyList())
    val connectedPeers: LiveData<List<String>> = _connectedPeers
    private val _connectedPeersCount = MutableLiveData<Int>(0)
    val connectedPeersCount: LiveData<Int> = _connectedPeersCount
    private val _nickname = MutableLiveData<String>()
    val nickname: LiveData<String> = _nickname

    private val _isConnected = MutableLiveData<Boolean>(false)
    val isConnected: LiveData<Boolean> = _isConnected

    // Getters
    fun getPeerLocationsValue() = _peerLocations.value ?: emptyMap()
    fun getMyLocationValue() = _myLocation.value
    fun getNicknameValue() = _nickname.value ?: "Unknown"

    // Setters
    fun setPeerLocations(locations: Map<String, PeerInfo>) {
        _peerLocations.postValue(locations)
    }

    fun setMyLocation(location: Location?) {
        _myLocation.postValue(location)
    }

    fun setConnectedPeers(peers: List<String>) {
        _connectedPeers.postValue(peers)
    }
    fun setConnectedPeersCount(count: Int) {
        _connectedPeersCount.postValue(count)
    }

    fun setNickname(name: String) {
        _nickname.postValue(name)
    }
    fun setIsConnected(connected: Boolean) {
        _isConnected.postValue(connected)
    }

}
