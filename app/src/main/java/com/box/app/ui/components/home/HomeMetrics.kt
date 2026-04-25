package com.box.app.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.box.app.utils.ThemeManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.box.app.ui.theme.AppFonts
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.box.app.R
import java.util.Locale
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun HomeTwoColumnGrid(
    models: List<HomeCardModel>,
    modifier: Modifier = Modifier,
    columns: Int = 2
) {
    val columnCount = columns.coerceAtLeast(1)
    val rows = remember(models, columnCount) { models.chunked(columnCount) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    model = row[0],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                if (row.size > 1) {
                    MetricCard(
                        model = row[1],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                repeat((columnCount - row.size).coerceAtLeast(0)) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(model: HomeCardModel, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = metricCardContainerColor(model.kind)),
        onClick = model.onClick,
        showIndication = model.onClick != null,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        val palette = metricPalette(model)
        if (model.kind == HomeMetricKind.System) {
            SystemMetricCardContent(model = model)
        } else if (model.kind == HomeMetricKind.Subscription) {
            SubscriptionMetricCardContent(model = model)
        } else if (model.kind == HomeMetricKind.Speed) {
            SpeedMetricCardContent(model = model)
        } else if (model.kind == HomeMetricKind.Ip) {
            IpMetricCardContent(model = model)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.title,
                            style = MiuixTheme.textStyles.button,
                            color = palette.titleColor,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBadge(
                            kind = model.kind,
                            accent = model.accent,
                            overrideText = model.badgeText,
                            containerColor = palette.badgeContainer,
                            textColor = palette.badgeText
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    val animatedValueColor by animateColorAsState(
                        targetValue = metricValueColor(model),
                        animationSpec = tween(durationMillis = 360),
                        label = "metric_value_${model.kind}"
                    )
                    Text(
                        text = model.value,
                        style = if (model.kind == HomeMetricKind.Ip) {
                            MiuixTheme.textStyles.title4
                        } else {
                            MiuixTheme.textStyles.title2
                        },
                        fontFamily = AppFonts.dataFamily,
                        fontWeight = FontWeight.Medium,
                        color = if (model.kind == HomeMetricKind.Ip) palette.valueColor else animatedValueColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (model.subtitle.isNotBlank()) {
                        Text(
                            text = model.subtitle,
                            style = MiuixTheme.textStyles.body2,
                            color = palette.supportingColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (model.progress != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = model.progress.coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedRectangle(6.dp)),
                            colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = model.accent,
                                backgroundColor = palette.progressTrack
                            )
                        )
                    }
                }

                if (model.onCornerAction != null && model.cornerActionIcon != null) {
                    Icon(
                            imageVector = model.cornerActionIcon,
                            contentDescription = null,
                            tint = palette.supportingColor,
                            modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = 2.dp)
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                model.onCornerAction.invoke()
                            }
                    )
                }
            }
        }
    }
}

private fun splitMetricSubtitle(raw: String): Pair<String, String>? {
    val text = raw.trim()
    if (text.isBlank()) return null
    val separators = listOf('：', ':')
    val index = separators
        .map { sep -> text.indexOf(sep) }
        .firstOrNull { it > 0 }
        ?: return null
    val label = text.substring(0, index).trim()
    val value = text.substring(index + 1).trim()
    return if (label.isBlank() || value.isBlank()) null else label to value
}

/**
 * IP 卡片 — 仿参考设计
 * ┌───────────────────┐
 * │ IP           WAN  │  ← 标题(灰) + badge(灰)
 * │                   │
 * │ 104.28.210.91     │  ← 大号 IP 值
 * │ 地区: Hong Kong   │  ← 底部信息(灰)
 * └───────────────────┘
 */
