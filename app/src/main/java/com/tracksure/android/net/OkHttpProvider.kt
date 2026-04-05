package com.tracksure.android.net

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Centralized OkHttp provider to ensure all network traffic honors Tor settings.
 */
object OkHttpProvider {
    private const val TAG = "OkHttpProvider"
    private val httpClientRef = AtomicReference<OkHttpClient?>(null)
    private val directHttpClientRef = AtomicReference<OkHttpClient?>(null)
    private val wsClientRef = AtomicReference<OkHttpClient?>(null)

    fun reset() {
        httpClientRef.set(null)
        directHttpClientRef.set(null)
        wsClientRef.set(null)
    }

    fun httpClient(): OkHttpClient {
        httpClientRef.get()?.let { return it }
        val client = proxiedHttpBuilderForCurrentProxy()
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        httpClientRef.set(client)
        return client
    }

    /**
     * Returns a direct client for LAN/private targets and proxied client for all other URLs.
     */
    fun httpClientForUrl(url: String): OkHttpClient {
        val host = url.toHttpUrlOrNull()?.host
        return if (host != null && isLanOrPrivateHost(host)) {
            directHttpClient().also { Log.d(TAG, "Routing DIRECT (no proxy) for host=$host") }
        } else {
            Log.d(TAG, "Routing PROXY for url=$url host=${host ?: "<unparsed>"}")
            httpClient()
        }
    }

    fun webSocketClient(): OkHttpClient {
        wsClientRef.get()?.let { return it }
        val client = proxiedHttpBuilderForCurrentProxy()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
        wsClientRef.set(client)
        return client
    }

    private fun directHttpClient(): OkHttpClient {
        directHttpClientRef.get()?.let { return it }
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .callTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        directHttpClientRef.set(client)
        return client
    }

    private fun proxiedHttpBuilderForCurrentProxy(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        val socks: InetSocketAddress? = TorManager.currentSocksAddress()
        // If a SOCKS address is defined, always use it. TorManager sets this as soon as Tor mode is ON,
        // even before bootstrap, to prevent any direct connections from occurring.
        if (socks != null) {
            val proxy = Proxy(Proxy.Type.SOCKS, socks)
            builder.proxy(proxy)
        }
        return builder
    }

    internal fun isLanOrPrivateHost(host: String): Boolean {
        val normalized = host.trim().lowercase()
        if (normalized == "localhost") return true
        if (normalized == "127.0.0.1") return true
        if (normalized.startsWith("10.")) return true
        if (normalized.startsWith("192.168.")) return true
        if (normalized.startsWith("172.")) {
            val second = normalized.split('.').getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        return false
    }
}
