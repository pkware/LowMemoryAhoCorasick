plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("plugin.allopen") version "2.4.10" apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.jmh) apply false
}
