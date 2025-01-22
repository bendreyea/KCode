plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.7"
}

group = "org.editor"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    val mockkVersion = "1.13.16"
    testImplementation("io.mockk:mockk:${mockkVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.7")
}

tasks.test {
    useJUnitPlatform()
}

benchmark {
    configurations {
        named("main") {
            iterationTime = 5
            iterationTimeUnit = "sec"
        }
    }
    targets {
        register("main") {
            this as kotlinx.benchmark.gradle.JvmBenchmarkTarget
            jmhVersion = "1.21"
        }
    }
}