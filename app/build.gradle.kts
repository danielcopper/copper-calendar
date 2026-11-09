plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "dev.cupr.calendar"
    compileSdk = 37

    defaultConfig {
        // Provisional until the first upload to Play (D13).
        applicationId = "dev.cupr.calendar"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric with native graphics needs more than the default
                // 512 MB test heap.
                test.maxHeapSize = "1536m"
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

ktlint {
    version.set(
        libs.versions.ktlint.cli
            .get(),
    )
}

detekt {
    buildUponDefaultConfig = true
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidsvg)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
