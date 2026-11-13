package com.tvpaste.tv

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException

/**
 * TV'nin yerel ag (IPv4) adresini bulur.
 * 192.168.x.x gibi site-local adresler tercih edilir.
 */
object LocalIpFinder {

    private const val TAG = "PasteService"

    fun findIpv4Address(): String? {
        return try {
            val addresses = NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList() }
                ?.filterIsInstance<Inet4Address>()
                ?: return null

            val siteLocal = addresses.firstOrNull { it.isSiteLocalAddress }
            (siteLocal ?: addresses.firstOrNull())?.hostAddress
        } catch (e: SocketException) {
            Log.w(TAG, "IP adresi okunamadi: ${e.message}")
            null
        }
    }
}
