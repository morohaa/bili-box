package io.github.bilibox.data.bili

import java.net.URLEncoder
import java.security.MessageDigest

/** B 站 Web 端 WBI 参数签名。 */
internal object WbiSigner {
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
    )

    fun extractKey(imageUrl: String): String =
        imageUrl.substringAfterLast('/').substringBefore('.')

    fun mixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        require(raw.length >= 64) { "wbi key 长度异常: ${raw.length}" }
        return buildString(32) { repeat(32) { append(raw[mixinKeyEncTab[it]]) } }
    }

    fun sign(params: Map<String, String>, mixinKey: String, wts: Long = System.currentTimeMillis() / 1000L): String {
        val all = java.util.TreeMap<String, String>().apply {
            putAll(params)
            put("wts", wts.toString())
        }
        val query = all.entries.joinToString("&") { "${it.key}=${encode(it.value)}" }
        return "$query&w_rid=${md5(query + mixinKey)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~")

    private fun md5(input: String): String = buildString(32) {
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).forEach { byte ->
            val value = byte.toInt() and 0xFF
            append("0123456789abcdef"[value ushr 4])
            append("0123456789abcdef"[value and 0x0F])
        }
    }
}
