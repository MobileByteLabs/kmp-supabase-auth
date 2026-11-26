package io.github.mobilebytelabs.supabaseauth

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The published JVM artifact must load on a Java 17 runtime. 0.3.4 and 0.3.5 shipped Java 21 bytecode (major 65)
 * because the `jvm()` target inherited the CI JDK — a Java 17 consumer (PayCraft's JVM tests) then died with
 * `UnsupportedClassVersionError`. Reads this module's own compiled class, so it fails whatever JDK CI runs.
 */
class JvmBytecodeTargetTest {
    @Test
    fun classesTargetJava17OrOlder() {
        val bytes = KmpSupabaseAuthError::class.java
            .getResourceAsStream("KmpSupabaseAuthError.class")!!
            .use { it.readBytes() }
        val major = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        assertTrue(major <= JAVA_17, "class file major $major > $JAVA_17 (Java 17)")
    }

    private companion object {
        const val JAVA_17 = 61
    }
}
