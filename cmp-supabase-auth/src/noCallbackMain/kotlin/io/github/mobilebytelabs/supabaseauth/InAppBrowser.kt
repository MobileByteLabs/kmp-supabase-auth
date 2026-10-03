package io.github.mobilebytelabs.supabaseauth

import io.github.jan.supabase.auth.AuthConfig

/**
 * No-op for every non-Android target — and NOT because they were overlooked.
 *
 * supabase-kt declares `ExternalAuthAction` only on Android. The iOS klib for auth-kt 3.8.0 carries
 * no such symbol and no `ASWebAuthenticationSession` / `SFSafariViewController` /`SafariServices`
 * binding; it opens the URL via `UIApplication.openURL`, i.e. the external Safari app. Desktop, JS
 * and Wasm have only the system browser.
 *
 * So there is nothing to set here. Writing a flag that silently did nothing on iOS would be worse
 * than this empty actual, which is at least honest at the call site.
 */
internal actual fun AuthConfig.preferInAppBrowser() = Unit
