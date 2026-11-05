# HTTP Response Validation DSL

A Kotlin DSL for checking HTTP responses in memory. The implementation reports every failed operation rather than returning a single Boolean. The starter response model, legacy checks, JSON parser, and supplied tests are unchanged.

## Requirements and tests

Install **JDK 25** and set `JAVA_HOME` to that installation. The project uses Kotlin 2.4.10 and Gradle Wrapper 9.5.1. Run from the repository root:

```bash
bash ./gradlew clean build
bash ./gradlew :app:test
```

## Example

```kotlin
import lesson09.HttpResponse
import lesson09.dsl.validate

val response = HttpResponse(
    status = 500,
    headers = mapOf("content-type" to listOf("text/plain")),
    body = "{}",
)

val result = validate(response) {
    status { equalTo(200) }
    header("Content-Type") { startsWith("application/json") }
    body {
        json {
            field("id") { present() }
        }
    }
}

println(result.isValid) // false
println(result.report())
```

Output:

```text
status: expected 200, actual 500
headers.Content-Type: expected prefix application/json, actual [text/plain]
body.json.id: expected present, actual <missing>
```

Each `Discrepancy` stores `path`, `expected`, and `actual` separately. `ValidationResult.discrepancies` is empty on success. Each DSL operation produces at most one discrepancy, and duplicates are retained. Failures are reported in DSL declaration order, including the operations of nested blocks at their position in the outer block.

## Architecture

`lesson09.dsl.ResponseDsl.kt` defines receiver types `ResponseSpecBuilder`, `StatusRulesBuilder`, `HeaderRulesBuilder`, `BodyRulesBuilder`, `JsonRulesBuilder` and `JsonFieldRulesBuilder`. All carry the same `@ResponseDslMarker` to prevent accidentally calling outer-scope operations from nested blocks. The response receiver supports `status`, `header`, `body`, and `include`. Status supports `equalTo` and `inRange`; headers support `equalTo`, `startsWith`, and `satisfies`; body supports `absentOrEmpty`, `nonEmpty`, and `json`; JSON supports `field`; fields support `present`, typed `equalTo`, and `isNull`.

Header names match case-insensitively. Values from matching keys are concatenated **in map iteration order**, preserving order within each list, without trimming or splitting comma-separated strings. Checks succeed if any value matches; separate checks may match different values. Actual header values are displayed in brackets; `<missing>` means no matching key, `[]` means a matching key with an empty list, and `[""]` means a list containing an empty string. Strings containing commas or surrounding spaces are quoted when displayed.

`null` and `""` count as absent/empty body, while `" "` is nonempty. A `json` operation uses the provided `parseJsonObject` parser, irrespective of Content-Type. Missing body and invalid JSON each produce a single `body.json` discrepancy for that operation; field operations are skipped in that case. Field presence uses `containsKey`, so a present JSON `null` is not confused with a missing field. String, Long, and Boolean comparisons preserve types (an Int expectation is converted to Long to match the parser). Values are formatted with quotes for strings and without quotes for numbers, booleans, and JSON `null`.

`HeaderRulesBuilder.satisfies(description: String, predicate: (String) -> Boolean)` stores a user-provided predicate. When validating, it evaluates values until the first match; it does not call the predicate for a missing header or empty list. The predicate can be supplied as a lambda literal, a function-typed variable, or a function reference.

## Legacy compatibility

`lesson09.dsl.LegacyDslChecks.kt` contains `check01Dsl` through `check06Dsl`, corresponding exactly to `lesson09.legacy.check01` through `check06`. A suitable or unsuitable response has the same success result under each pair. Unlike the original short-circuiting Boolean checks, the DSL reports all independent violations. In particular `check02Dsl` checks only body nonemptiness, **not** whether its content is valid JSON.

## Reusable specifications (optional extension)

```kotlin
val common = responseSpec {
    status { equalTo(200) }
}
val user = responseSpec {
    include(common)
    body {
        json {
            field("id") { present() }
        }
    }
}
val a = user.validate(HttpResponse(200, emptyMap(), "{\"id\":null}"))
val b = user.validate(HttpResponse(503, emptyMap(), "{}"))
```

`responseSpec(block: ResponseSpecBuilder.() -> Unit)` executes its receiver lambda **once**, building an immutable ordered list of validation rules `(HttpResponse, MutableList<Discrepancy>) -> Unit`. Sub-builder lambdas (e.g. `StatusRulesBuilder.() -> Unit`) also execute during specification creation and store rules for their level. User predicates `(String) -> Boolean` and the actual value checks execute **only** during `ResponseSpecification.validate(response)`. The specification does not hold an HTTP response, result, or accumulation buffer. Each validation allocates a fresh mutable buffer and returns a separate result with a snapshot of discrepancies. `include` appends the included specification's rules at the declaration position; repeated inclusions repeat checks, and the same common rules can be included in multiple specifications. The implementation does not require `inline`, `noinline`, or `crossinline`.

## Source and tests

- `app/src/main/kotlin/lesson09/dsl/ResponseDsl.kt` — DSL, results, rules, and reusable specifications.
- `app/src/main/kotlin/lesson09/dsl/LegacyDslChecks.kt` — six DSL equivalents.
- `app/src/test/kotlin/lesson09/DslValidationTest.kt` — mandatory edge cases, error ordering, predicates, and DSL receiver behavior.
- `app/src/test/kotlin/lesson09/DslLegacyParityTest.kt` — success and independently violated conditions for all six legacy checks.
- `app/src/test/kotlin/lesson09/DslSpecificationTest.kt` — optional specification creation, re-use, inclusion, and independence.
