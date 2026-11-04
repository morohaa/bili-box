package com.example.bilibox.bili

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

/**
 * 解码弹幕接口的响应体。支持原始文本、gzip、zlib 和 raw deflate 四种形态；
 * 解码失败时退回 UTF-8 文本，交给弹幕解析器给出空列表而不是让播放整体失败。
 *
 * B 站 CDN 实测会忽略 `Accept-Encoding: gzip` 并直接返回 `deflate` 的 raw
 * deflate 数据，所以不能只依赖 OkHttp 的自动解压。
 */
internal fun decodeDanmakuPayload(payload: ByteArray): String {
    if (payload.isEmpty()) return ""
    if (isXml(payload)) return String(payload, Charsets.UTF_8).removePrefix("\uFEFF")

    val gzip = runCatching {
        GZIPInputStream(ByteArrayInputStream(payload)).use { it.readBytes() }
    }.getOrNull()
    if (gzip != null) return String(gzip, Charsets.UTF_8)

    // Some CDNs label raw deflate as zlib (or vice versa); try both wrappers
    // before giving up so a header mismatch does not make every video look
    // like it has no comments.
    val inflated = runCatching { inflate(payload, nowrap = !isZlib(payload)) }.getOrNull()
        ?: runCatching { inflate(payload, nowrap = isZlib(payload)) }.getOrNull()
    if (inflated != null && isXml(inflated)) {
        return String(inflated, Charsets.UTF_8).removePrefix("\uFEFF")
    }
    return String(payload, Charsets.UTF_8)
}

private fun isXml(bytes: ByteArray): Boolean {
    var index = 0
    // Skip a UTF-8 BOM and leading whitespace before looking for the XML marker.
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        index = 3
    }
    while (index < bytes.size && bytes[index].toInt().toChar().isWhitespace()) index++
    return index < bytes.size && bytes[index] == '<'.code.toByte()
}

/** zlib 头两个字节满足 CMF/FLG 校验；raw deflate 没有这个头。 */
private fun isZlib(bytes: ByteArray): Boolean {
    if (bytes.size < 2) return false
    val cmf = bytes[0].toInt() and 0xFF
    val flg = bytes[1].toInt() and 0xFF
    return (cmf and 0x0F) == 8 && ((cmf shl 8) + flg) % 31 == 0
}

private fun inflate(payload: ByteArray, nowrap: Boolean): ByteArray {
    val inflater = Inflater(nowrap)
    val output = ByteArrayOutputStream(payload.size.coerceAtLeast(64))
    val buffer = ByteArray(8 * 1024)
    try {
        inflater.setInput(payload)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                output.write(buffer, 0, count)
            }
        }
    } finally {
        inflater.end()
    }
    return output.toByteArray()
}
