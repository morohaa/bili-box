package io.github.bilibox.data.online

internal data class OnlineCacheVideoDestination(
    val relativeDirectory: String,
    val displayName: String,
)

/** Single-part videos use the title; multi-part videos group by title and BV. */
internal fun onlineCacheVideoDestination(
    title: String,
    bvid: String,
    totalParts: Int,
    page: Int,
    part: String,
): OnlineCacheVideoDestination {
    val safeTitle = safeCacheComponent(title, "BiliBox")
    if (totalParts <= 1) {
        return OnlineCacheVideoDestination("Movies/BiliBox", "$safeTitle.mp4")
    }
    // Limit title and BV separately so long titles never remove the identifier.
    val safeBvid = safeCacheComponent(bvid, "unknown").take(20)
    val safePart = safeCacheComponent(part, "第 $page 部分")
    return OnlineCacheVideoDestination(
        relativeDirectory = "Movies/BiliBox/$safeTitle【$safeBvid】",
        displayName = "【P$page】$safePart.mp4",
    )
}

private fun safeCacheComponent(value: String, fallback: String): String = value.trim().map { char ->
    if (char < ' ' || char == '\u007f' || char in "\"*/:<>?\\|") '_' else char
}.joinToString("")
    // At most 180 UTF-8 bytes; leave room for the page/BV and extension.
    .take(60)
    .trimEnd { it.isHighSurrogate() }
    .trim(' ', '.')
    .ifBlank { fallback }
