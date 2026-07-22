package com.hillel.studyzone.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hillel.studyzone.ui.theme.StudyBlue
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt

val LocalHapticsEnabled = staticCompositionLocalOf { true }

internal val LocalLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }
private val LocalContentBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun LiquidBackdropLayer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
    overlays: (@Composable BoxScope.() -> Unit)? = null
) {
    val ambientBackdrop = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalLiquidBackdrop provides ambientBackdrop,
        LocalContentBackdrop provides contentBackdrop
    ) {
        Box(modifier) {
            Box(Modifier.matchParentSize().layerBackdrop(contentBackdrop)) {
                LiquidAmbientBackground(Modifier.matchParentSize().layerBackdrop(ambientBackdrop))
                content()
            }
            if (overlays != null) {
                CompositionLocalProvider(LocalLiquidBackdrop provides contentBackdrop) {
                    overlays()
                }
            }
        }
    }
}

@Composable
private fun LiquidAmbientBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .10f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.secondary.copy(alpha = .08f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(1200f, 1800f)
                )
            )
    )
}

@Composable
private fun Modifier.liquidBackdrop(
    shape: Shape,
    selected: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    exportedBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
    highlightAlpha: Float = if (selected) .78f else .5f,
    blurRadius: Dp = if (selected) 14.dp else 10.dp,
    lensRadius: Dp = if (selected) 22.dp else 16.dp
): Modifier {
    val backdrop = LocalLiquidBackdrop.current ?: return this
    return drawBackdrop(
        backdrop = backdrop,
        exportedBackdrop = exportedBackdrop,
        layerBlock = layerBlock,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx())
            lens(lensRadius.toPx(), 26.dp.toPx(), chromaticAberration = true)
        },
        highlight = {
            Highlight.Default.copy(alpha = highlightAlpha)
        },
        shadow = {
            Shadow(
                radius = 18.dp,
                color = Color.Black.copy(alpha = if (selected) .18f else .11f)
            )
        },
        innerShadow = {
            InnerShadow(radius = 8.dp, alpha = if (selected) .58f else .34f)
        },
        onDrawSurface = {
            drawRect(surfaceColor)
            drawLiquidSheen(selected, borderColor)
        }
    )
}

private fun DrawScope.drawLiquidSheen(selected: Boolean, borderColor: Color) {
    drawRect(
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (selected) .22f else .15f),
                Color.Transparent,
                Color.Black.copy(alpha = .035f)
            )
        )
    )
    drawRect(borderColor.copy(alpha = if (selected) .10f else .06f), blendMode = BlendMode.Screen)
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    selected: Boolean = false,
    exportedBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    layerBlock: (androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val surfaceColor by animateColorAsState(
        targetValue = if (selected) {
            StudyBlue.copy(alpha = .22f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = .54f)
        },
        animationSpec = spring(stiffness = 900f, dampingRatio = .9f),
        label = "liquidSurface"
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            StudyBlue.copy(alpha = .58f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = .48f)
        },
        animationSpec = spring(stiffness = 900f, dampingRatio = .9f),
        label = "liquidBorder"
    )
    val backdrop = LocalLiquidBackdrop.current
    if (backdrop == null) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = surfaceColor.copy(alpha = if (selected) .26f else .86f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, border),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content
        )
    } else {
        val boxContent = @Composable {
            Box(
                modifier = modifier
                    .liquidBackdrop(shape, selected, surfaceColor, border, exportedBackdrop, layerBlock)
                    .border(1.dp, border, shape),
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    content()
                }
            }
        }
        if (exportedBackdrop != null) {
            CompositionLocalProvider(LocalLiquidBackdrop provides exportedBackdrop) {
                boxContent()
            }
        } else {
            boxContent()
        }
    }
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
        targetValue = if (pressed && enabled) .965f else 1f,
        animationSpec = spring(stiffness = 900f, dampingRatio = .68f),
        label = "pressScale"
    )
    GlassSurface(
        modifier = modifier
            .alpha(if (enabled) 1f else .45f)
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
        targetValue = if (pressed && enabled) .88f else 1f,
        animationSpec = spring(stiffness = 920f, dampingRatio = .62f),
        label = "roundScale"
    )
    val touchSize = if (size < 48.dp) 48.dp else size
    GlassSurface(
        modifier = modifier
            .sizeIn(minWidth = touchSize, minHeight = touchSize)
            .alpha(if (enabled) 1f else .4f)
            .scale(scale),
        shape = CircleShape,
        selected = active
    ) {
        Box(
            Modifier
                .size(touchSize)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = if (active) .17f else .09f),
                            Color.Transparent
                        )
                    )
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
}

