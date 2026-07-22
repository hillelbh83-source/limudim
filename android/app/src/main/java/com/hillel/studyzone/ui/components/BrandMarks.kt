package com.hillel.studyzone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hillel.studyzone.ui.theme.StudyBlue

/** One clean scalable brand mark shared by the in-app launch transition. */
@Composable
fun StudyZoneMark(size: Dp = 80.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * .29f))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF07152F), Color(0xFF0B3A86), StudyBlue),
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
            .border(1.dp, Color.White.copy(alpha = .24f), RoundedCornerShape(size * .29f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val path = Path().apply {
                moveTo(w * .72f, h * .27f)
                cubicTo(
                    w * .60f, h * .18f,
                    w * .34f, h * .21f,
                    w * .30f, h * .38f
                )
                cubicTo(
                    w * .27f, h * .50f,
                    w * .41f, h * .54f,
                    w * .54f, h * .55f
                )
                cubicTo(
                    w * .70f, h * .57f,
                    w * .78f, h * .64f,
                    w * .73f, h * .76f
                )
                cubicTo(
                    w * .67f, h * .91f,
                    w * .39f, h * .89f,
                    w * .27f, h * .78f
                )
            }
            drawPath(
                path = path,
                color = Color.Black.copy(alpha = .24f),
                style = Stroke(width = this.size.width * .15f, cap = StrokeCap.Round)
            )
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF69D6FF), Color.White, Color(0xFFB9DAFF)),
                    start = Offset(0f, 0f),
                    end = Offset(this.size.width, this.size.height)
                ),
                style = Stroke(width = this.size.width * .105f, cap = StrokeCap.Round)
            )
            drawCircle(
                color = Color(0xFF7EE3FF),
                radius = this.size.width * .035f,
                center = Offset(this.size.width * .78f, this.size.height * .20f)
            )
            drawOval(
                color = Color.White.copy(alpha = .23f),
                topLeft = Offset(this.size.width * .13f, this.size.height * .1f),
                size = Size(this.size.width * .50f, this.size.height * .24f)
            )
        }
    }
}
