// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register<Exec>("ktlintCheck") {
    group = "verification"
    description = "Runs the pinned ktlint CLI in check mode."
    commandLine("bash", "scripts/ktlint.sh", "check")
}

tasks.register<Exec>("ktlintFormat") {
    group = "verification"
    description = "Runs the pinned ktlint CLI and applies auto-formatting."
    commandLine("bash", "scripts/ktlint.sh", "format")
}

val pythonExecutable = providers.gradleProperty("pythonExecutable").orElse(
    if (System.getProperty("os.name").startsWith("Windows")) "python" else "python3"
).get()

val checkArchitectureTests = tasks.register<Exec>("checkArchitectureTests") {
    group = "verification"
    description = "Tests the architecture checker, including KDoc-only references."
    commandLine(pythonExecutable, "-B", "-m", "unittest", "discover", "-s", "scripts/tests")
}

tasks.register<Exec>("checkArchitecture") {
    group = "verification"
    description = "Checks main Kotlin package boundaries and feature dependency cycles."
    dependsOn(checkArchitectureTests)
    commandLine(pythonExecutable, "-B", "scripts/check_architecture.py")
}
