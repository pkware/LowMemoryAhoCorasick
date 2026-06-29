plugins {
    kotlin("jvm")
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.pkware.ahocorasick"
val lowMemoryAhoCorasickVersion: String by project
version = lowMemoryAhoCorasickVersion

dependencies {
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    explicitApi()
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}

detekt {
    parallel = true
    config.setFrom(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
}
