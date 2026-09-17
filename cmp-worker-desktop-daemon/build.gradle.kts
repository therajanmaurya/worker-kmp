import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    application
}

group = "io.github.mobilebytelabs"
version = providers.gradleProperty("worker.version").get()

// Desktop true-background daemon — JVM-only executable JAR (Phase 8 of worker-kmp v3.0.0 epic).
//
// The daemon is invoked by the host OS scheduler (Windows Task Scheduler / macOS launchd /
// Linux systemd-user timer / cron) to process pending work when the consumer app is not
// running. alpha05 ships the scaffold only (logs + exits); per-OS installer impls and full
// work execution land in v3.0.0-alpha05.X follow-ups.
//
// NOTE: this module uses `kotlin("jvm")` rather than the multiplatform plugin because
// (a) the daemon entry point is a `main(args)` function that only makes sense on the JVM,
// (b) `application` plugin auto-applies `java` which is incompatible with the multiplatform
// plugin. Sibling modules use the multiplatform plugin; this is a second JVM-only exception
// alongside `cmp-worker-bench`.
//
// NOTE: This module produces an executable JAR (the daemon) that the host OS scheduler
// invokes. Full shadowJar fat-JAR packaging lands in alpha05.X follow-up — for alpha05
// we ship the source + tasks scaffold.

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    api(project(":cmp-worker-kmp"))
    implementation(project(":cmp-worker-desktop"))
    implementation(libs.kermit)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

application {
    mainClass.set("io.github.mobilebytelabs.worker.daemon.DesktopBackgroundDaemonKt")
}

// Compose Multiplatform 1.12.0 redirects `org.jetbrains.compose.runtime:*` to the matching
// `androidx.compose.runtime:*` artifacts, and BOTH groups publish the same
// `<artifactId>-<version>.jar` FILENAME. The `application` plugin flattens runtimeClasspath
// into the distribution's `lib/`, so the two collide and distTar/distZip fail with
// "Entry ... is a duplicate but no duplicate handling strategy has been set".
//
// A blanket duplicatesStrategy is NOT safe here: it resolves by classpath order, and the
// order differs per artifact (measured 2026-09-16 on this configuration —
// androidx first for runtime-saveable-desktop, JetBrains first for runtime-desktop), so
// EXCLUDE would keep the 4,865-byte JetBrains shim and drop the real 1,948,637-byte
// androidx runtime, producing a distribution that fails at runtime, not at build time.
//
// The JetBrains artifacts are pure redirect POMs — their jars carry ZERO classes (measured:
// 0 vs 742 for runtime-desktop, 0 vs 22 for runtime-saveable-desktop) — so dropping them
// from the archive loses nothing. Filter by the cache path's group directory, which is an
// exact discriminator. Dependency RESOLUTION is untouched on purpose: androidx is reached
// THROUGH these shims, so a configuration-level `exclude` would remove the real jars too.
tasks.withType<AbstractCopyTask>().configureEach {
    eachFile {
        if ("/org.jetbrains.compose.runtime/" in file.invariantSeparatorsPath &&
            name.endsWith(".jar")
        ) {
            exclude()
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