@Composable
private fun IpMetricCardContent(model: HomeCardModel) {
    val palette = metricPalette(model)
    val detail = remember(model.subtitle) { splitMetricSubtitle(model.subtitle) }
    val ipValue = model.value.trim()
    val isIpv6 = ipValue.contains(':')
    val ipTextStyle = when {
        isIpv6 -> MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, lineHeight = 17.sp)
        ipValue.length >= 15 -> MiuixTheme.textStyles.title4.copy(fontSize = 15.sp, lineHeight = 18.sp)
        ipValue.length >= 13 -> MiuixTheme.textStyles.title4.copy(fontSize = 16.sp, lineHeight = 19.sp)
        else -> MiuixTheme.textStyles.title4.copy(fontSize = 19.sp, lineHeight = 22.sp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 顶部：IP (左) + WAN/LAN badge (右)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = model.title,
                style = MiuixTheme.textStyles.footnote1,
                color = palette.supportingColor,
                modifier = Modifier.weight(1f)
            )
            if (!model.badgeText.isNullOrBlank()) {
                Text(
                    text = model.badgeText,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = palette.supportingColor
                )
            }
        }

        // 数据区（紧贴标题下方）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 大号 IP 值
            SingleLineAdaptiveMetricText(
                text = ipValue,
                enableMarquee = isIpv6,
                fadeEdgeWidth = 10.dp,
                modifier = Modifier.fillMaxWidth(),
                style = ipTextStyle,
                fontFamily = AppFonts.dataFamily,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            // 地区信息
            if (detail != null) {
                Text(
                    text = "${detail.first}: ${detail.second}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = palette.supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SingleLineAdaptiveMetricText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    color: Color,
    modifier: Modifier = Modifier,
    enableMarquee: Boolean = false,
    fadeEdgeWidth: Dp = 10.dp
) {
    val textMeasurer = rememberTextMeasurer()
    var containerWidth by remember { mutableIntStateOf(0) }
    val mergedStyle = style.copy(
        fontFamily = fontFamily ?: style.fontFamily,
        fontWeight = fontWeight ?: style.fontWeight
    )
    val measuredWidth = remember(text, mergedStyle) {
        textMeasurer.measure(text = text, style = mergedStyle, maxLines = 1).size.width
    }
    val needsMarquee = enableMarquee && containerWidth in 1..<measuredWidth

    Text(
        text = text,
        modifier = modifier
            .onSizeChanged { containerWidth = it.width }
            .then(
                if (needsMarquee) {
                    Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val fadeWidth = fadeEdgeWidth.toPx()
                            if (fadeWidth > 0f && size.width > fadeWidth * 2f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Transparent,
                                        fadeWidth / size.width to Color.Black,
                                        1f - fadeWidth / size.width to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                        }
                        .basicMarquee(
                            iterations = Int.MAX_VALUE,
                            repeatDelayMillis = 3000,
                            initialDelayMillis = 1800,
                            velocity = 28.dp
                        )
                } else {
                    Modifier
                }
            ),
        style = mergedStyle,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (needsMarquee) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

private fun extractSpeedValue(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return "-"
    val idx = text.indexOf(' ')
    return if (idx in 1 until text.lastIndex) text.substring(idx + 1).trim().ifBlank { text } else text
}

private fun formatSpeedValue(raw: String): String {
    val text = raw.trim()
    if (text.isBlank() || text == "-" || text == "—") return "-"

    val pattern = Regex("""^([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?)(?:i)?B/s$""", RegexOption.IGNORE_CASE)
    val m = pattern.find(text) ?: return text
    val value = m.groupValues[1].toFloatOrNull() ?: return text
    val unit = m.groupValues[2].uppercase(Locale.US)
    val number = String.format(Locale.US, "%.1f", value)
    return if (unit.isBlank()) "${number}B/s" else "${number}${unit}B/s"
}

/**
 * 网速卡片 — 仿参考设计
 * ┌───────────────────┐
 * │ 网速              │  ← 标题(灰)
 * │    ╱ 背景图表 ╲   │
 * │ 上行    128 KB/s  │  ← label(灰) + value(右)
 * │ 下行    2.3 MB/s  │
 * └───────────────────┘
 */
@Composable
private fun SpeedMetricCardContent(model: HomeCardModel) {
    val upColor = MiuixTheme.colorScheme.primary
    val downColor = homeSuccessColors().accent
    val palette = metricPalette(model)
    val upValue = remember(model.subtitle) { formatSpeedValue(extractSpeedValue(model.subtitle)) }
    val downValue = remember(model.value) { formatSpeedValue(model.value) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // 背景实时图表
        if (model.sparkDown != null && model.sparkUp != null) {
            SpeedSparkline(
                downSeries = model.sparkDown,
                upSeries = model.sparkUp,
                downColor = downColor,
                upColor = upColor,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 前景内容
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题
            Text(
                text = model.title,
                style = MiuixTheme.textStyles.footnote1,
                color = palette.supportingColor
            )

            // 上行：label 左 + value 右
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_speed_upload_label),
                    style = MiuixTheme.textStyles.footnote2,
                    color = palette.supportingColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = upValue,
                    style = MiuixTheme.textStyles.body2,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            // 下行：label 左 + value 右
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_speed_download_label),
                    style = MiuixTheme.textStyles.footnote2,
                    color = palette.supportingColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = downValue,
                    style = MiuixTheme.textStyles.body2,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 订阅卡片 — 仿参考设计
 * ┌───────────────────┐
 * │ 订阅    剩余 76%  │  ← 标题(灰) + 剩余百分比(右,灰绿)
 * │                   │
 * │ 23.4 GB           │  ← 超大已用量
 * │ 总量 100 GB       │  ← 底部总量(灰)
 * └───────────────────┘
 */
/**
 * 订阅卡片 — 层次化水波背景
 *
 * 已用量从底部向上填充，上方透明区域为剩余量。
 * 渲染层级（从底到顶）：
 * 1. 水体深度渐变（底部深色 → 顶部浅色，表现水体厚度）
 * 2. 后浪（振幅大、频率低、最浅色）
 * 3. 中浪（振幅中、干涉频率、中色）
 * 4. 前浪（振幅小、频率高、最深色）
 * 5. 水面高光线（前浪顶点的细亮线）
 *
 * 每层浪使用双正弦叠加产生自然非周期性运动。
 */
@Composable
private fun SubscriptionMetricCardContent(model: HomeCardModel) {
    val palette = metricPalette(model)
    val remainHint = model.badgeText
    val dark = ThemeManager.shouldUseDarkTheme()

    // ── 颜色阶梯（从浅到深，层次分明） ──
    val surface = MiuixTheme.colorScheme.surfaceContainer
    val accent = model.accent
    // 水体主色（深度渐变底色）
    val waterDeep = lerp(surface, accent, if (dark) 0.22f else 0.14f)
    val waterShallow = lerp(surface, accent, if (dark) 0.10f else 0.06f)
    // 三层波浪色（浅 → 中 → 深）
    val waveBackColor = lerp(surface, accent, if (dark) 0.16f else 0.10f)
    val waveMidColor = lerp(surface, accent, if (dark) 0.22f else 0.14f)
    val waveFrontColor = lerp(surface, accent, if (dark) 0.30f else 0.20f)
    // 水面高光
    val highlightColor = lerp(surface, accent, if (dark) 0.45f else 0.30f)

    // ── 水位弹簧动画（柔和，带一点回弹） ──
    val animatedProgress by animateFloatAsState(
        targetValue = (model.progress ?: 0f).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 90f),
        label = "subscription_water_level"
    )

    // ── 基于帧时间的连续相位（永不重置，避免循环处跳帧） ──
    // 使用 Double 维持精度，sin() 会自然对 2π 取模
    val phaseSeconds by produceState(0.0) {
        val startFrame = withFrameNanos { it }
        while (true) {
            val currentFrame = withFrameNanos { it }
            value = (currentFrame - startFrame) / 1_000_000_000.0
        }
    }
    // 三层波浪使用不同角速度（rad/s），连续时间流，无重置
    // 周期 = 2π / 角速度，对应：2.4s / 3.4s / 4.6s
    val omegaFront = 2.0 * kotlin.math.PI / 2.4
    val omegaMid = 2.0 * kotlin.math.PI / 3.4
    val omegaBack = 2.0 * kotlin.math.PI / 4.6
    val phaseFront = (phaseSeconds * omegaFront).toFloat()
    val phaseMid = (phaseSeconds * omegaMid).toFloat()
    val phaseBack = (phaseSeconds * omegaBack).toFloat()

    Box(modifier = Modifier.fillMaxWidth()) {
        // ── 水位 + 多层波浪背景 ──
        if (animatedProgress > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val waterY = h * (1f - animatedProgress)
                val pi2 = 2f * kotlin.math.PI.toFloat()

                // 波浪振幅（按层级递减）
                val ampFront = h * 0.022f
                val ampMid = h * 0.016f
                val ampBack = h * 0.028f
                val steps = 80

                // 双正弦叠加：主波 + 副波，产生更自然的非周期性运动
                fun waveY(
                    baseY: Float,
                    x: Float,
                    amplitude: Float,
                    freq1: Float,
                    freq2: Float,
                    phase: Float,
                    phaseOffset: Float = 0f
                ): Float {
                    val main = kotlin.math.sin(pi2 * freq1 * x / w + phase)
                    val sub = 0.35f * kotlin.math.sin(pi2 * freq2 * x / w + phase * 1.3f + phaseOffset)
                    return baseY + amplitude * (main + sub)
                }

                fun buildWavePath(
                    amplitude: Float,
                    freq1: Float,
                    freq2: Float,
                    phase: Float,
                    phaseOffset: Float = 0f
                ): Path = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, waveY(waterY, 0f, amplitude, freq1, freq2, phase, phaseOffset))
                    for (i in 1..steps) {
                        val x = w * i / steps
                        lineTo(x, waveY(waterY, x, amplitude, freq1, freq2, phase, phaseOffset))
                    }
                    lineTo(w, h)
                    close()
                }

                // ── 1. 水体深度渐变（底层，给水体厚度感） ──
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(waterShallow, waterDeep),
                        startY = waterY,
                        endY = h
                    ),
                    topLeft = Offset(0f, (waterY - ampBack * 1.5f).coerceAtLeast(0f)),
                    size = androidx.compose.ui.geometry.Size(
                        w,
                        (h - waterY + ampBack * 1.5f).coerceAtLeast(0f)
                    )
                )

                // ── 2. 后浪（最浅，最大振幅，最慢） ──
                drawPath(
                    path = buildWavePath(
                        amplitude = ampBack,
                        freq1 = 1.0f,
                        freq2 = 2.3f,
                        phase = phaseBack,
                        phaseOffset = 0.8f
                    ),
                    color = waveBackColor.copy(alpha = 0.7f)
                )

                // ── 3. 中浪（中等色，干涉频率） ──
                drawPath(
                    path = buildWavePath(
                        amplitude = ampMid,
                        freq1 = 1.5f,
                        freq2 = 3.1f,
                        phase = phaseMid,
                        phaseOffset = 1.5f
                    ),
                    color = waveMidColor.copy(alpha = 0.75f)
                )

                // ── 4. 前浪（最深，最小振幅，最快） ──
                val frontPath = buildWavePath(
                    amplitude = ampFront,
                    freq1 = 2.0f,
                    freq2 = 4.2f,
                    phase = phaseFront,
                    phaseOffset = 0.3f
                )
                drawPath(path = frontPath, color = waveFrontColor)

                // ── 5. 水面高光线（前浪顶部的细亮线，增强立体感） ──
                val highlightPath = Path()
                val firstY = waveY(waterY, 0f, ampFront, 2.0f, 4.2f, phaseFront, 0.3f)
                highlightPath.moveTo(0f, firstY)
                for (i in 1..steps) {
                    val x = w * i / steps
                    highlightPath.lineTo(
                        x,
                        waveY(waterY, x, ampFront, 2.0f, 4.2f, phaseFront, 0.3f)
                    )
                }
                drawPath(
                    path = highlightPath,
                    color = highlightColor.copy(alpha = 0.45f),
                    style = Stroke(width = 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // ── 6. 底部光泽（微弱的向下渐亮，模拟水底反射） ──
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, waveFrontColor.copy(alpha = 0.15f)),
                        startY = h * 0.75f,
                        endY = h
                    ),
                    topLeft = Offset(0f, h * 0.75f),
                    size = androidx.compose.ui.geometry.Size(w, h * 0.25f)
                )
            }
        }

        // ── 前景内容 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.title,
                    style = MiuixTheme.textStyles.footnote1,
                    color = palette.supportingColor,
                    modifier = Modifier.weight(1f)
                )
                if (!remainHint.isNullOrBlank()) {
                    Text(
                        text = remainHint,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = palette.supportingColor
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = model.value,
                    style = MiuixTheme.textStyles.title2,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (model.subtitle.isNotBlank()) {
                    Text(
                        text = "${stringResource(R.string.home_subscription_total_label)} ${model.subtitle}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = palette.supportingColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun parseCpuPercent(text: String): Float? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*%""").find(text) ?: return null
    return match.groupValues.getOrNull(1)?.toFloatOrNull()
}

private fun parseRamMb(text: String): Float? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*MB""", RegexOption.IGNORE_CASE).find(text) ?: return null
    return match.groupValues.getOrNull(1)?.toFloatOrNull()
}

private fun ramDisplayCeilingMb(ramMb: Float?): Int {
    val v = (ramMb ?: 0f).coerceAtLeast(0f)
    return when {
        v <= 64f -> 128
        v <= 128f -> 256
        v <= 256f -> 512
        v <= 512f -> 1024
        v <= 1024f -> 2048
        else -> 4096
    }
}

/**
 * 资源占用卡片 — 仿参考设计
 * ┌───────────────────┐
 * │ 资源占用          │  ← 标题(灰)
 * │ CPU     12.8%     │  ← label(灰) + value(右)
 * │ 内存 382M/1024MB ◎│  ← label+value + 圆形进度环(右下)
 * └───────────────────┘
 */
@Composable
private fun SystemMetricCardContent(model: HomeCardModel) {
    val isActive = model.isActive
    val palette = metricPalette(model)
    val dark = ThemeManager.shouldUseDarkTheme()

    val cpuPercent = remember(model.value) { parseCpuPercent(model.value) }
    val ramMb = remember(model.subtitle) { parseRamMb(model.subtitle) }
    val ramCeiling = remember(ramMb) { ramDisplayCeilingMb(ramMb) }

    // CPU/RAM 可视化填充比例（0-1 截断，不代表真实值）
    val cpuFractionRaw = ((cpuPercent ?: 0f) / 100f).coerceIn(0f, 1f)
    val ramFractionRaw = ((ramMb ?: 0f) / ramCeiling.toFloat()).coerceIn(0f, 1f)
    val cpuFraction by animateFloatAsState(
        targetValue = cpuFractionRaw,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 110f),
        label = "system_cpu_fraction"
    )
    val ramFraction by animateFloatAsState(
        targetValue = ramFractionRaw,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 110f),
        label = "system_ram_fraction"
    )

    // 颜色：CPU 按阈值 success→warning→danger，RAM 固定 info；与订阅卡(tertiary)不重复
    val cpuAccent = when {
        (cpuPercent ?: 0f) >= 100f -> homeDangerColors().accent
        (cpuPercent ?: 0f) >= 85f -> homeWarningColors().accent
        else -> homeSuccessColors().accent
    }
    val ramAccent = homeInfoColors().accent
    val animCpuColor by animateColorAsState(
        targetValue = cpuAccent,
        animationSpec = tween(durationMillis = 360),
        label = "cpu_accent"
    )
    val animRamColor by animateColorAsState(
        targetValue = ramAccent,
        animationSpec = tween(durationMillis = 360),
        label = "ram_accent"
    )

    // 共享的连续时间源（永不重置，避免跳帧）
    val phaseSeconds by produceState(0.0) {
        val startFrame = withFrameNanos { it }
        while (true) {
            val currentFrame = withFrameNanos { it }
            value = (currentFrame - startFrame) / 1_000_000_000.0
        }
    }

    val surface = MiuixTheme.colorScheme.surfaceContainer

    Box(modifier = Modifier.fillMaxWidth()) {
        // ── 双层水位背景：全宽叠加，半透明自然混色 ──
        if (isActive) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 底层：较高的水位先画（视觉上作为"水体基底"）
                // 顶层：较低的水位半透明叠加，重叠区自然混色
                val cpuHigher = cpuFraction >= ramFraction
                val bottomLayerFraction = if (cpuHigher) cpuFraction else ramFraction
                val bottomLayerColor = if (cpuHigher) animCpuColor else animRamColor
                val bottomPeriods = if (cpuHigher)
                    Triple(2.6, 3.6, 4.8) else Triple(2.3, 3.3, 4.4)
                val bottomTimeOffset = if (cpuHigher) 0.0 else 0.7

                val topLayerFraction = if (cpuHigher) ramFraction else cpuFraction
                val topLayerColor = if (cpuHigher) animRamColor else animCpuColor
                val topPeriods = if (cpuHigher)
                    Triple(2.3, 3.3, 4.4) else Triple(2.6, 3.6, 4.8)
                val topTimeOffset = if (cpuHigher) 0.7 else 0.0

                // 底层：完整不透明
                drawSystemWaterFill(
                    fraction = bottomLayerFraction,
                    tint = bottomLayerColor,
                    surface = surface,
                    dark = dark,
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    time = phaseSeconds + bottomTimeOffset,
                    periodFront = bottomPeriods.first,
                    periodMid = bottomPeriods.second,
                    periodBack = bottomPeriods.third,
                    alpha = 1f
                )
                // 顶层：半透明叠加，与底层混色
                drawSystemWaterFill(
                    fraction = topLayerFraction,
                    tint = topLayerColor,
                    surface = surface,
                    dark = dark,
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    time = phaseSeconds + topTimeOffset,
                    periodFront = topPeriods.first,
                    periodMid = topPeriods.second,
                    periodBack = topPeriods.third,
                    alpha = 0.55f
                )
            }
        }

        // ── 前景内容：标题顶部 + 数据行底部（与 IP/订阅卡视觉重心一致） ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = model.title,
                style = MiuixTheme.textStyles.footnote1,
                color = palette.supportingColor
            )

            if (isActive) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // CPU：显示真实值（可能 >100%），视觉填充已截断
                    val cpuText = cpuPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"
                    val ramText = ramMb?.let {
                        "${String.format(Locale.US, "%.0f", it)} / ${ramCeiling}MB"
                    } ?: "--"

                    SystemMetricRow(
                        label = "CPU",
                        value = cpuText,
                        labelColor = palette.supportingColor,
                        valueColor = MiuixTheme.colorScheme.onSurface
                    )
                    SystemMetricRow(
                        label = stringResource(R.string.home_system_ram_label),
                        value = ramText,
                        labelColor = palette.supportingColor,
                        valueColor = MiuixTheme.colorScheme.onSurface
                    )
                }
            } else {
                SystemMetricDisabledHint()
            }
        }
    }
}

/**
 * 系统卡专用水位填充：在指定矩形区域内绘制多层波浪。
 *
 * 支持 alpha 参数以实现双层叠加混色（底层 alpha=1，顶层 alpha<1）。
 * 顶层半透明时，未被底层覆盖区域直接显示顶层色；与底层重叠区域
 * 由 Compose 的标准 SrcOver 混合产生自然过渡色。
 */
private fun DrawScope.drawSystemWaterFill(
    fraction: Float,
    tint: Color,
    surface: Color,
    dark: Boolean,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    time: Double,
    periodFront: Double,
    periodMid: Double,
    periodBack: Double,
    alpha: Float = 1f
) {
    if (fraction <= 0f || alpha <= 0f) return
    val rectW = right - left
    val rectH = bottom - top
    val waterY = top + rectH * (1f - fraction)
    val pi2 = 2.0 * kotlin.math.PI
    val phaseFront = (time * pi2 / periodFront).toFloat()
    val phaseMid = (time * pi2 / periodMid).toFloat()
    val phaseBack = (time * pi2 / periodBack).toFloat()

    // 五级色阶（与订阅卡同机制，不同 accent）
    val waterDeep = lerp(surface, tint, if (dark) 0.22f else 0.14f)
    val waterShallow = lerp(surface, tint, if (dark) 0.10f else 0.06f)
    val backC = lerp(surface, tint, if (dark) 0.16f else 0.10f)
    val midC = lerp(surface, tint, if (dark) 0.22f else 0.14f)
    val frontC = lerp(surface, tint, if (dark) 0.30f else 0.20f)

    val ampFront = rectH * 0.022f
    val ampMid = rectH * 0.016f
    val ampBack = rectH * 0.028f
    val steps = 40
    val pi2f = pi2.toFloat()

    fun waveY(x: Float, amp: Float, f1: Float, f2: Float, phase: Float, off: Float): Float {
        val nx = (x - left) / rectW
        val main = kotlin.math.sin(pi2f * f1 * nx + phase)
        val sub = 0.35f * kotlin.math.sin(pi2f * f2 * nx + phase * 1.3f + off)
        return waterY + amp * (main + sub)
    }

    fun buildPath(amp: Float, f1: Float, f2: Float, phase: Float, off: Float): Path = Path().apply {
        moveTo(left, bottom)
        lineTo(left, waveY(left, amp, f1, f2, phase, off))
        for (i in 1..steps) {
            val x = left + rectW * i / steps
            lineTo(x, waveY(x, amp, f1, f2, phase, off))
        }
        lineTo(right, bottom)
        close()
    }

    clipRect(left = left, top = top, right = right, bottom = bottom) {
        // 1. 水体深度渐变
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    waterShallow.copy(alpha = waterShallow.alpha * alpha),
                    waterDeep.copy(alpha = waterDeep.alpha * alpha)
                ),
                startY = waterY,
                endY = bottom
            ),
            topLeft = Offset(left, (waterY - ampBack * 1.5f).coerceAtLeast(top)),
            size = androidx.compose.ui.geometry.Size(
                rectW,
                (bottom - waterY + ampBack * 1.5f).coerceAtLeast(0f)
            )
        )
        // 2-4. 三层波浪
        drawPath(buildPath(ampBack, 1.0f, 2.3f, phaseBack, 0.8f), backC.copy(alpha = 0.7f * alpha))
        drawPath(buildPath(ampMid, 1.5f, 3.1f, phaseMid, 1.5f), midC.copy(alpha = 0.75f * alpha))
        drawPath(buildPath(ampFront, 2.0f, 4.2f, phaseFront, 0.3f), frontC.copy(alpha = alpha))
    }
}

/** 系统指标行：label 左对齐，value 右对齐 */
@Composable
private fun SystemMetricRow(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = labelColor
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MiuixTheme.textStyles.footnote2,
            fontFamily = AppFonts.dataFamily,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SystemMetricDisabledHint() {
    val palette = metricPaletteForKind(
        kind = HomeMetricKind.System,
        accent = MiuixTheme.colorScheme.secondary,
        isActive = false
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(10.dp))
            .background(palette.badgeContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.home_system_disabled_hint),
            style = MiuixTheme.textStyles.body2,
            color = palette.supportingColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SystemMetricBar(
    label: String,
    valueText: String,
    progress: Float,
    color: Color,
    trackColor: Color,
    rightHint: String? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = valueText,
                style = MiuixTheme.textStyles.footnote1,
                fontFamily = AppFonts.dataFamily,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (!rightHint.isNullOrBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = rightHint,
                    style = MiuixTheme.textStyles.footnote2,
                    fontFamily = AppFonts.dataFamily,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1
                )
            }
        }

        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedRectangle(6.dp)),
            colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = color,
                backgroundColor = trackColor
            )
        )
    }
}

@Composable
fun MetricBadge(
    kind: HomeMetricKind,
    accent: Color,
    modifier: Modifier = Modifier,
    overrideText: String? = null,
    containerColor: Color? = null,
    textColor: Color? = null
) {
    val text = overrideText ?: when (kind) {
        HomeMetricKind.Service -> stringResource(R.string.home_badge_service)
        HomeMetricKind.Ip -> stringResource(R.string.home_badge_ip)
        HomeMetricKind.Speed -> stringResource(R.string.home_badge_net)
        HomeMetricKind.Latency -> stringResource(R.string.home_badge_ms)
        HomeMetricKind.Subscription -> stringResource(R.string.home_badge_sub)
        HomeMetricKind.System -> stringResource(R.string.home_badge_sys)
    }

    val palette = metricBadgePalette(kind = kind, accent = accent)

    Card(
        modifier = modifier
            .clip(Capsule()),
        cornerRadius = 999.dp,
        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
        colors = CardDefaults.defaultColors(color = containerColor ?: palette.first)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.SemiBold,
            color = textColor ?: palette.second
        )
    }
}

/**
 * 实时网速图表：直接绘制最新数据，仅 Y 轴比例平滑过渡。
 *
 * 渲染层级（从底到顶）：
 * 1. 下载填充渐变（较深）
 * 2. 上传填充渐变（较浅）
 * 3. 下载描边光晕 + 实线
 * 4. 上传描边光晕 + 实线
 * 5. 左右边缘水平淡出遮罩
 *
 * 交叉区域两条线均清晰可见，不会相互遮盖。
 */
@Composable
fun SpeedSparkline(
    downSeries: List<Float>,
    upSeries: List<Float>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier
) {
    val rawMax = ((downSeries + upSeries).maxOrNull() ?: 0f).coerceAtLeast(1f)
    val animMaxV by animateFloatAsState(
        targetValue = rawMax,
        animationSpec = tween(durationMillis = 800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "sparkline_max"
    )
    // 提前取颜色（Canvas DrawScope 中不能访问 @Composable）
    val edgeFadeColor = MiuixTheme.colorScheme.surfaceContainer

    Canvas(
        modifier = modifier
            .graphicsLayer {
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val topMargin = h * 0.10f
        val bottomMargin = h * 0.04f
        val chartH = h - topMargin - bottomMargin

        // ── 路径构建 ──

        fun buildSmoothPath(series: List<Float>): Path {
            val p = Path()
            if (series.isEmpty()) return p
            val n = series.size
            fun point(i: Int): Offset {
                val x = if (n == 1) w / 2f else (i.toFloat() / (n - 1)) * w
                val v = (series[i] / animMaxV).coerceIn(0f, 1f)
                val y = topMargin + chartH * (1f - v)
                return Offset(x, y)
            }
            p.moveTo(point(0).x, point(0).y)
            if (n == 1) return p
            for (i in 1 until n) {
                val prev = point(i - 1)
                val cur = point(i)
                val cpX = (prev.x + cur.x) / 2f
                p.cubicTo(cpX, prev.y, cpX, cur.y, cur.x, cur.y)
            }
            return p
        }

        fun buildFillPath(series: List<Float>): Path {
            val stroke = buildSmoothPath(series)
            if (series.isEmpty()) return stroke
            val n = series.size
            val lastX = if (n == 1) w / 2f else w
            return Path().apply {
                addPath(stroke)
                lineTo(lastX, h)
                lineTo(0f, h)
                close()
            }
        }

        // 缓存路径（避免重复构建）
        val downStroke = buildSmoothPath(downSeries)
        val upStroke = buildSmoothPath(upSeries)
        val downFill = buildFillPath(downSeries)
        val upFill = buildFillPath(upSeries)

        // ── Layer 1 & 2：填充渐变（所有填充在描边之下） ──

        // 下载填充：三阶渐变，更有层次
        drawPath(
            path = downFill,
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to downColor.copy(alpha = 0.30f),
                    0.5f to downColor.copy(alpha = 0.12f),
                    1.0f to Color.Transparent
                ),
                startY = topMargin, endY = h
            ),
            style = Fill
        )

        // 上传填充：三阶渐变，比下载更透
        drawPath(
            path = upFill,
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to upColor.copy(alpha = 0.20f),
                    0.5f to upColor.copy(alpha = 0.08f),
                    1.0f to Color.Transparent
                ),
                startY = topMargin, endY = h
            ),
            style = Fill
        )

        // ── Layer 3 & 4：描边（所有描边在填充之上，交叉区双线清晰） ──

        // 下载描边光晕（宽、低透明度）
        drawPath(
            path = downStroke,
            color = downColor.copy(alpha = 0.18f),
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // 下载描边实线
        drawPath(
            path = downStroke,
            color = downColor.copy(alpha = 0.85f),
            style = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 上传描边光晕
        drawPath(
            path = upStroke,
            color = upColor.copy(alpha = 0.14f),
            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // 上传描边实线
        drawPath(
            path = upStroke,
            color = upColor.copy(alpha = 0.75f),
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ── Layer 5：左右边缘水平淡出遮罩 ──
        val fadeWidth = w * 0.06f
        // 左侧淡入
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(edgeFadeColor, Color.Transparent),
                startX = 0f,
                endX = fadeWidth
            ),
            size = size
        )
        // 右侧淡出
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, edgeFadeColor),
                startX = w - fadeWidth,
                endX = w
            ),
            size = size
        )
    }
}

@Composable
private fun metricCardContainerColor(kind: HomeMetricKind): Color = when (kind) {
    HomeMetricKind.Ip -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Speed -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Subscription -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.System -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Service -> metricPaletteForKind(kind).containerColor
    HomeMetricKind.Latency -> metricPaletteForKind(kind).containerColor
}

@Composable
private fun metricValueColor(model: HomeCardModel): Color = metricPalette(model).valueColor

private data class MetricPalette(
    val containerColor: Color,
    val titleColor: Color,
    val valueColor: Color,
    val supportingColor: Color,
    val badgeContainer: Color,
    val badgeText: Color,
    val progressTrack: Color
)

@Composable
private fun metricPalette(model: HomeCardModel): MetricPalette = metricPaletteForKind(
    kind = model.kind,
    accent = model.accent,
    isActive = model.isActive
)

@Composable
private fun metricPaletteForKind(
    kind: HomeMetricKind,
    accent: Color = MiuixTheme.colorScheme.primary,
    isActive: Boolean = true
): MetricPalette {
    val scheme = MiuixTheme.colorScheme
    val dark = ThemeManager.shouldUseDarkTheme()
    return MetricPalette(
        containerColor = scheme.surfaceContainer,
        titleColor = scheme.onSurface,
        valueColor = when (kind) {
            HomeMetricKind.Service, HomeMetricKind.Latency -> accent
            HomeMetricKind.Speed -> scheme.onSurface
            HomeMetricKind.System -> if (isActive) scheme.onSurface else scheme.onSurfaceSecondary
            else -> scheme.onSurface
        },
        supportingColor = scheme.onSurfaceSecondary,
        badgeContainer = metricBadgeContainerColor(kind, dark, isActive),
        badgeText = metricBadgeTextColor(kind, dark, isActive),
        progressTrack = scheme.surfaceContainerHighest
    )
}

// ── Badge 颜色：统一从 MiuixTheme.colorScheme 派生 ─────────────────────────
// scheme.primary 在 Monet ON/OFF 时已自动切换，无需手动判断。
// 所有 badge 均通过 lerp(surface, tint, ratio) 生成柔和容器色，避免过度鲜艳。

@Composable
private fun metricBadgeContainerColor(
    kind: HomeMetricKind,
    dark: Boolean,
    isActive: Boolean
): Color {
    val scheme = MiuixTheme.colorScheme
    val primary = scheme.primary
    val surface = scheme.surface
    val neutral = scheme.onSurfaceSecondary
    return when (kind) {
        // NET: 主色调容器 — 最醒目但仍柔和
        HomeMetricKind.Speed ->
            lerp(surface, primary, if (dark) 0.28f else 0.18f)
        // SUB / 延迟: 主色调容器（中等）
        HomeMetricKind.Subscription, HomeMetricKind.Latency ->
            lerp(surface, primary, if (dark) 0.20f else 0.14f)
        // IP: 主色调容器（偏淡）
        HomeMetricKind.Ip ->
            lerp(surface, primary, if (dark) 0.16f else 0.10f)
        // SYS / Service: 中性容器
        HomeMetricKind.System, HomeMetricKind.Service -> {
            val ratio = if (isActive) {
                if (dark) 0.12f else 0.08f
            } else {
                if (dark) 0.08f else 0.06f
            }
            lerp(surface, neutral, ratio)
        }
    }
}

@Composable
private fun metricBadgeTextColor(
    kind: HomeMetricKind,
    dark: Boolean,
    isActive: Boolean
): Color {
    val scheme = MiuixTheme.colorScheme
    val primary = scheme.primary
    return when (kind) {
        // NET: 主色文字
        HomeMetricKind.Speed -> primary
        // SUB / 延迟: 主色偏移（确保在柔和容器上可读）
        HomeMetricKind.Subscription, HomeMetricKind.Latency ->
            if (dark) lerp(primary, Color.White, 0.30f)
            else lerp(primary, Color.Black, 0.15f)
        // IP: 同上略淡
        HomeMetricKind.Ip ->
            if (dark) lerp(primary, Color.White, 0.35f)
            else lerp(primary, Color.Black, 0.20f)
        // SYS / Service: 中性文字
        HomeMetricKind.System, HomeMetricKind.Service ->
            if (isActive) scheme.onSurfaceSecondary
            else scheme.onSurfaceSecondary.copy(alpha = 0.55f)
    }
}

@Composable
private fun metricBadgePalette(kind: HomeMetricKind, accent: Color): Pair<Color, Color> {
    val palette = metricPaletteForKind(kind = kind, accent = accent)
    return palette.badgeContainer to palette.badgeText
}
