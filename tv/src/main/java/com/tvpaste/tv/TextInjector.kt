package com.tvpaste.tv

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Odaklanilmis metin alanina metin ekler.
 * Mevcut metin SILINMEZ; imlecin oldugu yere eklenir.
 */
object TextInjector {

    private const val TAG = "PasteService"

    fun inject(service: AccessibilityService, text: String): Boolean {
        val root = service.rootInActiveWindow
        if (root == null) {
            Log.w(TAG, "inject: rootInActiveWindow yok")
            return false
        }

        val node = findInputNode(root)
        if (node == null) {
            Log.w(TAG, "inject: odakli duzenlenebilir alan bulunamadi")
            NodeDumper.dump(root)
            return false
        }

        val current = currentText(node)
        val start = safeSelection(node.textSelectionStart, current.length)
        val end = safeSelection(node.textSelectionEnd, current.length).coerceAtLeast(start)

        val newText = current.substring(0, start) + text + current.substring(end)
        val cursor = start + text.length

        Log.i(TAG, "inject: uzunluk=${text.length}, imlec=$start,$end")

        if (applySetText(node, newText, cursor)) {
            Log.i(TAG, "inject: ACTION_SET_TEXT basarili")
            return true
        }

        Log.w(TAG, "inject: ACTION_SET_TEXT basarisiz, pano yedegi denenecek")
        if (pasteFallback(service, node, text)) {
            Log.i(TAG, "inject: pano yedegi basarili")
            return true
        }

        Log.w(TAG, "inject: basarisiz")
        return false
    }

    private fun findInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        return findEditableFocused(root)
    }

    private fun findEditableFocused(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableFocused(child)
            if (found != null) return found
        }
        return null
    }

    /**
     * Alanin mevcut metni. Alan bir ipucu (hint) metni gosteriyorsa
     * bunu gercek metin saymayiz.
     */
    private fun currentText(node: AccessibilityNodeInfo): String {
        if (node.isShowingHintText) return ""
        return node.text?.toString() ?: ""
    }

    private fun safeSelection(value: Int, length: Int): Int {
        return if (value < 0 || value > length) length else value
    }

    private fun applySetText(node: AccessibilityNodeInfo, newText: String, cursor: Int): Boolean {
        val args = Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            newText
        )
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false

        val selection = Bundle()
        selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
        selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
        return true
    }

    private fun pasteFallback(
        service: AccessibilityService,
        node: AccessibilityNodeInfo,
        text: String
    ): Boolean {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText("TVPaste", text))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }
}
