buildscript {
    // AGP's built-in Kotlin support needs AGP and the Kotlin Gradle plugin in one class loader, so AGP
    // goes on the root classpath next to the Kotlin plugins below. Environments without Google Maven
    // (spajza.jvmOnly, see settings.gradle.kts) build only the JVM modules and leave it out.
    if (findProperty("spajza.jvmOnly") != "true") {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath(libs.android.gradle)
        }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
