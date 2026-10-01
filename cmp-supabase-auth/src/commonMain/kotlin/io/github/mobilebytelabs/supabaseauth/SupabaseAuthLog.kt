package io.github.mobilebytelabs.supabaseauth

/**
 * Debug logging for the sign-in path. **Off by default.**
 *
 * Auth is the hardest thing in this library to debug from the outside: a provider flow that
 * returns nothing looks identical to one that was never launched, and the interesting decisions —
 * native or web fallback, which callback arrived, whether the client was configured — all happen
 * where the app cannot see them. This is the seam that makes them visible.
 *
 * Enable it from the consuming app, usually in debug builds only:
 *
 * ```kotlin
 * SupabaseAuthLog.handler = { line -> println(line) }        // or Log.d("auth", line)
 * ```
 *
 * ## What is safe to log here
 *
 * Every call site in this library logs **decisions and outcomes, never credentials**. No access
 * token, no id token, no client id, no email, no user id. A client id is not secret in the way a
 * token is, but it is exactly the value people paste into a bug report, so [redacted] exists to
 * describe one without printing it.
 *
 * If you add a log line, describe WHAT HAPPENED. "no credential returned" is actionable;
 * "signInWithGoogle(1234-abc.apps.googleusercontent.com)" is a value in someone's log aggregator
 * forever.
 */
public object SupabaseAuthLog {

    /**
     * Where lines go. Null (the default) means logging is off and every call site short-circuits
     * before building its message, so an app that never sets this pays nothing.
     */
    public var handler: ((String) -> Unit)? = null

    /** True when a handler is installed. Guard expensive message construction with this. */
    public val isEnabled: Boolean get() = handler != null

    /**
     * Log a line, prefixed so it is greppable in a shared logcat.
     *
     * The message is built lazily: with no handler installed the lambda never runs.
     */
    // Public, not internal: `internal` is MODULE-scoped in Kotlin and the Compose module — which
    // owns the launchers, the noisiest thing worth logging — is a separate module. Consumers may
    // also log through it, which is fine: it is a formatter, not a capability.
    public inline fun log(message: () -> String) {
        handler?.invoke("[supabase-auth] ${message()}")
    }

    /** Log a failure with its cause's TYPE and message — never a stack of app-owned values. */
    public inline fun logError(error: Throwable?, message: () -> String) {
        handler?.invoke(
            buildString {
                append("[supabase-auth] ").append(message())
                if (error != null) {
                    append(" | cause=").append(error::class.simpleName)
                    error.message?.let { append(": ").append(it) }
                }
            },
        )
    }

    /**
     * Describe a credential-ish string without printing it — `"absent"`, or its length.
     *
     * Length is the diagnostic that actually matters: "is it blank?" and "did the whole value get
     * through, or a truncated one?" are the two questions a misconfigured client id raises, and
     * both are answerable without the value.
     */
    public fun redacted(value: String?): String = when {
        value == null -> "absent"
        value.isBlank() -> "blank"
        else -> "set(${value.length} chars)"
    }
}
