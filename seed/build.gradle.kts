import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    api(project(":domain"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val seedDir = rootProject.layout.projectDirectory.dir("app/src/main/assets/seed")

tasks.test {
    useJUnitPlatform()
    // Content tests validate the real seed bundled into the app.
    inputs.dir(seedDir).withPropertyName("seed")
    systemProperty("spajza.seedDir", seedDir.asFile.absolutePath)
}

tasks.register<JavaExec>("contentReport") {
    description = "Writes docs/CONTENT.md and docs/DATA_TODO.md from the bundled seed."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("sk.matej.spajza.data.seed.CoverageReport")
    args(seedDir.asFile.absolutePath, rootProject.layout.projectDirectory.dir("docs").asFile.absolutePath)
}
