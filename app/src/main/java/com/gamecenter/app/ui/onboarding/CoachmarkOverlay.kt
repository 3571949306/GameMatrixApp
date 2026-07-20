package com.gamecenter.app.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gamecenter.app.R

/**
 * 渲染单步 Coachmark 聚光遮罩。
 *
 * 设计要点（参考设计总监颜好看 §5.6 方向 + Spec §6 优先级）：
 * 1. 全屏半透明 scrim 盖在目标页面之上
 * 2. 在目标 View 矩形位置"挖洞"——透明高亮区 + 主色描边
 * 3. 步骤浮层（标题 + 说明 + 下一步/跳过按钮 + 步骤指示器点）展示在屏幕底部
 * 4. 引用 GameMatrixTheme 的 colorScheme / typography（外部 [MaterialTheme] 注入）
 * 5. 间距全部走 gm_spacing_* token（dimensionResource），不硬编码 dp
 * 6. 当 [reduceMotion] = true 时关闭脉冲动画（无障碍 / Reduced motion）
 *
 * @param targetRect 目标 View 在 Compose 坐标系下的矩形（已减去状态栏/导航栏偏移）。
 * @param step 当前步骤数据。
 * @param currentStepIndex 当前步骤下标（0-based）。
 * @param totalSteps 总步数。
 * @param reduceMotion 是否减少动态效果（无障碍设置）。
 * @param onNext 点击"下一步/完成"回调。
 * @param onSkip 点击"跳过"回调。
 */
@Composable
fun CoachmarkOverlay(
    targetRect: Rect,
    step: CoachmarkStep,
    currentStepIndex: Int,
    totalSteps: Int,
    reduceMotion: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val highlightPadding = dimensionResource(R.dimen.gm_spacing_2)
    val cardCornerRadius = dimensionResource(R.dimen.gm_spacing_4)
    val strokeWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        2.dp.toPx()
    }

    // 脉冲缩放动画（reduceMotion=true 时为常量 1f，不持续动画）
    val infiniteTransition = rememberInfiniteTransition(label = "coachmark-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (reduceMotion) 1f else 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "coachmark-pulse-scale",
    )

    // 步骤切换时的渐显动画（一次性，即使 reduceMotion 也保留）
    val enterAnim = remember { Animatable(0f) }
    LaunchedEffect(currentStepIndex) {
        enterAnim.snapTo(0f)
        enterAnim.animateTo(1f, tween(durationMillis = 220))
    }

    Dialog(
        onDismissRequest = onSkip,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // ===== 1. scrim + 挖洞 =====
            Canvas(modifier = Modifier.fillMaxSize()) {
                val expandedRect = targetRect.inflate(highlightPadding.toPx())
                val holePath = when (step.shape) {
                    CoachmarkShape.CIRCLE -> {
                        val center = expandedRect.center
                        val baseRadius =
                            (maxOf(expandedRect.width, expandedRect.height) / 2f)
                        val radius = baseRadius * pulseScale
                        Path().apply {
                            addOval(
                                Rect(
                                    left = center.x - radius,
                                    top = center.y - radius,
                                    right = center.x + radius,
                                    bottom = center.y + radius,
                                )
                            )
                        }
                    }
                    CoachmarkShape.ROUNDED_RECT -> {
                        val scaled = Rect(
                            left = expandedRect.center.x -
                                (expandedRect.width / 2f) * pulseScale,
                            top = expandedRect.center.y -
                                (expandedRect.height / 2f) * pulseScale,
                            right = expandedRect.center.x +
                                (expandedRect.width / 2f) * pulseScale,
                            bottom = expandedRect.center.y +
                                (expandedRect.height / 2f) * pulseScale,
                        )
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = scaled.left,
                                    top = scaled.top,
                                    right = scaled.right,
                                    bottom = scaled.bottom,
                                    cornerRadius = CornerRadius(
                                        16.dp.toPx(),
                                        16.dp.toPx(),
                                    ),
                                )
                            )
                        }
                    }
                }

                // 全屏 scrim
                drawRect(color = Color.Black.copy(alpha = 0.78f))
                // 在 scrim 上挖洞（ClipOp.Difference 让 holePath 区域不被填充）
                clipPath(path = holePath, clipOp = ClipOp.Difference) {
                    drawRect(color = Color.Black.copy(alpha = 0.78f))
                }
                // 高亮区描边
                drawPath(
                    path = holePath,
                    color = colorScheme.primary,
                    style = Stroke(width = strokeWidthPx),
                )
            }

            // ===== 2. 步骤浮层（屏幕底部） =====
            StepFloatingCard(
                step = step,
                currentStepIndex = currentStepIndex,
                totalSteps = totalSteps,
                enterAlpha = enterAnim.value,
                onNext = onNext,
                onSkip = onSkip,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = dimensionResource(R.dimen.gm_spacing_4),
                        end = dimensionResource(R.dimen.gm_spacing_4),
                        bottom = dimensionResource(R.dimen.gm_spacing_7),
                    ),
                cardCornerRadius = cardCornerRadius,
            )
        }
    }
}

