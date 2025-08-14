import io.gitlab.arturbosch.detekt.Detekt

plugins {
    kotlin("jvm") version "2.2.10"
    `maven-publish`
    signing
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "com.pkware.ahocorasick"
val lowMemoryAhoCorasickVersion: String by project
version = lowMemoryAhoCorasickVersion

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.truth)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    explicitApi()
    jvmToolchain { languageVersion.set(JavaLanguageVersion.of(8)) }
}

tasks.withType<Detekt>().configureEach {
    parallel = true
    config.from(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
}

// <editor-fold desc="Publishing and Signing">
java {
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = pomArtifactId
            from(components["java"])
            pom {
                name.set(pomName)
                packaging = pomPackaging
                description.set(pomDescription)
                url.set("https://github.com/pkware/LowMemoryAhoCorasick")
                setPkwareOrganization()

                developers {
                    developer {
                        id.set("all")
                        name.set("PKWARE, Inc.")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/pkware/LowMemoryAhoCorasick.git")
                    developerConnection.set("scm:git:ssh://github.com/pkware/LowMemoryAhoCorasick.git")
                    url.set("https://github.com/pkware/LowMemoryAhoCorasick")
                }

                licenses {
                    license {
                        name.set("MIT License")
                        distribution.set("repo")
                        url.set("https://github.com/pkware/LowMemoryAhoCorasick/blob/main/LICENSE")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            url = uri(if (version.toString().isReleaseBuild) releaseRepositoryUrl else snapshotRepositoryUrl)
            credentials {
                username = repositoryUsername
                password = repositoryPassword
            }
        }
    }
}

signing {
    // Signing credentials are stored locally in the user's global gradle.properties file.
    // See https://docs.gradle.org/current/userguide/signing_plugin.html#sec:signatory_credentials for more information.
    sign(publishing.publications["mavenJava"])
}

val String.isReleaseBuild
    get() = !contains("SNAPSHOT")

val Project.releaseRepositoryUrl: String
    get() = properties.getOrDefault(
        "RELEASE_REPOSITORY_URL",
        "https://oss.sonatype.org/service/local/staging/deploy/maven2",
    ).toString()

val Project.snapshotRepositoryUrl: String
    get() = properties.getOrDefault(
        "SNAPSHOT_REPOSITORY_URL",
        "https://oss.sonatype.org/content/repositories/snapshots",
    ).toString()

val Project.repositoryUsername: String
    get() = properties.getOrDefault("NEXUS_USERNAME", "").toString()

val Project.repositoryPassword: String
    get() = properties.getOrDefault("NEXUS_PASSWORD", "").toString()

val Project.pomPackaging: String
    get() = properties.getOrDefault("POM_PACKAGING", "jar").toString()

val Project.pomName: String?
    get() = properties["POM_NAME"]?.toString()

val Project.pomDescription: String?
    get() = properties["POM_DESCRIPTION"]?.toString()

val Project.pomArtifactId
    get() = properties.getOrDefault("POM_ARTIFACT_ID", name).toString()

fun MavenPom.setPkwareOrganization() {
    organization {
        name.set("PKWARE, Inc.")
        url.set("https://www.pkware.com")
    }
}
// </editor-fold>
