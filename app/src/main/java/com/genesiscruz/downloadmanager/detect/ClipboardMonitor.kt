package com.genesiscruz.downloadmanager.detect

import android.content.ClipboardManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the clipboard when the app comes to the foreground (Android only
 * allows clipboard access to the focused app) and reports a URL at most once.
 */
@Singleton
class ClipboardMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lastOffered: String? = null

    /** Returns a not-yet-offered http(s) URL from the clipboard, or null. */
    fun detectNewUrl(): String? {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()
            ?: return null
        val url = UrlUtils.extractUrl(text) ?: return null
        if (url == lastOffered) return null
        lastOffered = url
        return url
    }
}
