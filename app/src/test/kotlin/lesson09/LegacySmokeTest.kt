package lesson09

import lesson09.legacy.check01
import lesson09.legacy.check02
import lesson09.legacy.check03
import lesson09.legacy.check04
import lesson09.legacy.check05
import lesson09.legacy.check06
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacySmokeTest {
    @Test
    fun noContentAllowsAbsentOrEmptyBody() {
        assertTrue(check01(HttpResponse(204, emptyMap(), null)))
        assertTrue(check01(HttpResponse(204, emptyMap(), "")))
        assertFalse(check01(HttpResponse(204, emptyMap(), " ")))
        assertFalse(check01(HttpResponse(200, emptyMap(), null)))
    }

    @Test
    fun nonEmptyBodyDoesNotRequireValidJson() {
        val response = HttpResponse(
            200, mapOf("content-type" to listOf("application/json; charset=utf-8")), "invalid"
        )
        assertTrue(check02(response))
        assertFalse(check02(response.copy(headers = emptyMap())))
        assertFalse(check02(response.copy(body = "")))
    }

    @Test
    fun twoHeaderConditionsCanUseDifferentValues() {
        val response = HttpResponse(
            200, linkedMapOf("Accept" to listOf("application/json"), "accept" to listOf("text/html")), null
        )
        assertTrue(check03(response))
        assertFalse(check03(response.copy(headers = mapOf("Accept" to listOf("application/json")))))
        assertFalse(check03(response.copy(headers = mapOf("Accept" to listOf("application/json,text/html")))))
    }

    @Test
    fun presentNullIsDifferentFromMissingKey() {
        assertTrue(check04(HttpResponse(200, emptyMap(), """{"id":null,"name":"Ivanov"}""")))
        assertFalse(check04(HttpResponse(200, emptyMap(), """{"name":"Ivanov"}""")))
    }

    @Test
    fun numericAndBooleanValuesKeepTheirTypes() {
        val response = HttpResponse(200, emptyMap(), """{"id":42,"active":true}""")
        assertTrue(check05(response))
        assertFalse(check05(response.copy(body = """{"id":"42","active":true}""")))
        assertFalse(check05(response.copy(body = """{"id":42,"active":false}""")))
    }

    @Test
    fun errorResponseRequiresStatusHeaderAndJsonFields() {
        val response = HttpResponse(
            422, mapOf("Content-Type" to listOf("application/json")),
            """{"error":"invalid","retry":false}"""
        )
        assertTrue(check06(response))
        assertFalse(check06(response.copy(status = 200)))
        assertFalse(check06(response.copy(headers = emptyMap())))
        assertFalse(check06(response.copy(body = "invalid")))
        assertFalse(check06(response.copy(body = """{"error":"invalid","retry":true}""")))
    }
}
