package io.github.mobilebytelabs.supabaseauth.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.compose.auth.ui.ProviderIcon
import io.github.jan.supabase.compose.auth.ui.annotations.AuthUiExperimental
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthClient
import io.github.mobilebytelabs.supabaseauth.KmpSupabaseAuthError
import kotlinx.coroutines.CoroutineScope
import org.koin.compose.koinInject
import kotlin.time.Duration

/**
 * Shared height for every provider button on a sign-in screen.
 *
 * 56dp, and the number is chosen rather than inherited. It clears Apple's documented minimum for
 * the Sign in with Apple button (30pt @1x) and Google's 48dp button minimum with room to spare, and
 * it is the size a full-width primary CTA wants on a login screen — Material's default 40dp button
 * reads as a secondary action there. Equal height across all three is the point: providers rendered
 * at different heights look like different tiers of option, and Google's brand guidance explicitly
 * requires its button be no less prominent than the alternatives.
 */
public val KmpSupabaseSignInButtonHeight: Dp = 56.dp

/** Corner radius shared by the provider buttons. Apple permits adjusting it to match the app. */
private val ButtonShape = RoundedCornerShape(12.dp)

private val MarkBlack = Color(0xFF000000)
private val FieldWhite = Color(0xFFFFFFFF)

/**
 * ONE outline for every provider button.
 *
 * Google and Apple previously drew different strokes — Google a light `#DADCE0` hairline, Apple a
 * solid black one — and side by side that reads as two different KINDS of control rather than two
 * options of equal standing, which is the opposite of what both brands ask for. Apple permits
 * white-with-outline without constraining the outline's colour, and Google specifies this stroke,
 * so matching on Google's value satisfies both.
 */
private val ProviderOutline = Color(0xFFDADCE0)
private val GlyphSize = 20.dp
private val LabelSize = 17.sp

/**
 * Google sign-in button.
 *
 * The mark is [ProviderIcon], the official asset — never a hand-drawn glyph, which is a brand
 * violation and a plausible review finding. The LABEL is ours so the wording can be
 * "Continue with …", which reads correctly whether the person is creating an account or returning;
 * "Sign in" is wrong for half of them.
 *
 * ComposeAuthUI is flagged EXPERIMENTAL upstream, so its opt-in is required. Containing the `@OptIn`
 * here — rather than switching it on repo-wide — keeps this file the single blast radius for an
 * upstream API break, and consuming apps never opt in to an experimental API they did not choose.
 */
@OptIn(AuthUiExperimental::class)
@Composable
public fun KmpSupabaseGoogleSignInButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Continue with Google",
    height: Dp = KmpSupabaseSignInButtonHeight,
    client: KmpSupabaseAuthClient = koinInject(),
    linkIdentity: Boolean = false,
    timeout: Duration = KmpSupabaseDefaultSignInTimeout,
    scope: CoroutineScope? = null,
    onClick: () -> Unit = {},
    onError: (KmpSupabaseAuthError) -> Unit = {},
) {
    val launcher = rememberKmpSupabaseGoogleSignIn(
        client = client,
        linkIdentity = linkIdentity,
        timeout = timeout,
        watchdogScope = scope,
        onError = onError,
    )
    ProviderButton(
        onClick = {
            onClick()
            launcher.launch()
        },
        enabled = enabled && client.isConfigured,
        height = height,
        container = FieldWhite,
        content = MarkBlack,
        border = BorderStroke(1.dp, ProviderOutline),
        modifier = modifier,
    ) {
        ProviderIcon(Google, contentDescription = null, modifier = Modifier.size(GlyphSize))
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = LabelSize, fontWeight = FontWeight.Medium, color = MarkBlack)
    }
}

/**
 * Sign in with Apple button.
 *
 * **White field, black mark, black label** — one of the three presentations Apple permits (black,
 * white, white-with-outline), rendered with the official glyph unmodified. The black variant is
 * deliberately NOT used: ComposeAuthUI supplies the Apple mark as an SVG string whose fill is baked
 * in, so it cannot be recoloured to white, and drawing Apple's logo by hand to get a white one is
 * exactly the brand risk this button exists to avoid. White-with-outline is also the safer choice
 * on an unknown background.
 *
 * Unlike Google, Apple constrains the WORDING too: "Sign in with Apple" and "Continue with Apple"
 * are the system-provided variants, so [text] should stay one of those two.
 */
@OptIn(AuthUiExperimental::class)
@Composable
public fun KmpSupabaseAppleSignInButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Continue with Apple",
    height: Dp = KmpSupabaseSignInButtonHeight,
    /**
     * Which of Apple's permitted presentations to render. White by default so the button sits in a
     * row with the other providers; [KmpSupabaseAppleButtonStyle.Black] is the black field variant,
     * reachable now that the mark is a tintable vector rather than a fixed-fill SVG.
     */
    style: KmpSupabaseAppleButtonStyle = KmpSupabaseAppleButtonStyle.White,
    client: KmpSupabaseAuthClient = koinInject(),
    linkIdentity: Boolean = false,
    timeout: Duration = KmpSupabaseDefaultSignInTimeout,
    scope: CoroutineScope? = null,
    onClick: () -> Unit = {},
    onError: (KmpSupabaseAuthError) -> Unit = {},
) {
    val launcher = rememberKmpSupabaseAppleSignIn(
        client = client,
        linkIdentity = linkIdentity,
        timeout = timeout,
        watchdogScope = scope,
        onError = onError,
    )
    val onBlack = style == KmpSupabaseAppleButtonStyle.Black
    val field = if (onBlack) MarkBlack else FieldWhite
    val ink = if (onBlack) FieldWhite else MarkBlack
    ProviderButton(
        onClick = {
            onClick()
            launcher.launch()
        },
        enabled = enabled && client.isConfigured,
        height = height,
        container = field,
        content = ink,
        border = BorderStroke(1.dp, if (onBlack) MarkBlack else ProviderOutline),
        modifier = modifier,
    ) {
        Icon(
            imageVector = kmpSupabaseAppleLogo(ink),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(GlyphSize),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, fontSize = LabelSize, fontWeight = FontWeight.Medium, color = ink)
    }
}

/** Apple's permitted button presentations. */
public enum class KmpSupabaseAppleButtonStyle { White, Black }

/** One shape/height/row for every provider, so no provider can drift from the others. */
@Composable
private fun ProviderButton(
    onClick: () -> Unit,
    enabled: Boolean,
    height: Dp,
    container: Color,
    content: Color,
    border: BorderStroke,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = border,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.38f),
            disabledContentColor = content.copy(alpha = 0.38f),
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier.fillMaxWidth().height(height),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) { label() }
    }
}
