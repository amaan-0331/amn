package dev.amaan.amn.widgets.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AwqafTest {
    private fun body(days: Int, official: Boolean = true): String {
        val day = JSONObject(mapOf("fajer" to "05:04", "shuroq" to "06:18", "dhohur" to "12:06", "aser" to "15:19", "maghreb" to "17:48", "isha" to "19:02"))
        val result = JSONObject()
            .put("prayer_times", JSONArray(List(days) { day }))
            .put("meta_data", JSONObject().put("method", JSONObject().put("official", official)))
        return JSONObject().put("result", result).toString()
    }

    @Test
    fun `parse encodes a year of official times`() {
        val times = Awqaf.parse(body(366))
        assertEquals(366 * 24, times.length)
        assertTrue(times.startsWith("050406181206151917481902"))
    }

    @Test
    fun `parse rejects anything else`() {
        assertFailsWith<AwqafException> { Awqaf.parse(body(365)) }
        assertFailsWith<AwqafException> { Awqaf.parse(body(366, official = false)) }
        assertFailsWith<AwqafException> { Awqaf.parse("""{"message":"Invalid or expired API key","result":null}""") }
        assertFailsWith<AwqafException> { Awqaf.parse("not json") }
    }
}
