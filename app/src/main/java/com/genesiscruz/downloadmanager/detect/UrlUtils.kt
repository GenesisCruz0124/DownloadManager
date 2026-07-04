package com.genesiscruz.downloadmanager.detect

object UrlUtils {

    const val FALLBACK_NAME = "download"

    fun isHttpUrl(text: String): Boolean {
        val trimmed = text.trim()
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            runCatching { java.net.URI(trimmed).host != null }.getOrDefault(false)
    }

    /** Extracts the first http(s) URL found in [text], or null. */
    fun extractUrl(text: String): String? =
        Regex("""https?://\S+""").find(text)?.value?.trimEnd('.', ',', ')', ']', '>', '"', '\'')
            ?.takeIf { isHttpUrl(it) }

    fun isFallbackName(name: String): Boolean =
        name.isBlank() || name == FALLBACK_NAME || name.startsWith("$FALLBACK_NAME.")

    fun fileNameFromUrl(url: String): String? {
        val path = runCatching { java.net.URI(url).path }.getOrNull() ?: return null
        val name = path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        return sanitize(java.net.URLDecoder.decode(name, Charsets.UTF_8.name()))
    }

    /**
     * Parses `filename=` / `filename*=` from a Content-Disposition header.
     * Prefers the RFC 5987 `filename*` form when present.
     */
    fun fileNameFromContentDisposition(header: String): String? {
        Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)""").find(header)?.let {
            return sanitize(
                java.net.URLDecoder.decode(it.groupValues[1].trim(), Charsets.UTF_8.name())
            )
        }
        Regex("""filename\s*=\s*"([^"]+)"""").find(header)?.let {
            return sanitize(it.groupValues[1])
        }
        Regex("""filename\s*=\s*([^;\s]+)""").find(header)?.let {
            return sanitize(it.groupValues[1])
        }
        return null
    }

    /** Strips path separators and control characters so the name is safe on disk. */
    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]|[\x00-\x1f]"""), "_")
            .trim()
            .take(200)
            .ifBlank { FALLBACK_NAME }
}
