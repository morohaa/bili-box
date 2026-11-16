package io.github.bilibox.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineCacheNamingTest {
    @Test
    fun singlePartUsesTitleWithoutCreatingASubfolder() {
        val destination = onlineCacheVideoDestination("入门课程", "BV1test", 1, 1, "正片")
        assertEquals("Movies/BiliBox", destination.relativeDirectory)
        assertEquals("入门课程.mp4", destination.displayName)
    }

    @Test
    fun multiPartSharesTitleAndBvFolderAndKeepsOriginalPageNumbers() {
        val first = onlineCacheVideoDestination("入门课程", "BV1test", 12, 1, "起步")
        val tenth = onlineCacheVideoDestination("入门课程", "BV1test", 12, 10, "进阶")
        assertEquals("Movies/BiliBox/入门课程【BV1test】", first.relativeDirectory)
        assertEquals(first.relativeDirectory, tenth.relativeDirectory)
        assertEquals("【P1】起步.mp4", first.displayName)
        assertEquals("【P10】进阶.mp4", tenth.displayName)
        val other = onlineCacheVideoDestination("入门课程", "BV2test", 12, 1, "起步")
        assertTrue(first.relativeDirectory != other.relativeDirectory)
    }

    @Test
    fun unsafeCharactersCannotCreateExtraDirectories() {
        val destination = onlineCacheVideoDestination("../课程:入门", "BV1test", 2, 2, "打开 CMD? / 演示")
        assertEquals("Movies/BiliBox/_课程_入门【BV1test】", destination.relativeDirectory)
        assertEquals("【P2】打开 CMD_ _ 演示.mp4", destination.displayName)
    }

    @Test
    fun blankNamesHaveVisibleFallbacks() {
        val single = onlineCacheVideoDestination(" ... ", "BV1test", 1, 1, "")
        val multi = onlineCacheVideoDestination("", "BV1test", 2, 2, " . ")
        assertEquals("BiliBox.mp4", single.displayName)
        assertEquals("Movies/BiliBox/BiliBox【BV1test】", multi.relativeDirectory)
        assertEquals("【P2】第 2 部分.mp4", multi.displayName)
    }

    @Test
    fun longUnicodeNamesKeepBvAndPageWithinFilesystemLimits() {
        val destination = onlineCacheVideoDestination("课".repeat(200), "BV1test", 120, 100, "讲".repeat(200))
        assertTrue(destination.relativeDirectory.endsWith("【BV1test】"))
        assertTrue(destination.displayName.startsWith("【P100】"))
        assertTrue(destination.displayName.endsWith(".mp4"))
        assertTrue(destination.relativeDirectory.substringAfterLast('/').toByteArray(Charsets.UTF_8).size <= 255)
        assertTrue(destination.displayName.toByteArray(Charsets.UTF_8).size <= 255)
    }
}
