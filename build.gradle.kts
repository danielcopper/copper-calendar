buildscript {
    dependencies {
        // AGP's built-in Kotlin brings its own Kotlin Gradle plugin (2.2.10 for
        // AGP 9.5); this pins the one in the version catalog instead.
        classpath(libs.kotlin.gradle.plugin)
    }
    // The plugins below land on this classpath too; its versions are locked in
    // buildscript-gradle.lockfile (`mise run deps-refresh` writes it).
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property("sonar.projectKey", "danielcopper_copper-calendar")
        property("sonar.organization", "danielcopper")
        property("sonar.host.url", "https://sonarcloud.io")
        // The scanner polls the quality gate and fails when it fails, so the CI
        // job that runs the scan fails with it.
        property("sonar.qualitygate.wait", "true")
    }
}
