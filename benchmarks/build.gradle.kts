import io.gitlab.arturbosch.detekt.Detekt

plugins {
    kotlin("jvm")
    kotlin("plugin.allopen")
    alias(libs.plugins.jmh)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

dependencies {
    implementation(project(":library"))
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<Detekt>().configureEach {
    parallel = true
    config.from(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
    // The module compiles on a JDK 25 toolchain (for the JMH fork), but detekt's bundled compiler
    // only accepts jvmTarget up to 22. Pin the static-analysis target to an LTS detekt supports; it
    // does not affect the compiled/forked bytecode.
    jvmTarget = "21"
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

jmh {
    jmhVersion.set("1.37")
    warmupIterations.set(5)
    iterations.set(10)
    fork.set(2)
    profilers.set(listOf("gc"))
    resultFormat.set("JSON")
    resultsFile.set(layout.buildDirectory.file("results/jmh/results.json"))
    // The champeau plugin has no built-in CLI override for benchmark selection, so wire one:
    // `./gradlew :benchmarks:jmh -PjmhIncludes=ParseBenchmark` runs only matching benchmarks.
    (findProperty("jmhIncludes") as String?)?.let { includes.set(listOf(it)) }
}
