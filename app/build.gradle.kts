plugins { alias(libs.plugins.kotlin.jvm) }
kotlin {
    jvmToolchain(25)
}
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
tasks.test {
    useJUnitPlatform()
    testLogging { events("failed", "skipped", "passed") }
}
