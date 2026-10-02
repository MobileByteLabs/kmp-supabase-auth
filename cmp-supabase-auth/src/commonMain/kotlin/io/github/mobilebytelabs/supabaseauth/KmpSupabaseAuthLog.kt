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
 * KmpSupabaseAuthLog.handler = { line -> println(line) }        // or Log.d("auth", line)
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
public object KmpSupabaseAuthLog {

    /**
     * Explicit sink override. Null (the default) does NOT mean silence — it means "use the
     * build-type default", which is `println` in a DEBUG build of the host app and silence in a
     * release one. See [isDebugBuild].
     *
     * Set it to route lines somewhere else (`Log.d`, Timber, a crash-reporter breadcrumb); set
     * [silenced] to turn diagnostics off even in debug.
     */
    public var handler: ((String) -> Unit)? = null

    /** Force silence regardless of build type. For a debug build that must stay quiet. */
    public var silenced: Boolean = false

    /**
     * The build-type default: `println` in debug, nothing in release.
     *
     * Logging USED to default to off on every platform, which read as a safe choice and was not:
     * auth is the least observable part of an app from the outside, no consumer ever opted in, and
     * a failing iOS sign-in produced a device log with zero lines about the sign-in path across
     * four device rounds. Debug builds now explain themselves by default.
     *
     * `by lazy` on purpose — Android resolves [isDebugBuild] through the Application captured by a
     * ContentProvider, so this must not be evaluated at class-init time.
     */
    @PublishedApi
    internal val buildTypeDefault: ((String) -> Unit)? by lazy {
        if (isDebugBuild) { line: String -> println(line) } else null
    }

    /** The sink actually used: explicit override, else the build-type default, unless silenced. */
    @PublishedApi
    internal val sink: ((String) -> Unit)?
        get() = if (silenced) null else handler ?: buildTypeDefault

    /** True when something will receive lines. Guard expensive message construction with this. */
    public val isEnabled: Boolean get() = sink != null

    /**
     * Log a line, prefixed so it is greppable in a shared logcat.
     *
     * The message is built lazily: with no handler installed the lambda never runs.
     */
    // Public, not internal: `internal` is MODULE-scoped in Kotlin and the Compose module — which
    // owns the launchers, the noisiest thing worth logging — is a separate module. Consumers may
    // also log through it, which is fine: it is a formatter, not a capability.
    public inline fun log(message: () -> String) {
        sink?.invoke("[supabase-auth] ${message()}")
    }

    /** Log a failure with its cause's TYPE and message — never a stack of app-owned values. */
    public inline fun logError(error: Throwable?, message: () -> String) {
        sink?.invoke(
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
