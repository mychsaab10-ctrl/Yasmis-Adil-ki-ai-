package com.yesmin.ai.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.yesmin.ai.voice.CallState

@Composable
fun GlowCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val border = BorderStroke(1.dp, Brush.linearGradient(listOf(Cyan.copy(alpha = 0.55f), Purple.copy(alpha = 0.55f))))
    val m = if (onClick != null) modifier.clip(RoundedCornerShape(18.dp)).clickable { onClick() } else modifier
    Card(
        modifier = m,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = border
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
}

@Composable
fun Chip(text: String, ok: Boolean? = null) {
    val c = when (ok) { true -> Color(0xFF34D399); false -> Pink; null -> MaterialTheme.colorScheme.onSurfaceVariant }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) { Text(text, color = c, fontSize = 12.sp) }
}

@Composable
fun VoiceOrb(state: CallState, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "orb")
    val rot by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "rot")
    val speed = if (state == CallState.Speaking) 450 else 1400
    val pulse by inf.animateFloat(
        0.92f, 1.08f, infiniteRepeatable(tween(speed, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val base = when (state) {
        CallState.Listening -> Cyan
        CallState.Thinking -> Purple
        CallState.Speaking -> Pink
        CallState.Connecting -> Purple
        CallState.Error -> Color(0xFFFF4D6D)
        CallState.Idle -> Color(0xFF6B5E8F)
    }
    Canvas(modifier) {
        val c = center
        val k = if (state == CallState.Idle) 1f else pulse
        val r = size.minDimension / 2f * 0.72f * k
        drawCircle(
            brush = Brush.radialGradient(listOf(base.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r * 1.5f),
            radius = r * 1.5f, center = c
        )
        rotate(rot, c) {
            drawCircle(
                brush = Brush.sweepGradient(listOf(Cyan, Purple, Pink, Cyan), center = c),
                radius = r, center = c, style = Stroke(width = 7.dp.toPx())
            )
        }
        drawCircle(color = base.copy(alpha = 0.22f), radius = r * 0.78f, center = c)
        drawCircle(color = base, radius = r * 0.14f, center = c)
    }
}

/** Stylised vector avatar of Yesmin (drawn in code, no image assets needed). */
@Composable
fun Avatar(size: Dp, modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "avatar")
    val breathe by inf.animateFloat(
        0.985f, 1.015f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b"
    )
    Canvas(
        modifier.size(size).scale(breathe).clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2B1A55), Color(0xFF0B0714))))
    ) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(
            brush = Brush.radialGradient(listOf(Purple.copy(alpha = 0.45f), Color.Transparent), center = Offset(w / 2, h * 0.45f), radius = w * 0.6f),
            radius = w * 0.6f, center = Offset(w / 2, h * 0.45f)
        )
        drawOval(Color(0xFF3B1D6E), Offset(w * 0.2f, h * 0.08f), Size(w * 0.6f, h * 0.82f))                 // back hair
        drawArc(Color(0xFF2A1A52), 180f, 180f, true, Offset(w * 0.1f, h * 0.8f), Size(w * 0.8f, h * 0.5f)) // shoulders
        drawRect(Color(0xFFF0C2B0), Offset(w * 0.44f, h * 0.62f), Size(w * 0.12f, h * 0.2f))                 // neck
        drawOval(Color(0xFFF7D2C2), Offset(w * 0.3f, h * 0.2f), Size(w * 0.4f, h * 0.5f))                    // face
        val bangs = Path().apply {                                                                           // fringe
            moveTo(w * 0.27f, h * 0.46f)
            cubicTo(w * 0.24f, h * 0.1f, w * 0.76f, h * 0.1f, w * 0.73f, h * 0.46f)
            cubicTo(w * 0.68f, h * 0.31f, w * 0.56f, h * 0.27f, w * 0.5f, h * 0.25f)
            cubicTo(w * 0.44f, h * 0.31f, w * 0.34f, h * 0.33f, w * 0.27f, h * 0.46f)
            close()
        }
        drawPath(bangs, Color(0xFF4C2A8C))
        drawOval(Cyan, Offset(w * 0.375f, h * 0.43f), Size(w * 0.075f, h * 0.05f))                           // eyes
        drawOval(Cyan, Offset(w * 0.55f, h * 0.43f), Size(w * 0.075f, h * 0.05f))
        drawCircle(Pink.copy(alpha = 0.35f), w * 0.04f, Offset(w * 0.36f, h * 0.54f))                        // blush
        drawCircle(Pink.copy(alpha = 0.35f), w * 0.04f, Offset(w * 0.64f, h * 0.54f))
        drawArc(                                                                                              // smile
            Color(0xFFC2456F), 20f, 140f, false, Offset(w * 0.44f, h * 0.53f), Size(w * 0.12f, h * 0.07f),
            style = Stroke(width = w * 0.012f, cap = StrokeCap.Round)
        )
        drawArc(                                                                                              // neon headset
            Cyan, 200f, 140f, false, Offset(w * 0.27f, h * 0.17f), Size(w * 0.46f, h * 0.5f),
            style = Stroke(width = w * 0.012f, cap = StrokeCap.Round)
        )
    }
}
