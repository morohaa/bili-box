package io.github.bilibox.data.danmaku



/**
 * 一条可显示的弹幕。
 *
 * [color] 使用 B 站 XML 里的 0xRRGGBB 数值，不带 alpha；绘制时再补成不透明色。
 * 目前只保留普通滚动、顶部固定和底部固定三种模式，高级弹幕（7/8）与逆向弹幕（6）
 * 依赖各自的位置脚本，直接忽略比用错误的轨迹绘制更安全。
 */
internal data class Danmaku(
    val timeMs: Long,
    val mode: Mode,
    val fontSize: Int,
    val color: Int,
    val text: String,
    val id: Long = 0L,
) {
    enum class Mode { SCROLL, TOP, BOTTOM }
}
