package lesson09

import lesson09.dsl.Discrepancy
import lesson09.dsl.ResponseSpecBuilder
import lesson09.dsl.validate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DslValidationTest {
    private fun response(
        status: Int = 200,
        headers: Map<String, List<String>> = emptyMap(),
        body: String? = null,
    ) = HttpResponse(status, headers, body)

    @Test
    fun exampleCollectsIndependentDiscrepanciesInOrder() {
        val result = validate(response(500, mapOf("content-type" to listOf("text/plain")), "{}")) {
            status { equalTo(200) }
            header("Content-Type") { startsWith("application/json") }
            body { json { field("id") { present() } } }
        }
        assertEquals(
            listOf(
                Discrepancy("status", "200", "500"),
                Discrepancy("headers.Content-Type", "prefix application/json", "[text/plain]"),
                Discrepancy("body.json.id", "present", "<missing>"),
            ),
            result.discrepancies,
        )
        assertEquals(
            "status: expected 200, actual 500\n" +
                "headers.Content-Type: expected prefix application/json, actual [text/plain]\n" +
                "body.json.id: expected present, actual <missing>",
            result.report(),
        )
    }

    @Test
    fun bodyNullEmptyWhitespaceAndNonemptyAreDistinct() {
        for (body in listOf<String?>(null, "")) {
            assertTrue(validate(response(body = body)) { body { absentOrEmpty() } }.isValid)
            assertFalse(validate(response(body = body)) { body { nonEmpty() } }.isValid)
        }
        for (body in listOf(" ", "test")) {
            assertFalse(validate(response(body = body)) { body { absentOrEmpty() } }.isValid)
            assertTrue(validate(response(body = body)) { body { nonEmpty() } }.isValid)
        }
        assertEquals(
            Discrepancy("body", "non-empty", "<missing>"),
            validate(response()) { body { nonEmpty() } }.discrepancies.single(),
        )
        assertEquals(
            Discrepancy("body", "non-empty", "\"\""),
            validate(response(body = "")) { body { nonEmpty() } }.discrepancies.single(),
        )
        assertEquals(
            Discrepancy("body", "absent or empty", "\" \""),
            validate(response(body = " ")) { body { absentOrEmpty() } }.discrepancies.single(),
        )
    }

    @Test
    fun missingEmptyAndEmptyStringHeadersHaveDifferentActuals() {
        fun validateHeader(headers: Map<String, List<String>>) =
            validate(response(headers = headers)) { header("X-Token") { equalTo("expected") } }
        assertEquals("<missing>", validateHeader(emptyMap()).discrepancies.single().actual)
        assertEquals("[]", validateHeader(mapOf("x-token" to emptyList())).discrepancies.single().actual)
        assertEquals("[\"\"]", validateHeader(mapOf("X-Token" to listOf(""))).discrepancies.single().actual)
    }

    @Test
    fun headerNamesAreCaseInsensitiveAndValuesAreCaseSensitive() {
        val headers = linkedMapOf(
            "ACCEPT" to listOf("application/json"),
            "accept" to listOf("text/html", " application/json", "application/json,text/html"),
        )
        val passing = validate(response(headers = headers)) {
            header("Accept") {
                equalTo("application/json")
                equalTo("text/html")
                startsWith("application/")
            }
        }
        assertTrue(passing.isValid)
        val failing = validate(response(headers = headers)) {
            header("Accept") {
                equalTo("APPLICATION/JSON")
                equalTo("application/json,text/html")
                equalTo("application/json ")
                equalTo("text/html ")
            }
        }
        assertEquals(3, failing.discrepancies.size)
        assertEquals(
            "[application/json, text/html, \" application/json\", \"application/json,text/html\"]",
            failing.discrepancies.first().actual,
        )
    }

    @Test
    fun multipleHeaderValuesMaySatisfyDifferentOperations() {
        val values = mapOf("accept" to listOf("application/json", "text/html"))
        assertTrue(validate(response(headers = values)) {
            header("Accept") { equalTo("application/json"); equalTo("text/html") }
        }.isValid)
        assertEquals(2, validate(response(headers = mapOf("Accept" to listOf("application/json,text/html")))) {
            header("Accept") { equalTo("application/json"); equalTo("text/html") }
        }.discrepancies.size)
    }

    @Test
    fun predicateSupportsLiteralVariableAndFunctionReference() {
        val direct: (String) -> Boolean = { it.isNotBlank() }
        val expected = response(headers = mapOf("X-Trace" to listOf("  ", "trace")))
        val missing = response(headers = mapOf("X-Trace" to listOf(" ")))
        val variants: List<ResponseSpecBuilder.() -> Unit> = listOf(
            { header("X-Trace") { satisfies("non-blank value") { it.isNotBlank() } } },
            { header("X-Trace") { satisfies("non-blank value", direct) } },
            { header("X-Trace") { satisfies("non-blank value", ::nonBlankValue) } },
        )
        assertTrue(variants.all { validate(expected, it).isValid })
        val results = variants.map { validate(missing, it).discrepancies }
        assertTrue(results.all { it == results.first() })
        var calls = 0
        val custom = { value: String -> calls++; value.isNotEmpty() }
        val missingResult = validate(response()) {
            header("X-Trace") { satisfies("something", custom) }
        }
        val emptyResult = validate(response(headers = mapOf("X-Trace" to emptyList()))) {
            header("X-Trace") { satisfies("something", custom) }
        }
        assertFalse(missingResult.isValid)
        assertFalse(emptyResult.isValid)
        assertEquals(0, calls)
    }

    @Test
    fun presentJsonNullDiffersFromAbsentFieldAndTypeDifferences() {
        val rules: ResponseSpecBuilder.() -> Unit = {
            body {
                json {
                    field("id") { present(); isNull() }
                    field("name") { equalTo("Ivanov") }
                    field("number") { equalTo(42); equalTo(42L) }
                    field("active") { equalTo(true) }
                }
            }
        }
        assertTrue(validate(response(body = """{"id":null,"name":"Ivanov","number":42,"active":true,"extra":1}"""), rules).isValid)
        val invalid = validate(response(body = """{"name":"ivanov","number":"42","active":"true"}"""), rules)
        assertEquals(
            listOf(
                Discrepancy("body.json.id", "present", "<missing>"),
                Discrepancy("body.json.id", "null", "<missing>"),
                Discrepancy("body.json.name", "\"Ivanov\"", "\"ivanov\""),
                Discrepancy("body.json.number", "42", "\"42\""),
                Discrepancy("body.json.number", "42", "\"42\""),
                Discrepancy("body.json.active", "true", "\"true\""),
            ),
            invalid.discrepancies,
        )
        assertEquals(
            Discrepancy("body.json.id", "null", "42"),
            validate(response(body = """{"id":42}""")) { body { json { field("id") { isNull() } } } }
                .discrepancies.single(),
        )
    }

    @Test
    fun nullBodyOrInvalidJsonSkipsFieldsButNotOtherRules() {
        for (badBody in listOf<String?>(null, "broken", "[1]", "{bad}")) {
            val result = validate(response(status = 500, headers = emptyMap(), body = badBody)) {
                status { equalTo(200) }
                body { json { field("id") { present(); equalTo(42) } } }
                header("X-Trace") { equalTo("yes") }
            }
            assertEquals(listOf("status", "body.json", "headers.X-Trace"), result.discrepancies.map { it.path })
        }
        assertEquals(
            "<missing>",
            validate(response()) { body { json { field("id") { present() } } } }.discrepancies.single().actual,
        )
    }

    @Test
    fun nestedOrderAndDuplicateChecksAreRetained() {
        val result = validate(response(status = 500, body = "{}")) {
            status { equalTo(200); equalTo(200) }
            body {
                nonEmpty()
                json { field("id") { present(); equalTo(42); present() } }
            }
            status { equalTo(204) }
        }
        assertEquals(
            listOf("200", "200", "present", "42", "present", "204"),
            result.discrepancies.map { it.expected },
        )
        assertEquals(
            listOf("status", "status", "body.json.id", "body.json.id", "body.json.id", "status"),
            result.discrepancies.map { it.path },
        )
    }

    @Test
    fun repeatedValidateRunsAreIndependentAndDoNotMutateInput() {
        val headers = linkedMapOf("x-id" to listOf("ok"))
        val good = response(headers = headers, body = "{}")
        val bad = response(status = 500, headers = headers, body = "{}")
        val block: ResponseSpecBuilder.() -> Unit = {
            status { equalTo(200) }
            header("X-ID") { equalTo("ok") }
        }
        val first = validate(good, block)
        val second = validate(bad, block)
        val third = validate(good, block)
        assertTrue(first.isValid)
        assertEquals(listOf(Discrepancy("status", "200", "500")), second.discrepancies)
        assertTrue(third.isValid)
        assertEquals(mapOf("x-id" to listOf("ok")), good.headers)
        assertEquals("{}", good.body)
        assertEquals(listOf(Discrepancy("status", "200", "500")), second.discrepancies)
    }

    @Test
    fun statusRangeIsInclusiveAndExplicitReceiverLabelWorks() {
        val block: ResponseSpecBuilder.() -> Unit = { status { inRange(200..299) } }
        assertFalse(validate(response(199), block).isValid)
        assertTrue(validate(response(200), block).isValid)
        assertTrue(validate(response(299), block).isValid)
        assertFalse(validate(response(300), block).isValid)
        val labeled = validate(response(500)) {
            body {
                this@validate.status { equalTo(200) }
            }
        }
        assertEquals(listOf("status"), labeled.discrepancies.map { it.path })
    }
}

private fun nonBlankValue(value: String) = value.isNotBlank()
