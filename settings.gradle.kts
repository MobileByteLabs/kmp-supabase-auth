pluginManagement {
    // Composite build supplying the dokka + kover convention plugins.
    includeBuild("build-logic")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kmp-supabase-auth"
include(":cmp-supabase-auth") // headless — client, Store5 session store, repository, Koin DI
include(":cmp-supabase-auth-compose") // Compose UI — provider buttons, login screen, ViewModel
include(":sample-app")
