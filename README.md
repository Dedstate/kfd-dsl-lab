# Свой DSL

Создайте DSL для проверки HTTP-ответов и перепишите через него шесть функций `check01`–`check06` из `lesson09.legacy`. Новые проверки должны возвращать список расхождений с ожиданиями.

Полное условие, требования к тестам и необязательное усложнение — в [docs/lesson09.md](docs/lesson09.md).

## Что уже есть в проекте

- `app/src/main/kotlin/lesson09/HttpResponse.kt` — модель ответа.
- `app/src/main/kotlin/lesson09/legacy/Checks.kt` — шесть исходных проверок.
- `app/src/main/kotlin/lesson09/support/json/Json.kt` — готовый парсер JSON. Писать свой парсер не нужно.
- `app/src/test/kotlin/lesson09/LegacySmokeTest.kt` — начальные тесты исходных проверок.
- `app/src/test/kotlin/lesson09/support/json/JsonParserTest.kt` — тесты готового парсера.

Реализацию DSL добавьте в пакет `lesson09.dsl`, свои тесты — в `app/src/test/kotlin/lesson09`. Предоставленные исходники и тесты оставьте без изменений.

## Запуск

Установите JDK 25 и укажите его в `JAVA_HOME`. В IntelliJ IDEA выберите этот же JDK для Gradle JVM. Gradle устанавливать отдельно не нужно: проект использует Wrapper. При первом запуске нужен доступ к интернету для загрузки Gradle и зависимостей.

В проекте используются Kotlin 2.4.10, Gradle 9.5.1 и JUnit 6.1.3. Запускайте команды из корня репозитория:

| Действие | Bash | PowerShell |
|---|---|---|
| Сборка и тесты | `bash ./gradlew build` | `.\gradlew.bat build` |
| Тесты | `bash ./gradlew :app:test` | `.\gradlew.bat :app:test` |

Начальные тесты должны проходить до начала работы. По мере реализации DSL добавляйте собственные тесты из условия задания.

Если продолжаете работу в своём проекте курса, перенесите каталоги `app/src/main/kotlin/lesson09`, `app/src/test/kotlin/lesson09` и файл `docs/lesson09.md`. В модуле должны быть подключены Kotlin/JVM, JUnit Jupiter и `kotlin-test-junit5`, а задача тестирования должна использовать `useJUnitPlatform()`; настройки можно взять из `app/build.gradle.kts` и `gradle/libs.versions.toml`.

## Сдача

Передайте преподавателю ссылку на свой репозиторий. Добавьте в README пример использования DSL, пример расхождений и объяснение решения.
