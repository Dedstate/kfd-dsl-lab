package lesson09.support.json

sealed interface JsonParseResult {
    data class Success(val fields: Map<String, Any?>) : JsonParseResult
    data class Failure(val reason: String) : JsonParseResult
}

private val tokenPattern = Regex(
    """[ \t\r\n]+|"(?:[^"\\\x00-\x1F]|\\["\\])*+"|-?(?:0|[1-9][0-9]*)|true|false|null|[{}:,]"""
)

/** Parses a flat JSON object. The last value wins when a key occurs more than once. */
fun parseJsonObject(source: String): JsonParseResult {
    val tokens = mutableListOf<String>()
    var offset = 0
    while (offset < source.length) {
        val match = tokenPattern.matchAt(source, offset)
            ?: return JsonParseResult.Failure("Invalid token at offset $offset")
        val text = match.value
        if (text[0] !in " \t\r\n") tokens.add(text)
        offset = match.range.last + 1
    }

    var index = 0
    fun take(expected: String): Boolean {
        if (tokens.getOrNull(index) != expected) return false
        index += 1
        return true
    }

    fun decoded(value: String): String {
        val result = StringBuilder()
        var position = 1
        while (position < value.lastIndex) {
            if (value[position] == '\\') position += 1
            result.append(value[position++])
        }
        return result.toString()
    }

    if (!take("{")) return JsonParseResult.Failure("Expected an object")
    val fields = linkedMapOf<String, Any?>()

    fun finish(): JsonParseResult =
        if (index == tokens.size) JsonParseResult.Success(fields.toMap())
        else JsonParseResult.Failure("Unexpected content after object")

    if (take("}")) return finish()
    while (true) {
        val key = tokens.getOrNull(index++)
            ?: return JsonParseResult.Failure("Expected a field name")
        if (!key.startsWith('"')) {
            return JsonParseResult.Failure("Expected a quoted field name")
        }
        if (!take(":")) return JsonParseResult.Failure("Expected ':' after field name")

        val value = tokens.getOrNull(index++)
            ?: return JsonParseResult.Failure("Expected a field value")
        fields[decoded(key)] = when {
            value.startsWith('"') -> decoded(value)
            value == "true" -> true
            value == "false" -> false
            value == "null" -> null
            else -> value.toLongOrNull()
                ?: return JsonParseResult.Failure("Expected a value supported by the parser")
        }

        if (take("}")) return finish()
        if (!take(",")) return JsonParseResult.Failure("Expected ',' or '}' after field value")
    }
}
