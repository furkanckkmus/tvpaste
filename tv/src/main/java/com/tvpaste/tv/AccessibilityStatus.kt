package com.tvpaste.tv

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Erisilebilirlik servisinin acik olup olmadigini sistem ayarlarindan okur.
 */
object AccessibilityStatus {

    private const val PACKAGE_NAME = "com.tvpaste.tv"
    private const val SERVICE_CLASS = "PasteService"

    fun isEnabled(context: Context): Boolean {
        val enabledFlag = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        if (enabledFlag != 1) return false

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (entry in splitter) {
            if (matches(entry)) return true
        }
        return false
    }

    /**
     * Ayarlarda servis iki bicimde yazilabilir:
     * "com.tvpaste.tv/com.tvpaste.tv.PasteService" veya "com.tvpaste.tv/.PasteService".
     */
    private fun matches(entry: String): Boolean {
        val parts = entry.split('/')
        if (parts.size != 2) return false
        val packageName = parts[0].trim()
        val className = parts[1].trim()
        if (packageName != PACKAGE_NAME) return false
        return className == SERVICE_CLASS || className.endsWith(".$SERVICE_CLASS")
    }
}
