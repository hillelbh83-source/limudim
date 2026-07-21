package com.hillel.studyzone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hillel.studyzone.ui.theme.StudyBlue

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    selected: Boolean = false,
    content: @Composable () -> Unit
) {
    val background by animateColorAsState(
        if (selected) StudyBlue.copy(alpha = .14f) else MaterialTheme.colorScheme.surface.copy(alpha = .88f),
        label = "glassBackground"
    )
    Surface(
        modifier = modifier.graphicsLayer {
            shadowElevation = 22f
            this.shape = shape
            clip = false
        },
        shape = shape,
        color = background,
        border = BorderStroke(
            1.dp,
            if (selected) StudyBlue.copy(alpha = .42f) else MaterialTheme.colorScheme.outline.copy(alpha = .62f)
        ),
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
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .965f else 1f,
        animationSpec = spring(stiffness = 720f, dampingRatio = .72f),
        label = "pressScale"
    )
    GlassSurface(modifier = modifier.scale(scale), shape = shape, selected = selected) {
        Row(
            modifier = Modifier
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    interactionSource = interaction,
                    indication = ripple(bounded = true),
                    onClick = onClick
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
    size: Dp = 50.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else 1f, spring(stiffness = 800f), label = "roundScale")
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(if (active) StudyBlue else MaterialTheme.colorScheme.surface.copy(alpha = .92f))
            .border(
                1.dp,
                if (active) Color.White.copy(alpha = .22f) else MaterialTheme.colorScheme.outline.copy(alpha = .6f),
                CircleShape
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .defaultMinSize(size, size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
