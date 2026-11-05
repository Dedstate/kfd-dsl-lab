package lesson09

import lesson09.dsl.ValidationResult
import lesson09.dsl.check01Dsl
import lesson09.dsl.check02Dsl
import lesson09.dsl.check03Dsl
import lesson09.dsl.check04Dsl
import lesson09.dsl.check05Dsl
import lesson09.dsl.check06Dsl
import lesson09.legacy.check01
import lesson09.legacy.check02
import lesson09.legacy.check03
import lesson09.legacy.check04
import lesson09.legacy.check05
import lesson09.legacy.check06
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DslLegacyParityTest {
    private data class Case(
        val name: String,
        val original: (HttpResponse) -> Boolean,
        val dsl: (HttpResponse) -> ValidationResult,
        val valid: HttpResponse,
        val variations: List<HttpResponse>,
    )

    @Test
    fun everyLegacyCheckMatchesWhenEachIndividualConditionIsBroken() {
        val first = HttpResponse(204, emptyMap(), null)
        val second = HttpResponse(200, mapOf("Content-Type" to listOf("application/json")), "not JSON")
        val third = HttpResponse(200, mapOf("Accept" to listOf("application/json", "text/html")), null)
        val fourth = HttpResponse(200, emptyMap(), """{"id":null,"name":"Ivanov"}""")
        val fifth = HttpResponse(200, emptyMap(), """{"id":42,"active":true}""")
        val sixth = HttpResponse(
            422,
            mapOf("Content-Type" to listOf("application/json")),
            """{"error":"invalid","retry":false}""",
        )

        val cases = listOf(
            Case(
                "check01", ::check01, ::check01Dsl, first,
                listOf(first.copy(status = 200), first.copy(body = " "), first.copy(body = "word")),
            ),
            Case(
                "check02", ::check02, ::check02Dsl, second,
                listOf(
                    second.copy(status = 204), second.copy(headers = emptyMap()),
                    second.copy(headers = mapOf("Content-Type" to listOf("text/plain"))),
                    second.copy(body = null), second.copy(body = ""),
                ),
            ),
            Case(
                "check03", ::check03, ::check03Dsl, third,
                listOf(
                    third.copy(status = 201),
                    third.copy(headers = mapOf("Accept" to listOf("application/json"))),
                    third.copy(headers = mapOf("Accept" to listOf("text/html"))),
                    third.copy(headers = mapOf("Accept" to listOf("application/json,text/html"))),
                ),
            ),
            Case(
                "check04", ::check04, ::check04Dsl, fourth,
                listOf(
                    fourth.copy(status = 204), fourth.copy(body = null),
                    fourth.copy(body = "bad-json"),
                    fourth.copy(body = """{"name":"Ivanov"}"""),
                    fourth.copy(body = """{"id":null,"name":"ivanov"}"""),
                ),
            ),
            Case(
                "check05", ::check05, ::check05Dsl, fifth,
                listOf(
                    fifth.copy(status = 204), fifth.copy(body = "bad-json"),
                    fifth.copy(body = """{"active":true}"""),
                    fifth.copy(body = """{"id":"42","active":true}"""),
                    fifth.copy(body = """{"id":42,"active":false}"""),
                    fifth.copy(body = """{"id":42}"""),
                ),
            ),
            Case(
                "check06", ::check06, ::check06Dsl, sixth,
                listOf(
                    sixth.copy(status = 200), sixth.copy(headers = emptyMap()),
                    sixth.copy(body = null), sixth.copy(body = "bad-json"),
                    sixth.copy(body = """{"retry":false}"""),
                    sixth.copy(body = """{"error":"wrong","retry":false}"""),
                    sixth.copy(body = """{"error":"invalid","retry":true}"""),
                ),
            ),
        )

        for (case in cases) {
            assertTrue(case.original(case.valid), "${case.name}: base response must pass")
            assertTrue(case.dsl(case.valid).isValid, "${case.name}: DSL must pass")
            for ((index, invalid) in case.variations.withIndex()) {
                assertFalse(case.original(invalid), "${case.name} variation $index must fail")
                assertFalse(case.dsl(invalid).isValid, "${case.name} DSL variation $index must fail")
                assertEquals(case.original(invalid), case.dsl(invalid).discrepancies.isEmpty())
            }
        }
    }

    @Test
    fun extraHeadersAndFieldsDoNotAddRestrictions() {
        val responses = listOf(
            HttpResponse(204, mapOf("Extra" to listOf("value")), ""),
            HttpResponse(200, mapOf("CONTENT-TYPE" to listOf("application/json;charset=UTF-8"), "Extra" to listOf("x")), "not JSON"),
            HttpResponse(200, linkedMapOf("accept" to listOf("application/json"), "ACCEPT" to listOf("text/html")), null),
            HttpResponse(200, emptyMap(), """{"id":null,"name":"Ivanov","extra":true}"""),
            HttpResponse(200, emptyMap(), """{"id":42,"active":true,"extra":null}"""),
            HttpResponse(422, mapOf("Content-Type" to listOf("application/jsonp")), """{"error":"invalid","retry":false,"extra":1}"""),
        )
        val originals = listOf(::check01, ::check02, ::check03, ::check04, ::check05, ::check06)
        val checks = listOf(::check01Dsl, ::check02Dsl, ::check03Dsl, ::check04Dsl, ::check05Dsl, ::check06Dsl)
        for (index in responses.indices) {
            assertTrue(originals[index](responses[index]), "Original check ${index + 1}")
            assertTrue(checks[index](responses[index]).isValid, "DSL check ${index + 1}")
        }
    }
}
