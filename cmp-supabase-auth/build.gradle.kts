import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    id("io.github.mobilebytelabs.supabaseauth.dokka")
    id("io.github.mobilebytelabs.supabaseauth.kover")
    alias(libs.plugins.binaryCompatibilityValidator)
}

group = "io.github.mobilebytelabs"
version = providers.gradleProperty("supabaseauth.version").get()

// ─────────────────────────────────────────────────────────────────────────────────────────────
// TARGETS — 17. MEASURED against Maven Central on 2026-09-26, never inferred.
//
// The ceiling is `auth-kt` 3.8.0, which publishes 17 of the 21 targets this toolkit family
// considers reachable. Missing, and therefore not declared here:
//     linuxArm64 · watchosArm32 · watchosDeviceArm64 · wasmWasi
//
// koin-core publishes every remaining target, so it adds no constraint.
//
// Store5 was REMOVED (it capped this module at 8): `auth-kt` already owns session lifecycle,
// persistence and refresh, so a second cache layer would have been a second owner of the same
// state — see AuthSessionStore's KDoc.
//
// Re-measure before widening:
//   curl -o /dev/null -w '%{http_code}' \
//     https://repo1.maven.org/maven2/io/github/jan-tennert/supabase/auth-kt-<target>/3.8.0/...pom
// ─────────────────────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)
kotlin {
    applyDefaultHierarchyTemplate()

    jvm()

    androidLibrary {
        namespace = "io.github.mobilebytelabs.supabaseauth"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        withJava()
        withHostTestBuilder {}.configure {
            // android.jar in a JVM host test is a stub whose methods THROW by default, so a
            // framework call aborts a test even when the code under test handled it correctly.
            isReturnDefaultValues = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    // Apple — every target auth-kt publishes. Unlike cmp-supabase-auth-compose (capped at 6 by
    // Compose + compose-auth), nothing here carries Compose, so the full Apple set is reachable.
    //
    // The linker options are NOT optional. supabase-kt pulls
    // `dev.whyoleg.cryptography:cryptography-provider-cryptokit`, whose CryptoKit interop is
    // written in Swift; linking it needs the Swift compatibility shims, which the Kotlin/Native
    // linker does not add on its own. Without them every *test* binary fails with
    // `Undefined symbols: __swift_FORCE_LOAD_$_swiftCompatibility56`.
    //
    // HOST-GATED: `xcrun` exists only on macOS and resolves at CONFIGURATION time, so an ungated
    // call aborts the build on every Linux runner — including jobs that never touch Apple.
    val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    val appleTargets =
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64(),
            macosX64(),
            macosArm64(),
            tvosX64(),
            tvosArm64(),
            tvosSimulatorArm64(),
            watchosX64(),
            watchosArm64(),
            watchosSimulatorArm64(),
        )
    if (isMacOs) {
        appleTargets.forEach { target ->
            val sdk = appleSdkFor(target.konanTarget.name)
            target.binaries.all { linkerOpts("-L${swiftLibraryPath(sdk)}") }
        }
    }

    // linuxX64 only — auth-kt publishes no linuxArm64.
    linuxX64()
    mingwX64()

    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        nodejs()
    }

    wasmJs {
        browser()
        nodejs()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        // Every non-Android target shares one no-op `registerAuthCallbackClient` actual. Android
        // is the only platform needing a real OAuth-redirect receiver; declaring the rest one by
        // one would be eleven identical files.
        val noCallbackMain = create("noCallbackMain").apply { dependsOn(getByName("commonMain")) }
        listOf("jvmMain", "appleMain", "linuxMain", "mingwMain", "jsMain", "wasmJsMain")
            .forEach { getByName(it).dependsOn(noCallbackMain) }

        commonMain.dependencies {
            // `api`, not `implementation`: SupabaseAuthClient exposes a ComposeAuth handle and
            // SessionStatus, so consumers must see those types. That leak is deliberate — native
            // Google/Apple sign-in are composition-scoped Compose APIs that cannot be wrapped
            // without reimplementing Credential Manager and ASAuthorization by hand.
            api(libs.supabase.auth)

            // The DI surface consumers wire with one line.
            api(libs.koin.core)

            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.koin.test)
        }
    }
}

/**
 * Absolute path to the active toolchain's Swift static libraries for [sdk].
 *
 * `xcrun --find swiftc` locates the toolchain the machine is actually using, so this keeps
 * working across Xcode versions and non-default install locations instead of pinning one path.
 */
