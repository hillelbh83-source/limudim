package com.hillel.studyzone.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hillel.studyzone.ui.theme.StudyBlue

val LocalHapticsEnabled = staticCompositionLocalOf { true }

/**
 * A lightweight glass-like surface.
 *
 * The previous implementation allocated a shadow graphics layer for every list item. That is
 * particularly expensive while a lazy grid scrolls. Depth now comes from contrast and a fine
 * border, while overlays can still compose their own scrim around this surface.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            StudyBlue.copy(alpha = .16f)
        } else {
            // Translucency keeps the floating hierarchy visible in both palettes without the
            // continuous blur render cost that made the first version stutter while scrolling.
            MaterialTheme.colorScheme.surface.copy(alpha = .86f)
        },
        animationSpec = spring(stiffness = 900f, dampingRatio = .9f),
        label = "glassBackground"
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            StudyBlue.copy(alpha = .46f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = .48f)
        },
        animationSpec = spring(stiffness = 900f, dampingRatio = .9f),
        label = "glassBorder"
    )
    Surface(
        modifier = modifier,
        shape = shape,
        color = background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content
    )
}

@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    enabled: Boolean = true,
    selected: Boolean = false,
    contentPadding: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .975f else 1f,
        animationSpec = spring(stiffness = 900f, dampingRatio = .72f),
        label = "pressScale"
    )
    GlassSurface(
        modifier = modifier
            .scale(scale)
            .semantics {
                role = Role.Button
                this.selected = selected
            },
        shape = shape,
        selected = selected
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                    role = Role.Button,
                    onClick = {
                        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    }
                )
                .defaultMinSize(minHeight = 50.dp)
                .padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
fun RoundActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 50.dp,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .9f else 1f,
        animationSpec = spring(stiffness = 920f, dampingRatio = .66f),
        label = "roundScale"
    )
    val fill by animateColorAsState(
        targetValue = if (active) StudyBlue else MaterialTheme.colorScheme.surface,
        label = "roundFill"
    )
    val touchSize = if (size < 48.dp) 48.dp else size
    Box(
        modifier = modifier
            .sizeIn(minWidth = touchSize, minHeight = touchSize)
            .alpha(if (enabled) 1f else .4f)
            .scale(scale)
            .shadow(
                elevation = if (active) 12.dp else 8.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = .16f),
                spotColor = if (active) StudyBlue.copy(alpha = .32f) else Color.Black.copy(alpha = .22f)
            )
            .clip(CircleShape)
            .background(fill.copy(alpha = if (active) .96f else .84f))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (active) .20f else .26f), Color.Transparent)
                )
            )
            .border(
                1.dp,
                if (active) Color.White.copy(alpha = .34f) else MaterialTheme.colorScheme.outline.copy(alpha = .58f),
                CircleShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = {
                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size((size * .46f).coerceIn(20.dp, 25.dp)),
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Coil provides memory/disk caching; initials remain visible until a real image is decoded. */
@Composable
fun ProfileAvatar(
    photoUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(StudyBlue.copy(alpha = .16f))
            .border(1.dp, StudyBlue.copy(alpha = .34f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayName.trim().take(1).ifBlank { "?" }.uppercase(),
            color = StudyBlue,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleLarge
        )
        if (!photoUrl.isNullOrBlank()) {
            val context = LocalContext.current
            val request = remember(photoUrl) {
                ImageRequest.Builder(context)
                    .data(photoUrl)
                    .crossfade(180)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = "תמונת הפרופיל של $displayName",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
