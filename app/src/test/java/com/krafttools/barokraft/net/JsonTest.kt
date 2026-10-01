package com.krafttools.barokraft.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JSON reader.
 *
 * The tests that matter here are the ones about **absent versus zero**,
 * because that is the distinction a weather app gets wrong: a missing
 * rain probability rendered as 0% is a false statement about the weather,
 * not a formatting bug.
 */
class JsonTest {

    @Test
    fun `parses an object`() {
        val v = parseJson("""{"a":1,"b":"two"}""").asObject()
        assertEquals(1.0, v["a"].asFloatOrNull()!!.toDouble(), 0.0)
        assertEquals("two", v["b"].asStringOrNull())
    }

    @Test
    fun `parses nested structures`() {
        val v = parseJson("""{"a":{"b":[1,2,{"c":3}]}}""").asObject()
        val items = v["a"].asObject()["b"].asArray()
        assertEquals(3, items.size)
        assertEquals(3.0, items[2].asObject()["c"].asFloatOrNull()!!.toDouble(), 0.0)
    }

    @Test
    fun `an absent field is null, not zero`() {
        // The bug this whole reader exists to prevent.
        val v = parseJson("""{"temperature":21.5}""").asObject()
        assertNull("a missing field must not become 0", v["precipitation_probability"].asFloatOrNull())
    }

    @Test
    fun `an explicit JSON null is null, not zero`() {
        val v = parseJson("""{"precipitation_probability":null}""").asObject()
        assertNull(
            "a null probability must stay null or it renders as 0% chance of rain",
            v["precipitation_probability"].asFloatOrNull(),
        )
    }

    @Test
    fun `zero and null are different things`() {
        val withZero = parseJson("""{"p":0}""").asObject()["p"].asFloatOrNull()
        val withNull = parseJson("""{"p":null}""").asObject()["p"].asFloatOrNull()
        assertEquals("zero must survive as zero", 0f, withZero!!, 0.0f)
        assertNull("null must not become zero", withNull)
    }

    @Test
    fun `parses negative and fractional numbers`() {
        val v = parseJson("""{"t":-4.25,"p":1013.2}""").asObject()
        assertEquals(-4.25, v["t"].asFloatOrNull()!!.toDouble(), 0.0001)
        assertEquals(1013.2, v["p"].asFloatOrNull()!!.toDouble(), 0.0001)
    }

    @Test
    fun `parses exponent notation`() {
        val v = parseJson("""{"x":1.5e3,"y":2E-2}""").asObject()
        assertEquals(1500.0, v["x"].asFloatOrNull()!!.toDouble(), 0.01)
        assertEquals(0.02, v["y"].asFloatOrNull()!!.toDouble(), 0.0001)
    }

    @Test
    fun `a timestamp survives as a long where a float would not`() {
        // A Float holds integers exactly only to 2^24, and epoch seconds
        // are around 1.76e9 — so the float path is 32 seconds early. That
        // produces times which look right and are not, which is why it has
        // to be a test rather than a code review comment.
        val epoch = 1_759_264_800L
        val v = parseJson("""{"t":$epoch}""").asObject()["t"]
        assertEquals(epoch, v.asLongOrNull())
        assertTrue(
            "the float path really is lossy here",
            v.asFloatOrNull()!!.toLong() != epoch,
        )
    }

    @Test
    fun `asLongOrNull is null for something that is not a number`() {
        assertNull(parseJson("""{"a":"x"}""").asObject()["a"].asLongOrNull())
        assertNull(parseJson("""{"a":null}""").asObject()["a"].asLongOrNull())
    }

    @Test
    fun `keeps the raw text of a number`() {
        val v = parseJson("""{"p":1013.20}""").asObject()["p"]
        assertEquals("1013.20", (v as JsonValue.Num).raw)
    }

    @Test
    fun `parses escapes in strings`() {
        val v = parseJson("""{"s":"a\"b\\c\nd\te"}""").asObject()
        assertEquals("a\"b\\c\nd\te", v["s"].asStringOrNull())
    }

    @Test
    fun `parses a unicode escape`() {
        // "Zürich" — a real place whose name needs the escape, so the test
        // reads as what it checks rather than as an arbitrary string.
        val v = parseJson("""{"s":"Z\u00fcrich"}""").asObject()
        assertEquals("Zürich", v["s"].asStringOrNull())
    }

    @Test
    fun `parses booleans and null`() {
        val v = parseJson("""{"t":true,"f":false,"n":null}""").asObject()
        assertEquals(true, (v["t"] as JsonValue.Bool).value)
        assertEquals(false, (v["f"] as JsonValue.Bool).value)
        assertTrue(v["n"] is JsonValue.Null)
    }

    @Test
    fun `parses empty containers`() {
        assertEquals(0, parseJson("[]").asArray().size)
        assertEquals(0, parseJson("{}").asObject().fields.size)
    }

    @Test
    fun `tolerates whitespace and newlines`() {
        val v = parseJson("{\n  \"a\" : 1 ,\n  \"b\" : [ 1 , 2 ]\n}").asObject()
        assertEquals(1.0, v["a"].asFloatOrNull()!!.toDouble(), 0.0)
        assertEquals(2, v["b"].asArray().size)
    }

    @Test
    fun `rejects a truncated object rather than returning what it has`() {
        // A partial parse is the dangerous case: three days of a seven-day
        // forecast with no indication anything is missing.
        for (bad in listOf("""{"a":1""", """{"a":}""", """{"a" 1}""", "{", "[")) {
            try {
                parseJson(bad)
                throw AssertionError("should have rejected: $bad")
            } catch (e: Protocol.MalformedResponse) {
                // expected
            }
        }
    }

    @Test
    fun `rejects trailing content`() {
        try {
            parseJson("""{"a":1} garbage""")
            throw AssertionError("should have rejected trailing content")
        } catch (e: Protocol.MalformedResponse) {
            assertTrue(e.message!!.contains("trailing"))
        }
    }

    @Test
    fun `rejects an unterminated string`() {
        try {
            parseJson("""{"a":"unterminated}""")
            throw AssertionError("should have rejected an unterminated string")
        } catch (e: Protocol.MalformedResponse) {
            // expected
        }
    }

    @Test
    fun `a non-number is not a number`() {
        val v = parseJson("""{"a":"21.5","b":true}""").asObject()
        assertNull("a numeric string must not become a number", v["a"].asFloatOrNull())
        assertNull("a boolean must not become a number", v["b"].asFloatOrNull())
    }

    @Test
    fun `an array on a non-array field yields nothing rather than crashing`() {
        val v = parseJson("""{"a":{"b":1}}""").asObject()
        assertEquals(0, v["a"].asArray().size)
    }
}
