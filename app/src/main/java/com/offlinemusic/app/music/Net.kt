package com.offlinemusic.app.music

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

private const val PREFS = "oma"
private const val KEY_WIFI_ONLY = "wifi_only"

fun isWifiOnly(ctx: Context): Boolean =
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_WIFI_ONLY, true)

fun setWifiOnly(ctx: Context, on: Boolean) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_WIFI_ONLY, on).apply()
}

fun connectionLabel(ctx: Context): String {
    val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        else -> "online"
    }
}

/** Official behavior: downloads only on unmetered WiFi/Ethernet unless user opts out. */
fun canDownloadNow(ctx: Context): Pair<Boolean, String> {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
        ?: return false to "No connectivity service."
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        ?: return false to "You are offline."
    if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
        return false to "No verified internet connection."
    }
    if (!isWifiOnly(ctx)) return true to ""
    val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    if (!wifi || metered) {
        return false to "WiFi-only is ON. Connect to unmetered WiFi or turn it off in Settings."
    }
    return true to ""
}
