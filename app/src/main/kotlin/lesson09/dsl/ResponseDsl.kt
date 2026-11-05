package lesson09.dsl

import lesson09.HttpResponse
import lesson09.support.json.JsonParseResult
import lesson09.support.json.parseJsonObject

@DslMarker
annotation class ResponseDslMarker

data class Discrepancy(
    val path: String,
    val expected: String,
    val actual: String,
) {
    override fun toString(): String = "$path: expected $expected, actual $actual"
}

data class ValidationResult(val discrepancies: List<Discrepancy>) {
    val isValid: Boolean get() = discrepancies.isEmpty()
    fun report(): String = discrepancies.joinToString("\n")
}

internal typealias Sink = MutableList<Discrepancy>
internal typealias ResponseRule = (HttpResponse, Sink) -> Unit
internal typealias StatusRule = (Int, Sink) -> Unit
internal typealias HeaderRule = (List<String>?, Sink) -> Unit
internal typealias BodyRule = (String?, Sink) -> Unit
internal typealias JsonRule = (Map<String, Any?>, Sink) -> Unit
internal typealias FieldRule = (Boolean, Any?, Sink) -> Unit

private fun mismatch(sink: Sink, path: String, expected: String, actual: String) {
    sink.add(Discrepancy(path, expected, actual))
}

