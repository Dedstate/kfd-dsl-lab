package lesson09.support.json

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class JsonParserTest {
    @Test
    fun parsesSupportedTypesAndWhitespace() {
        val source = " \t\r\n" + """{"id":42,"name":"Ivanov","active":true,"deleted":false,"note":null}"""
        val result = assertIs<JsonParseResult.Success>(parseJsonObject(source))
        assertEquals(42L, result.fields["id"])
        assertEquals("Ivanov", result.fields["name"])
        assertEquals(true, result.fields["active"])
        assertEquals(false, result.fields["deleted"])
        assertTrue(result.fields.containsKey("note"))
        assertEquals(null, result.fields["note"])
        assertFalse(result.fields.containsKey("missing"))
        assertEquals(emptyMap(), assertIs<JsonParseResult.Success>(parseJsonObject("{} ")).fields)
    }

    @Test
    fun decodesSupportedEscapes() {
        val result = assertIs<JsonParseResult.Success>(
            parseJsonObject("""{"quote":"He said \"yes\"","path":"C:\\tmp","a\"b":""}""")
        )
        assertEquals("He said \"yes\"", result.fields["quote"])
        assertEquals("C:\\tmp", result.fields["path"])
        assertEquals("", result.fields["a\"b"])
    }

    @Test
    fun preservesLongBoundariesAndStringType() {
        val result = assertIs<JsonParseResult.Success>(
            parseJsonObject("""{"min":-9223372036854775808,"max":9223372036854775807,"zero":-0,"text":"42"}""")
        )
        assertEquals(Long.MIN_VALUE, result.fields["min"])
        assertEquals(Long.MAX_VALUE, result.fields["max"])
        assertEquals(0L, result.fields["zero"])
        assertNotEquals(42L, result.fields["text"])
        assertEquals("42", result.fields["text"])
    }

    @Test
    fun lastDuplicateKeyWins() {
        val result = assertIs<JsonParseResult.Success>(parseJsonObject("""{"id":1,"id":null}"""))
        assertEquals(1, result.fields.size)
        assertTrue(result.fields.containsKey("id"))
        assertEquals(null, result.fields["id"])
    }

    @Test
    fun rejectsInvalidOrUnsupportedInput() {
        val sources = listOf(
            "", " ", "null", "[]", "{", "{id:1}", "{} {}", "{}x",
            """{"id":1,}""", """{"id" 1}""", """{"id":}""", """{"id":1 "name":"A"}""",
            """{"id":{}}""", """{"id":[]}""", """{"id":1.5}""", """{"id":1e2}""",
            """{"id":01}""", """{"id":-01}""", """{"id":+1}""", """{"id":--1}""",
            """{"id":9223372036854775808}""", """{"id":-9223372036854775809}""",
            """{"id":True}""", """{"id":truefalse}""", """{"id":"\n"}""",
            """{"id":"\u0041"}""", """{"id":"\/"}""", """{"id":"unfinished}""",
            "{\"id\":\"line\nbreak\"}", "{\"id\":\"\u0000\"}", "\u000C{}",
        )
        for (source in sources) {
            val result = assertIs<JsonParseResult.Failure>(parseJsonObject(source), source)
            assertTrue(result.reason.isNotBlank(), source)
        }
    }

    @Test
    fun laterCallsDoNotChangeEarlierResults() {
        val first = assertIs<JsonParseResult.Success>(parseJsonObject("""{"id":1}"""))
        assertIs<JsonParseResult.Failure>(parseJsonObject("invalid"))
        val second = assertIs<JsonParseResult.Success>(parseJsonObject("""{"id":2}"""))
        assertEquals(1L, first.fields["id"])
        assertEquals(2L, second.fields["id"])
    }
}
