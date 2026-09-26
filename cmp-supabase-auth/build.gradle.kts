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
// TARGETS — 8. MEASURED against Maven Central on 2026-09-26, never inferred.
//
// This module's ceiling is the INTERSECTION of its two hard dependencies:
//   auth-kt 3.8.0        17 targets — no linuxArm64 / watchosArm32 / watchosDeviceArm64 / wasmWasi
//   store5  5.1.0-beta01  8 targets — no macOS / tvOS / watchOS / mingwX64 / linuxArm64
//
// store5 is the binding constraint. Dropping macOS, tvOS, watchOS and Windows is therefore a
// measurement, not a preference — re-probe before widening this list, and widen it the moment
// store5 publishes more. `curl -o /dev/null -w '%{http_code}' \
//   https://repo1.maven.org/maven2/org/mobilenativefoundation/store/store5-<target>/<v>/...pom`
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

    // iOS — all three. compose-auth publishes iosX64, and this module carries no Compose, so
    // unlike cmp-supabase-auth-compose it is free to declare it.
    //
    // The linker options are NOT optional. supabase-kt pulls
    // `dev.whyoleg.cryptography:cryptography-provider-cryptokit`, whose CryptoKit interop is
    // written in Swift; linking it needs the Swift compatibility shims, which the Kotlin/Native
    // linker does not add on its own. Without them every *test* binary fails with
    // `Undefined symbols: __swift_FORCE_LOAD_$_swiftCompatibility56`. Resolved from the ACTIVE
    // Xcode via xcrun rather than hardcoded, so this survives an Xcode upgrade or a different
    // install path on CI.
    // HOST-GATED: `xcrun` exists only on macOS, and this resolves at CONFIGURATION time, so an
    // ungated call aborts the whole build on a Linux runner — which is every ubuntu CI job,
    // including ones that never touch an Apple target. Apple targets cannot be built off macOS
    // anyway, so skipping the linker options there loses nothing.
    val isMacOs = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        if (!isMacOs) return@forEach
        // iosX64 is the INTEL SIMULATOR, but its konan name is `ios_x64` with no
        // "simulator" in it — matching on the name alone sends it to the device SDK
        // and the link fails. Only iosArm64 is a real device target.
        val sdk = if (target.konanTarget.name == "ios_arm64") "iphoneos" else "iphonesimulator"
        target.binaries.all {
            linkerOpts("-L${swiftLibraryPath(sdk)}")
        }
    }

    // Linux x64 only — store5 publishes no linuxArm64.
    linuxX64()

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
        commonMain.dependencies {
            // `api`, not `implementation`: SupabaseAuthClient exposes a ComposeAuth handle and
            // SessionStatus, so consumers must see those types. That leak is deliberate — native
            // Google/Apple sign-in are composition-scoped Compose APIs that cannot be wrapped
            // without reimplementing Credential Manager and ASAuthorization by hand.
            api(libs.supabase.auth)

            // Store5 backs the session store: memory-only, Fetcher.ofFlow over GoTrue's
            // sessionStatus. NOT disk-cached — GoTrue already persists and refreshes, and
            // re-caching would show a signed-in user after the real token had expired.
            api(libs.store5)

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