/**
 * 步骤浮层卡片：标题 + 说明 + 步骤指示器点 + 下一步/跳过按钮。
 */
@Composable
private fun StepFloatingCard(
    step: CoachmarkStep,
    currentStepIndex: Int,
    totalSteps: Int,
    enterAlpha: Float,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    cardCornerRadius: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Surface(
        modifier = modifier.graphicsLayer { alpha = enterAlpha },
        shape = RoundedCornerShape(cardCornerRadius),
        color = colorScheme.surfaceContainerHigh,
        tonalElevation = dimensionResource(R.dimen.gm_spacing_2),
        shadowElevation = dimensionResource(R.dimen.gm_spacing_3),
    ) {
        Column(
            modifier = Modifier.padding(dimensionResource(R.dimen.gm_spacing_4)),
            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.gm_spacing_2)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = step.title,
                    style = typography.titleLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                // 跳过按钮
                OutlinedButton(
                    onClick = onSkip,
                    contentPadding = PaddingValues(
                        horizontal = dimensionResource(R.dimen.gm_spacing_2),
                        vertical = dimensionResource(R.dimen.gm_spacing_1),
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "跳过引导",
                        modifier = Modifier.size(dimensionResource(R.dimen.gm_spacing_4)),
                    )
                    Spacer(Modifier.width(dimensionResource(R.dimen.gm_spacing_1)))
                    Text(
                        text = "跳过",
                        style = typography.labelLarge,
                    )
                }
            }

            Text(
                text = step.description,
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(dimensionResource(R.dimen.gm_spacing_1)))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // 步骤指示器点
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.gm_spacing_1)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(totalSteps) { index ->
                        val isActive = index == currentStepIndex
                        val dotSize = if (isActive) {
                            dimensionResource(R.dimen.gm_spacing_2)
                        } else {
                            dimensionResource(R.dimen.gm_spacing_1)
                        }
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .background(
                                    color = if (isActive) colorScheme.primary else colorScheme.outline,
                                    shape = CircleShape,
                                )
                        )
                    }
                }

                Button(
                    onClick = onNext,
                    contentPadding = PaddingValues(
                        horizontal = dimensionResource(R.dimen.gm_spacing_4),
                        vertical = dimensionResource(R.dimen.gm_spacing_2),
                    ),
                ) {
                    val isLast = currentStepIndex == totalSteps - 1
                    Text(
                        text = if (isLast) "完成" else "下一步",
                        style = typography.labelLarge,
                    )
                    Spacer(Modifier.width(dimensionResource(R.dimen.gm_spacing_1)))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(dimensionResource(R.dimen.gm_spacing_4)),
                    )
                }
            }
        }
    }
}
