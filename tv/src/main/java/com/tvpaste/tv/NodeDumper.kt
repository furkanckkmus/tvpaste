package com.tvpaste.tv

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Metin yazilamadiginda erisilebilirlik agacini logcat'e doker.
 * Sadece sinif adi, isEditable ve isFocused yazilir (hassas veri yok).
 */
object NodeDumper {

    private const val TAG = "PasteService"
    private const val MAX_DEPTH = 12
    private const val MAX_NODES = 60

    private var printed = 0

    fun dump(root: AccessibilityNodeInfo) {
        printed = 0
        Log.w(TAG, "--- agac baslangici ---")
        walk(root, 0)
        Log.w(TAG, "--- agac sonu ($printed dugum) ---")
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > MAX_DEPTH || printed >= MAX_NODES) return
        printed++
        Log.w(TAG, ind(depth) + describe(node))
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1)
        }
    }

    private fun describe(node: AccessibilityNodeInfo): String {
        return "class=${node.className} editable=${node.isEditable} focused=${node.isFocused}"
    }

    private fun ind(depth: Int): String {
        val builder = StringBuilder()
        for (i in 0 until depth) builder.append("  ")
        return builder.toString()
    }
}
