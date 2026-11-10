import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

/** Passes the render test's output directory as a system property, as a declared output of the test task. */
class RenderDir(
    @get:OutputDirectory val dir: DirectoryProperty,
) : CommandLineArgumentProvider {
    override fun asArguments() = listOf("-Drender.dir=${dir.get().asFile.absolutePath}")
}

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
                // Robolectric sets up API 37's shared memory through JDK internals.
                test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                // Where the render test writes its PNGs (`mise run render`): an
                // output of the task, so a result from the build cache brings them
                // too, and keyed by its place in the project, not its absolute path.
                test.jvmArgumentProviders.add(
                    RenderDir(objects.directoryProperty().value(layout.buildDirectory.dir("render"))),
                )
                // Coverage of the classes Robolectric loads into its sandbox.
                test.extensions.configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
    }
}

// The unit and render tests' coverage, written by `createDebugUnitTestCoverageReport`. Set on this
// module with an absolute path: the scanner resolves a relative one against the module's directory.
extensions.configure<org.sonarqube.gradle.SonarExtension>("sonar") {
    properties {
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory
                .file("reports/coverage/test/debug/report.xml")
                .get()
                .asFile.absolutePath,
        )
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
