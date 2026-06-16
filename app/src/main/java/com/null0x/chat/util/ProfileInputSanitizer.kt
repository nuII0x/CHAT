package com.null0x.chat.util

import android.icu.text.BreakIterator
import java.util.Locale

internal fun normalizeProfileNameInput(text: String): String {
    val clean = text.trimStart()
    if (clean.isBlank()) return ""
    return clean.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
    }
}

internal fun normalizeProfileEmojiInput(text: String): String {
    val clean = text.trim()
    if (clean.isBlank()) return ""
    val iterator = BreakIterator.getCharacterInstance(Locale.getDefault())
    iterator.setText(clean)
    var start = iterator.first()
    while (true) {
        val end = iterator.next()
        if (end == BreakIterator.DONE) break
        val cluster = clean.substring(start, end).trim()
        if (isEmojiCluster(cluster)) {
            return cluster
        }
        start = end
    }
    return ""
}

private fun isEmojiCluster(cluster: String): Boolean {
    if (cluster.isBlank()) return false
    val codePoints = cluster.codePoints().toArray()
    return codePoints.any { isEmojiCodePoint(it) }
}

private fun isEmojiCodePoint(codePoint: Int): Boolean {
    return when {
        codePoint in 0x1F300..0x1FAFF -> true
        codePoint in 0x1F1E6..0x1F1FF -> true
        codePoint in 0x2600..0x27BF -> true
        codePoint in 0x2300..0x23FF -> true
        codePoint in 0x1F900..0x1F9FF -> true
        codePoint in 0x1F600..0x1F64F -> true
        codePoint in 0x1F680..0x1F6FF -> true
        codePoint in 0x1F700..0x1F77F -> true
        codePoint == 0x200D -> true
        codePoint == 0x20E3 -> true
        codePoint == 0xFE0F -> true
        codePoint in 0x1F3FB..0x1F3FF -> true
        else -> false
    }
}
