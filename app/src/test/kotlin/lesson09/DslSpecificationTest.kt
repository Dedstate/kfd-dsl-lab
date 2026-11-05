package lesson09

import lesson09.dsl.Discrepancy
import lesson09.dsl.responseSpec
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DslSpecificationTest {
    @Test
    fun definitionBlockRunsOnceAndPredicateOnlyDuringValidation() {
        var configurations = 0
        var predicateCalls = 0
        val spec = responseSpec {
            configurations += 1
            status { equalTo(200) }
            header("X-Trace") {
                satisfies("non-blank") { value ->
                    predicateCalls += 1
                    value.isNotBlank()
                }
            }
        }
        assertEquals(1, configurations)
        assertEquals(0, predicateCalls)

        val valid = HttpResponse(200, mapOf("X-Trace" to listOf("ok")), null)
        val invalid = HttpResponse(500, emptyMap(), null)
        val first = spec.validate(valid)
        val second = spec.validate(invalid)
        val third = spec.validate(valid)
        assertEquals(1, configurations)
        assertEquals(2, predicateCalls)
        assertTrue(first.isValid)
        assertEquals(
            listOf(
                Discrepancy("status", "200", "500"),
                Discrepancy("headers.X-Trace", "non-blank", "<missing>"),
            ),
            second.discrepancies,
        )
        assertTrue(third.isValid)
        assertEquals(2, second.discrepancies.size)
    }

    @Test
    fun sharedRulesCanBeIncludedInDifferentSpecificationsWithoutLeakingState() {
        val common = responseSpec { status { equalTo(200) } }
        val first = responseSpec {
            include(common)
            header("X-Key") { equalTo("yes") }
        }
        val second = responseSpec {
            body { nonEmpty() }
            include(common)
        }
        val invalid = HttpResponse(500, emptyMap(), null)
        assertEquals(listOf("status", "headers.X-Key"), first.validate(invalid).discrepancies.map { it.path })
        assertEquals(listOf("body", "status"), second.validate(invalid).discrepancies.map { it.path })
        assertEquals(listOf("status"), common.validate(invalid).discrepancies.map { it.path })
        assertTrue(first.validate(HttpResponse(200, mapOf("X-Key" to listOf("yes")), "ok")).isValid)
        assertFalse(second.validate(invalid).isValid)
    }

    @Test
    fun inclusionUsesDeclarationOrderAndPreservesDuplicates() {
        val common = responseSpec { status { equalTo(200) } }
        val spec = responseSpec {
            header("X-Token") { equalTo("yes") }
            include(common)
            body { nonEmpty() }
            include(common)
        }
        val invalid = HttpResponse(500, emptyMap(), null)
        val result = spec.validate(invalid)
        assertEquals(
            listOf("headers.X-Token", "status", "body", "status"),
            result.discrepancies.map { it.path },
        )
        assertEquals(
            listOf("value yes", "200", "non-empty", "200"),
            result.discrepancies.map { it.expected },
        )
    }
}
