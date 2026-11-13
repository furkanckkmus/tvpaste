package com.tvpaste.phone

import android.content.Context

/** Elle girilen TV IP'sini kalici olarak saklar. */
object PhonePrefs {

    private const val FILE_NAME = "tvpaste_phone"
    private const val KEY_MANUAL_IP = "manual_ip"

    fun getManualIp(context: Context): String? {
        val value = prefs(context).getString(KEY_MANUAL_IP, "") ?: ""
        return value.trim().ifEmpty { null }
    }

    fun setManualIp(context: Context, ip: String) {
        prefs(context).edit().putString(KEY_MANUAL_IP, ip.trim()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
}
