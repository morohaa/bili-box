package io.github.bilibox.feature.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.File

/** 无网络依赖的 30 秒灰阶视频，前后段亮度不同，便于验证目标帧而非当前帧。 */
internal fun createSeekPreviewFixture(context: Context): File {
    val file = File(context.cacheDir, "seek-preview-fixture.mp4")
    val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    var started = false
    try {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 160, 96).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 2)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val info = MediaCodec.BufferInfo()
        var frame = 0
        var track = -1
        var ended = false
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        while (!ended) {
            check(SystemClock.elapsedRealtime() < deadline) { "测试视频编码超时" }
            if (frame <= 60) {
                val input = encoder.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    if (frame == 60) {
                        encoder.queueInputBuffer(input, 0, 0, 30_000_000L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val pixels = ByteArray(160 * 96 * 3 / 2) { 128.toByte() }
                        pixels.fill((40 + frame / 20 * 80).toByte(), 0, 160 * 96)
                        encoder.getInputBuffer(input)!!.apply {
                            clear()
                            put(pixels)
                        }
                        encoder.queueInputBuffer(input, 0, pixels.size, frame * 500_000L, 0)
                    }
                    frame++
                }
            }
            val output = encoder.dequeueOutputBuffer(info, 10_000)
            if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(encoder.outputFormat)
                muxer.start()
                started = true
            } else if (output >= 0) {
                if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                    muxer.writeSampleData(track, encoder.getOutputBuffer(output)!!, info)
                }
                ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                encoder.releaseOutputBuffer(output, false)
            }
        }
    } finally {
        runCatching { encoder.stop() }
        encoder.release()
        if (started) muxer.stop()
        muxer.release()
    }
    return file
}
