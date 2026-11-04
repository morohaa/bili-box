plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.bilibox"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.bilibox"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.zxing.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Retrofit guard: allow the existing architecture but prevent new 1000+ line files.
val kotlinFileLineLimit = 1000
val checkKotlinFileSize = tasks.register("checkKotlinFileSize") {
    group = "verification"
    description = "Fails when a main Kotlin source file exceeds $kotlinFileLineLimit lines."
    doLast {
        val offenders = fileTree("src/main/java") { include("**/*.kt") }.files
            .map { it to it.readLines().size }
            .filter { (_, lines) -> lines > kotlinFileLineLimit }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Kotlin files exceed $kotlinFileLineLimit lines:\n" +
                    offenders.joinToString("\n") { (file, lines) ->
                        " - ${file.relativeTo(projectDir)}: $lines"
                    }
            )
        }
    }
}
tasks.named("check") { dependsOn(checkKotlinFileSize) }
