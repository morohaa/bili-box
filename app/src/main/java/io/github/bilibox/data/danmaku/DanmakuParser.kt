package io.github.bilibox.data.danmaku



/**
 * 解析 B 站 `x/v1/dm/list.so` 返回的 XML。
 *
 * 网络层负责把 deflate/gzip 响应解成文本，这里只处理 `<d p="...">text</d>` 的
 * 字段映射和 XML 转义，便于用 JVM 单元测试覆盖。
 */
internal object DanmakuParser {
    private const val DEFAULT_LIMIT = 6_000
    private val entryPattern = Regex(
        pattern = """<d\s+p="([^"]*)"[^>]*>(.*?)</d>""",
        option = RegexOption.DOT_MATCHES_ALL,
    )
    private val entityPattern = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

    fun parse(xml: String, limit: Int = DEFAULT_LIMIT): List<Danmaku> {
        if (xml.isBlank()) return emptyList()
        return entryPattern.findAll(xml)
            .asSequence()
            .mapNotNull { match -> parseEntry(match.groupValues[1], match.groupValues[2]) }
            .sortedBy { it.timeMs }
            .take(limit.coerceAtLeast(1))
            .toList()
    }

    private fun parseEntry(rawFields: String, rawText: String): Danmaku? {
        val fields = rawFields.split(',')
        if (fields.size < 4) return null
        val timeMs = fields[0].trim().toDoubleOrNull()?.times(1_000)?.toLong() ?: return null
        if (timeMs < 0L) return null
        val mode = when (fields[1].trim().toIntOrNull()) {
            1, 2, 3 -> Danmaku.Mode.SCROLL
            4 -> Danmaku.Mode.BOTTOM
            5 -> Danmaku.Mode.TOP
            else -> return null
        }
        val fontSize = fields[2].trim().toIntOrNull()?.coerceIn(12, 64) ?: 25
        val color = fields[3].trim().toIntOrNull()?.and(0xFFFFFF) ?: 0xFFFFFF
        val text = decodeEntities(rawText).trim()
        if (text.isEmpty()) return null
        val id = fields.getOrNull(7)?.trim()?.toLongOrNull() ?: 0L
        return Danmaku(
            timeMs = timeMs,
            mode = mode,
            fontSize = fontSize,
            color = color,
            text = text,
            id = id,
        )
    }

    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return entityPattern.replace(text) { match ->
            when (val entity = match.groupValues[1]) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> decodeNumericEntity(entity) ?: match.value
            }
        }
    }

    private fun decodeNumericEntity(entity: String): String? {
        if (!entity.startsWith("#")) return null
        val codePoint = when {
            entity.startsWith("#x", ignoreCase = true) ->
                entity.substring(2).toIntOrNull(16)
            else -> entity.substring(1).toIntOrNull()
        } ?: return null
        if (!Character.isValidCodePoint(codePoint)) return null
        return String(Character.toChars(codePoint))
    }
}
