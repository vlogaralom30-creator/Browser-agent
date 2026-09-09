package com.example.ui.agent

import com.example.ai.AgentPointerState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentPointerOverlay(
    pointerState: AgentPointerState,
    modifier: Modifier = Modifier
) {
    if (!pointerState.isVisible) return

    val animatedXFraction by animateFloatAsState(
        targetValue = pointerState.xPercent.coerceIn(0.05f, 0.92f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pointer_x_anim"
    )

    val animatedYFraction by animateFloatAsState(
        targetValue = pointerState.yPercent.coerceIn(0.05f, 0.92f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pointer_y_anim"
    )

    val clickRippleAnim = remember { Animatable(0f) }

    LaunchedEffect(pointerState.isClicking) {
        if (pointerState.isClicking) {
            clickRippleAnim.snapTo(0f)
            clickRippleAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("agent_pointer_overlay_box")
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val densityVal = androidx.compose.ui.platform.LocalDensity.current.density

        val xOffsetDp = (widthPx * animatedXFraction / densityVal).dp
        val yOffsetDp = (heightPx * animatedYFraction / densityVal).dp

        Box(
            modifier = Modifier
                .offset(x = xOffsetDp, y = yOffsetDp)
        ) {
            // Click Ripple Circle Effect
            if (clickRippleAnim.value > 0f && clickRippleAnim.value < 1f) {
                Box(
                    modifier = Modifier
                        .size((48 * clickRippleAnim.value).dp)
                        .scale(1f + clickRippleAnim.value * 0.5f)
                        .alpha(1f - clickRippleAnim.value)
                        .border(
                            width = 3.dp,
                            brush = Brush.radialGradient(
                                listOf(Color(0xFF673AB7), Color(0xFF00E5FF))
                            ),
                            shape = CircleShape
                        )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AI Agent Pointer Icon (Glowing Navigation Arrow / Cursor)
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF3F51B5),
                                        Color(0xFF7C4DFF),
                                        Color(0xFF00BCD4)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "AI Agent Pointer",
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(-45f)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Action Status Badge Pill
                if (pointerState.actionText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xEC121829),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pointerState.actionText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