fun appleSdkFor(konanTargetName: String): String =
    when {
        konanTargetName.startsWith("ios") -> {
            if (konanTargetName == "ios_arm64") "iphoneos" else "iphonesimulator"
        }

        konanTargetName.startsWith("macos") -> {
            "macosx"
        }

        konanTargetName.startsWith("tvos") -> {
            if (konanTargetName == "tvos_arm64") "appletvos" else "appletvsimulator"
        }

        konanTargetName.startsWith("watchos") -> {
            if (konanTargetName.endsWith("arm64") &&
                !konanTargetName.contains("simulator")
            ) {
                "watchos"
            } else {
                "watchsimulator"
            }
        }

        else -> {
            error("No Apple SDK mapping for konan target '$konanTargetName'")
        }
    }

fun swiftLibraryPath(sdk: String): String {
    val swiftc =
        providers
            .exec {
                commandLine("xcrun", "--find", "swiftc")
            }.standardOutput.asText
            .get()
            .trim()
    // …/usr/bin/swiftc  ->  …/usr/lib/swift/<sdk>
    val toolchainUsr = File(swiftc).parentFile.parentFile
    return File(toolchainUsr, "lib/swift/$sdk").absolutePath
}

mavenPublishing {
    configure(
        KotlinMultiplatform(
            javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"),
            sourcesJar = true,
        ),
    )
    publishToMavenCentral()
    signAllPublications()

    coordinates(group.toString(), "cmp-supabase-auth", version.toString())

    pom {
        name = "KMP Supabase Auth — Core"
        description =
            "Supabase authentication for Kotlin Multiplatform — native Google and Apple sign-in, " +
            "anonymous sessions, a Store5-backed session store, and one-line Koin wiring."
        inceptionYear = "2026"
        url = "https://github.com/MobileByteLabs/kmp-supabase-auth/"

        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }

        developers {
            developer {
                id = "MobileByteLabs"
                name = "MobileByteLabs"
                url = "https://github.com/MobileByteLabs"
            }
        }

        scm {
            url = "https://github.com/MobileByteLabs/kmp-supabase-auth/"
            connection = "scm:git:git://github.com/MobileByteLabs/kmp-supabase-auth.git"
            developerConnection = "scm:git:ssh://git@github.com/MobileByteLabs/kmp-supabase-auth.git"
        }
    }
}

// ── wasmJsBrowserTest: disabled, with a specific reason and a re-enable condition ────────────
//
// `jsBrowserTest`, `jsNodeTest` and `wasmJsNodeTest` all RUN and pass — only this one task is
// disabled, and only because of an upstream interaction we cannot configure around:
//
//   Uncaught SyntaxError: Cannot use 'import.meta' outside a module
//     at _karma_webpack_*/commons.js:18368
//
// The source is kotlinx-io's Node bindings, reached transitively via
// supabase-kt -> ktor -> kotlinx-io:
//
//   'kotlinx.io.node.getRequire' : () => {
//       const importMeta = import.meta;
//       return globalThis.module.default.createRequire(...)
//   }
//
// Karma serves the bundle with a classic <script src>, where `import.meta` is a PARSE error, so
// the suite dies before any test runs. Being unreachable Node-only code does not help.
//
// The usual webpack escape hatch (`module.parser.javascript.importMeta = false`, or forcing ESM
// output) cannot be applied: KGP does not feed `webpack.config.d/` into the wasm KARMA bundle —
// verified by adding a config there and confirming the generated karma.conf.js `extraJs` section
// stays empty. The fix belongs upstream, in KGP or kotlinx-io.
//
// Coverage is NOT lost: the same commonTest suite executes on jvm, iosX64/iosArm64/iosSimulator,
// linuxX64, android, jsBrowser, jsNode and wasmJsNode. Browser-specific wasm behaviour is the
// only gap, and this module has no browser-specific code.
//
// RE-ENABLE WHEN: `./gradlew :cmp-supabase-auth:wasmJsBrowserTest` passes after a KGP or
// kotlinx-io bump — delete this block and run it. Do not widen it to other targets.
tasks.matching { it.name == "wasmJsBrowserTest" }.configureEach {
    enabled = false
}

// Xcode does not provide a watchOS *simulator* test runner for watchos_simulator_arm64 — the
// task fails at property evaluation ("Check that requested SDK is installed"), which is an Xcode
// capability gap, not a code problem. The TARGET still compiles, links and publishes; only its
// simulator test execution is unavailable. watchosX64/watchosArm64 are unaffected.
tasks.matching { it.name == "watchosSimulatorArm64Test" }.configureEach {
    enabled = false
}
