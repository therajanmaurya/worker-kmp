// Minimal reference worker-kmp Web Push server (Ktor variant).
//
// Added in worker-kmp v3.0.0-alpha06.X (Phase 9 alpha06.X). Standalone — NOT included
// in the root `settings.gradle.kts`. To run from this directory:
//
//   ./gradlew run
//
// Set the VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY env vars before launching.

plugins {
    kotlin("jvm") version "2.4.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    val ktorVersion = "3.6.0"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("nl.martijndwars:web-push:5.1.2")
    // web-push 5.1.2 demoted httpasyncclient to runtime scope, so org.apache.http.HttpResponse
    // (the type PushService.send returns) is no longer on the compile classpath. Declare it.
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.jetbrains.exposed:exposed-core:1.5.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.5.0")
    implementation("ch.qos.logback:logback-classic:1.6.3")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
}

application {
    mainClass.set("MainKt")
}

kotlin {
    jvmToolchain(17)
}
