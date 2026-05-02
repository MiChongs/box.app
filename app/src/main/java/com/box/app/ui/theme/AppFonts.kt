package com.box.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.box.app.R

private val gmsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val geistMono = GoogleFont("Geist Mono")

/**
 * 数据展示专用字体族 — Geist Mono
 *
 * 选型理由（替换 IBM Plex Mono / JetBrains Mono）：
 *   - **现代极简几何风**：Vercel 2023 推出，是当代最具美感的等宽字体之一，
 *     字形几何感强、笔画干净、负空间均衡，与 HyperOS / miuix 设计语言高度契合
 *   - **数字辨识极佳**：0/O、1/l/I、5/S 等易混字符在 Geist Mono 中有刻意区分，
 *     IP / 端口 / 延迟 / 流量数字看起来不会眼花
 *   - **观感克制**：相比 IBM Plex Mono 的工业书写感、JetBrains Mono 的 hooks 装饰，
 *     Geist Mono 字形更冷静、更接近 SF Mono / iOS 系统等宽，
 *     用于数据卡片、徽章、统计面板时不抢戏，与 miuix 卡片视觉协调
 *   - **数字字宽统一**：等宽家族，多行数字严格对齐 — 兼容现有调用方
 *     (HomeMetrics / HomeLatencyCard / ProtocolBadge)
 *
 * 通过 GMS Downloadable Fonts 按需下载，无需打包字体文件。
 */
object AppFonts {
    val dataFamily: FontFamily = FontFamily(
        Font(googleFont = geistMono, fontProvider = gmsProvider, weight = FontWeight.Normal),
        Font(googleFont = geistMono, fontProvider = gmsProvider, weight = FontWeight.Medium),
        Font(googleFont = geistMono, fontProvider = gmsProvider, weight = FontWeight.SemiBold),
        Font(googleFont = geistMono, fontProvider = gmsProvider, weight = FontWeight.Bold)
    )
}
