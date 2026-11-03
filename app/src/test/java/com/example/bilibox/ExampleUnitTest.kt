package com.example.bilibox

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun searchDurationConvertsUnboundedMinutesToHours() {
        assertEquals("75:11:45", formatSearchDuration("4511:45"))
        assertEquals("15:08", formatSearchDuration("15:8"))
        assertEquals("--:--", formatSearchDuration("--:--"))
    }
}
