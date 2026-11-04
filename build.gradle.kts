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
