package com.krafttools.barokraft.ui

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

/**
 * A tap target with no ripple.
 *
 * The default ripple on a large surface card is a grey circle that has
 * nothing to do with the content, and on a screen this dark it reads as a
 * rendering fault rather than as feedback. The press feedback here is the
 * surface colour change that the caller already applies through
 * [Modifier.background], so the ripple would be a second, uglier signal
 * for the same event.
 */
internal fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = null,
        indication = null,
        onClick = onClick,
    )
