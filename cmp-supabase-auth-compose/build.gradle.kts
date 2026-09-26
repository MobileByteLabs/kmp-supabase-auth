import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.vanniktech.mavenPublish)
    id("io.github.mobilebytelabs.supabaseauth.dokka")
    id("io.github.mobilebytelabs.supabaseauth.kover")
    alias(libs.plugins.binaryCompatibilityValidator)
}

group = "io.github.mobilebytelabs"
version = providers.gradleProperty("supabaseauth.version").get()

// ─────────────────────────────────────────────────────────────────────────────────────────────
// TARGETS — 6, two fewer than cmp-supabase-auth. Both losses are MEASURED, and they have DIFFERENT causes:
//
//   macOS   — compose-auth 3.8.0 publishes NO macOS artifact at all (verified HTTP 404 for both
//             compose-auth-macosarm64 and compose-auth-macosx64, 2026-09-26). Consequence for
//             users: there is NO native Apple sign-in on macOS through this library; macOS
//             consumers take cmp-supabase-auth plus the web-OAuth fallback. Said plainly in SETUP_APPLE.md
//             rather than left to surface at link time.
//   iosX64  — Compose Multiplatform itself publishes no iosX64 artifact. cmp-supabase-auth DOES declare
//             iosX64 because it carries no Compose; only this module has to drop it.
//
// A Compose module can never match its headless sibling's matrix anyway: the Compose compiler
// plugin applies to EVERY compilation in a module and fails on any target lacking the runtime.
// Confining Compose to an intermediate source set does not work.
// ─────────────────────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)
kotlin {
    applyDefaultHierarchyTemplate()

    jvm()

    androidLibrary {
        namespace = "io.github.mobilebytelabs.supabaseauth.compose"
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
            isReturnDefaultValues = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    // `binaries.executable()` on both web targets: the Compose plugin refuses to configure UI
    // tests without it, because the Skiko runtime Compose needs can only be loaded from a
    // webpack-bundled executable (CMP-4906). Harmless for a library — it only affects how the
    // test bundle is produced, not what is published.
    js {
        browser()
        nodejs()
        binaries.executable()
    }

    wasmJs {
        browser()
        nodejs()
        binaries.executable()
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: SupabaseLoginScreen takes an AuthRepository and a SupabaseAuthClient, both
            // cmp-supabase-auth types, so every consumer of this module needs them on the compile path.
            api(project(":cmp-supabase-auth"))

            // rememberSignInWithGoogle() / rememberSignInWithApple() — the native flows.
            api(libs.supabase.compose.auth)
            // ProviderButtonContent(Google/Apple) — brand-compliant marks. Hand-drawing these
            // risks store review, so the component ships rather than a bespoke icon.
            implementation(libs.supabase.compose.auth.ui)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
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

    coordinates(group.toString(), "cmp-supabase-auth-compose", version.toString())

    pom {
        name = "KMP Supabase Auth — Compose"
        description =
            "Compose Multiplatform UI for KMP Supabase Auth — brand-compliant Google and Apple " +
            "provider buttons, a slot-based login screen, and a session-driven ViewModel."
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
