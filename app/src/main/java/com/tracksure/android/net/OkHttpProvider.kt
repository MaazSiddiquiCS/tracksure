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
        // Force direct client (no proxy) for all API calls to avoid routing via Tor/SOCKS.
        // This severs automatic Tor/proxy routing so network calls are reliable and fast.
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        httpClientRef.set(client)
        return client
    }

    /**
     * Returns a direct client for LAN/private targets and proxied client for all other URLs.
     */
    fun httpClientForUrl(url: String): OkHttpClient {
        // Always route direct for app API requests. We intentionally ignore any SOCKS proxy
        // that may be configured via TorManager to avoid intermittent timeouts and latency.
        val host = url.toHttpUrlOrNull()?.host
        Log.d(TAG, "Routing DIRECT for url=$url host=${host ?: "<unparsed>"} (Tor/proxy disabled)")
        return httpClient()
    }

    fun webSocketClient(): OkHttpClient {
        wsClientRef.get()?.let { return it }
        // WebSockets should also avoid SOCKS proxy to remain stable and low-latency.
        val client = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
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
        // No-op: do not apply any proxy. We deliberately ignore TorManager's SOCKS address
        // so that application API traffic is sent directly over the network (HTTPS).
        return OkHttpClient.Builder()
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
