package com.tvpaste.phone

/** "192.168.1.25" gibi bir IPv4 adresinin gecerli olup olmadigini kontrol eder. */
object IpAddressChecker {

    fun isValid(text: String): Boolean {
        val parts = text.trim().split(".")
        if (parts.size != 4) return false
        for (part in parts) {
            if (part.isEmpty() || part.length > 3) return false
            if (!part.all { it.isDigit() }) return false
            val value = part.toIntOrNull() ?: return false
            if (value !in 0..255) return false
        }
        return true
    }
}