@Composable
fun LiquidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    GlassSurface(modifier, shape = shape, selected = isError) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = label,
            placeholder = placeholder,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                cursorColor = StudyBlue,
                focusedLeadingIconColor = StudyBlue,
                focusedTrailingIconColor = StudyBlue
            )
        )
    }
}

@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    val hapticsEnabled = LocalHapticsEnabled.current
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 31.dp else 3.dp,
        animationSpec = spring(stiffness = 720f, dampingRatio = .72f),
        label = "switchKnob"
    )
    GlassSurface(
        modifier = modifier
            .size(66.dp, 36.dp)
            .alpha(if (enabled) 1f else .45f)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = ripple(bounded = true),
                role = Role.Switch,
                onClick = {
                    if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onCheckedChange(!checked)
                }
            ),
        shape = CircleShape,
        selected = checked
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .offset(x = knobOffset)
                    .size(32.dp, 30.dp)
                    .liquidBackdrop(
                        shape = CircleShape,
                        selected = checked,
                        surfaceColor = Color.White.copy(alpha = if (checked) .72f else .56f),
                        borderColor = Color.White.copy(alpha = .46f),
                        exportedBackdrop = null,
                        layerBlock = null,
                        highlightAlpha = .7f,
                        blurRadius = 6.dp,
                        lensRadius = 12.dp
                    )
                    .border(1.dp, Color.White.copy(alpha = .58f), CircleShape)
            )
        }
    }
}

@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
) {
    val layoutDirection = LocalLayoutDirection.current
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        fun valueAt(x: Float): Float {
            val rawFraction = (x / widthPx).coerceIn(0f, 1f)
            val visualFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - rawFraction else rawFraction
            return valueRange.start + span * visualFraction
        }
        val trackBackdrop = rememberLayerBackdrop()
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(valueRange, layoutDirection) {
                    detectTapGestures { onValueChange(valueAt(it.x)) }
                }
                .pointerInput(valueRange, layoutDirection) {
                    detectDragGestures { change, _ ->
                        onValueChange(valueAt(change.position.x))
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth().height(18.dp).layerBackdrop(trackBackdrop),
                shape = CircleShape
            ) {
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(18.dp)
                            .clip(CircleShape)
                            .background(StudyBlue.copy(alpha = .42f))
                    )
                }
            }
            val visualFraction = if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction
            val currentBackdrop = LocalLiquidBackdrop.current
            val thumbBackdrop = if (currentBackdrop != null) rememberCombinedBackdrop(currentBackdrop, trackBackdrop) else null
            
            CompositionLocalProvider(LocalLiquidBackdrop provides thumbBackdrop) {
                Box(
                    Modifier
                        .offset { IntOffset((widthPx * visualFraction - 22.dp.toPx()).roundToInt(), 0) }
                        .size(44.dp, 32.dp)
                        .liquidBackdrop(
                            shape = CircleShape,
                            selected = true,
                            surfaceColor = StudyBlue.copy(alpha = .32f),
                            borderColor = StudyBlue.copy(alpha = .62f),
                            exportedBackdrop = null,
                            layerBlock = null,
                            blurRadius = 8.dp,
                            lensRadius = 16.dp
                        )
                        .border(1.dp, Color.White.copy(alpha = .52f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    photoUrl: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    GlassSurface(
        modifier = modifier.size(size),
        shape = CircleShape,
        selected = true
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(StudyBlue.copy(alpha = .10f)),
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
}
