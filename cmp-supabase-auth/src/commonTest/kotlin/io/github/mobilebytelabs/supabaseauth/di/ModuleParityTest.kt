package io.github.mobilebytelabs.supabaseauth.di

import io.github.mobilebytelabs.supabaseauth.SupabaseAuthConfig
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// `Module.mappings` / `includedModules` are @KoinInternalApi. Opting in HERE is deliberate and
// contained: this is a test asserting Koin's own structure, nothing ships with it, and the
// alternative — booting a Koin application and resolving real bindings — would need a live
// SupabaseClient just to compare two module graphs.
@OptIn(KoinInternalApi::class)
class ModuleParityTest {

    private val config = SupabaseAuthConfig(projectRef = "abcdefgh", googleWebClientId = "id")

    private fun Module.bindingKeys(): Set<String> =
        (listOf(this) + includedModules).flatMap { m -> m.mappings.keys.map { it.toString() } }.toSet()

    /**
     * The dual-wiring promise: `supabaseAuth(config)` and the three per-rung includes must
     * produce the SAME binding set.
     *
     * Everything else about the two modes is a structural claim in a README; this test is what
     * makes it true. If someone adds a binding to supabaseAuth() directly instead of to a rung,
     * this fails.
     */
    @Test
    fun allInOneModuleMatchesThePerRungModules() {
        val allInOne = supabaseAuth(config).bindingKeys()
        val perRung = listOf(
            supabaseAuthNetwork(config),
            supabaseAuthStore(),
            supabaseAuthRepository(),
        ).flatMap { it.bindingKeys() }.toSet()

        assertEquals(perRung, allInOne)
    }

    @Test
    fun everyRungContributesAtLeastOneBinding() {
        assertTrue(supabaseAuthNetwork(config).bindingKeys().isNotEmpty())
        assertTrue(supabaseAuthStore().bindingKeys().isNotEmpty())
        assertTrue(supabaseAuthRepository().bindingKeys().isNotEmpty())
    }
}
