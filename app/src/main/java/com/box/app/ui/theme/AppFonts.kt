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

private val ibmPlexMono = GoogleFont("IBM Plex Mono")

/**
 * 数据展示专用字体族 — IBM Plex Mono
 *
 * 选型理由（替换 JetBrains Mono）：
 *   - **专为数据可视化/科技仪表盘设计**：IBM 公司设计语言体系内的标准等宽字体，
 *     在金融、监控、运维等数据展示界面有最广泛的工业级落地
 *   - **极佳的数字辨识**：0/O、1/l/I、5/S、6/G 这些易混字符差异显著，
 *     避免读错 IP / 端口 / 延迟数字
 *   - **数字字宽统一**：与 JetBrains Mono 同为等宽家族，IP 地址、流量、毫秒、
 *     百分比上下行严格对齐 — 兼容现有调用方 (HomeMetrics / HomeLatencyCard)
 *   - **观感更克制中性**：JetBrains Mono 字体设计偏书写感（hooks/curves 多）
 *     用作 logo/代码片段不错，但贴在状态卡上略花哨；Plex Mono 几何感更强、
 *     更接近 HyperOS 系统字体的设计语言，与 miuix 视觉调性更协调
 *
 * 通过 GMS Downloadable Fonts 按需下载，无需打包字体文件。
 */
object AppFonts {
    val dataFamily: FontFamily = FontFamily(
        Font(googleFont = ibmPlexMono, fontProvider = gmsProvider, weight = FontWeight.Normal),
        Font(googleFont = ibmPlexMono, fontProvider = gmsProvider, weight = FontWeight.Medium),
        Font(googleFont = ibmPlexMono, fontProvider = gmsProvider, weight = FontWeight.SemiBold),
        Font(googleFont = ibmPlexMono, fontProvider = gmsProvider, weight = FontWeight.Bold)
    )
}
