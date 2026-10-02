package io.github.mobilebytelabs.supabaseauth

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Captures the Application at process start so the OAuth tab can be launched from the CURRENT
 * ACTIVITY, keeping it in the app's own task (see [preferInAppBrowser]).
 *
 * A ContentProvider is the standard zero-config self-init hook: the system instantiates it before
 * `Application.onCreate`, so nothing has to be wired by the consumer. supabase-kt's own
 * `applicationContext()` is Kotlin-`internal` and cannot be called from this module, and requiring
 * an Application parameter in the library's API would break its commonMain-only public surface.
 *
 * `android:exported="false"` and a unique authority derived from `${applicationId}` — two apps
 * embedding this library must not collide on install.
 */
internal class KmpSupabaseAuthInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let(CurrentActivity::install)
        return true
    }

    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0
}