private fun quoted(value: String): String = buildString {
    append('"')
    for (character in value) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

private fun actualValue(value: Any?): String = when (value) {
    null -> "null"
    is String -> quoted(value)
    else -> value.toString()
}

private fun actualBody(value: String?): String = value?.let(::quoted) ?: "<missing>"

private fun actualHeader(values: List<String>?): String = when {
    values == null -> "<missing>"
    values.isEmpty() -> "[]"
    else -> values.joinToString(prefix = "[", postfix = "]") { value ->
        if (value.isEmpty() || value.contains(',') || value.startsWith(' ') || value.endsWith(' ')) {
            quoted(value)
        } else {
            value
        }
    }
}

class ResponseSpecification internal constructor(private val rules: List<ResponseRule>) {
    fun validate(response: HttpResponse): ValidationResult {
        val discrepancies = mutableListOf<Discrepancy>()
        rules.forEach { rule -> rule(response, discrepancies) }
        return ValidationResult(discrepancies.toList())
    }

    internal fun rulesForInclusion(): List<ResponseRule> = rules
}

@ResponseDslMarker
class ResponseSpecBuilder internal constructor() {
    private val rules = mutableListOf<ResponseRule>()

    fun status(block: StatusRulesBuilder.() -> Unit) {
        val operations = StatusRulesBuilder().apply(block).build()
        rules.add { response, sink ->
            operations.forEach { it(response.status, sink) }
        }
    }

    fun header(name: String, block: HeaderRulesBuilder.() -> Unit) {
        val operations = HeaderRulesBuilder(name).apply(block).build()
        rules.add { response, sink ->
            // Preserve map iteration order and value order across case-insensitive keys.
            var present = false
            val values = mutableListOf<String>()
            for ((key, entries) in response.headers) {
                if (key.equals(name, ignoreCase = true)) {
                    present = true
                    values.addAll(entries)
                }
            }
            val found: List<String>? = if (present) values else null
            operations.forEach { it(found, sink) }
        }
    }

    fun body(block: BodyRulesBuilder.() -> Unit) {
        val operations = BodyRulesBuilder().apply(block).build()
        rules.add { response, sink ->
            operations.forEach { it(response.body, sink) }
        }
    }

    fun include(specification: ResponseSpecification) {
        rules.addAll(specification.rulesForInclusion())
    }

    internal fun build(): ResponseSpecification = ResponseSpecification(rules.toList())
}

@ResponseDslMarker
class StatusRulesBuilder internal constructor() {
    private val rules = mutableListOf<StatusRule>()

    fun equalTo(expected: Int) {
        rules.add { actual, sink ->
            if (actual != expected) mismatch(sink, "status", expected.toString(), actual.toString())
        }
    }

    fun inRange(range: IntRange) {
        rules.add { actual, sink ->
            if (actual !in range) mismatch(sink, "status", "in ${range.first}..${range.last}", actual.toString())
        }
    }

    internal fun build(): List<StatusRule> = rules.toList()
}

@ResponseDslMarker
class HeaderRulesBuilder internal constructor(private val name: String) {
    private val rules = mutableListOf<HeaderRule>()
    private val path get() = "headers.$name"

    fun equalTo(expected: String) {
        rules.add { values, sink ->
            if (values == null || values.none { it == expected }) {
                mismatch(sink, path, "value $expected", actualHeader(values))
            }
        }
    }

    fun startsWith(prefix: String) {
        rules.add { values, sink ->
            if (values == null || values.none { it.startsWith(prefix) }) {
                mismatch(sink, path, "prefix $prefix", actualHeader(values))
            }
        }
    }

    fun satisfies(description: String, predicate: (String) -> Boolean) {
        rules.add { values, sink ->
            if (values == null || values.none(predicate)) {
                mismatch(sink, path, description, actualHeader(values))
            }
        }
    }

    internal fun build(): List<HeaderRule> = rules.toList()
}

@ResponseDslMarker
class BodyRulesBuilder internal constructor() {
    private val rules = mutableListOf<BodyRule>()

    fun absentOrEmpty() {
        rules.add { actual, sink ->
            if (actual != null && actual.isNotEmpty()) {
                mismatch(sink, "body", "absent or empty", actualBody(actual))
            }
        }
    }

    fun nonEmpty() {
        rules.add { actual, sink ->
            if (actual == null || actual.isEmpty()) {
                mismatch(sink, "body", "non-empty", actualBody(actual))
            }
        }
    }

    fun json(block: JsonRulesBuilder.() -> Unit) {
        val operations = JsonRulesBuilder().apply(block).build()
        rules.add { actual, sink ->
            when {
                actual == null -> mismatch(sink, "body.json", "valid JSON object", "<missing>")
                else -> when (val parsed = parseJsonObject(actual)) {
                    is JsonParseResult.Success -> operations.forEach { it(parsed.fields, sink) }
                    is JsonParseResult.Failure -> mismatch(
                        sink, "body.json", "valid JSON object", "parse failure: ${parsed.reason}"
                    )
                }
            }
        }
    }

    internal fun build(): List<BodyRule> = rules.toList()
}

@ResponseDslMarker
class JsonRulesBuilder internal constructor() {
    private val rules = mutableListOf<JsonRule>()

    fun field(name: String, block: JsonFieldRulesBuilder.() -> Unit) {
        val operations = JsonFieldRulesBuilder(name).apply(block).build()
        rules.add { fields, sink ->
            val present = fields.containsKey(name)
            val value = fields[name]
            operations.forEach { it(present, value, sink) }
        }
    }

    internal fun build(): List<JsonRule> = rules.toList()
}

@ResponseDslMarker
class JsonFieldRulesBuilder internal constructor(private val name: String) {
    private val rules = mutableListOf<FieldRule>()
    private val path get() = "body.json.$name"

    fun present() {
        rules.add { present, _, sink ->
            if (!present) mismatch(sink, path, "present", "<missing>")
        }
    }

    fun equalTo(expected: String) = equality(expected)
    fun equalTo(expected: Int) = equality(expected.toLong())
    fun equalTo(expected: Long) = equality(expected)
    fun equalTo(expected: Boolean) = equality(expected)

    fun isNull() {
        rules.add { present, actual, sink ->
            if (!present || actual != null) {
                mismatch(sink, path, "null", if (present) actualValue(actual) else "<missing>")
            }
        }
    }

    private fun equality(expected: Any) {
        rules.add { present, actual, sink ->
            if (!present || actual != expected) {
                mismatch(
                    sink,
                    path,
                    actualValue(expected),
                    if (present) actualValue(actual) else "<missing>",
                )
            }
        }
    }

    internal fun build(): List<FieldRule> = rules.toList()
}

fun responseSpec(block: ResponseSpecBuilder.() -> Unit): ResponseSpecification =
    ResponseSpecBuilder().apply(block).build()

fun validate(response: HttpResponse, block: ResponseSpecBuilder.() -> Unit): ValidationResult =
    responseSpec(block).validate(response)
