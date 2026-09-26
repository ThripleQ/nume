package com.thripleq.nume.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * **机器生成，请勿手改** —— 由 `tools/gen_palette.py` 从单一 seed 推导。
 * 换品牌色：改脚本里 `SEED` 一行 + 重跑，本文件与所有调用方零改动。
 *
 * seed = #C92027（品牌红）｜tertiary 色相 = seed + 70°｜暗色 surface 锚 tone 20
 *
 * 色相与明度音阶取自 OKLCH（感知均匀，tone = L×100）；与 Material Theme Builder 的
 * HCT 产出不逐位相同，但**所有 on_*/ 正文色对都按 WCAG 相对亮度验证过 ≥4.5:1**
 * （次要文字 ≥3.0:1），且主次容器、主次文字均强制可区分。
 *
 * 三条刻意的设计选择（详见脚本文件头）：
 * 1. 浅色 primary 用品牌本色而非 M3 惯例 tone 40 —— 白字压品牌红实测 5.6:1，
 *    本就达 AA，压暗反而是去品牌化。
 * 2. 暗色 surface 锚现网明度（tone 20）而非 M3 的 tone 6，只把色相从冷蓝灰拧到品牌红调。
 * 3. on_* 先试 M3 规范 tone，不达标才修正 —— 保证次要文字不与主文字同色。
 *
 * 音阶留档（tone: RRGGBB）：
 *   primary          10:0D0000 20:320002 30:5C0007 40:8B000F 50:B71921 60:DB423F 70:FF645D 80:FFA097 90:FFD2CC 99:FFFBFA
 *   secondary        10:0D0000 20:270D0B 30:422522 40:5E3E3B 50:7B5A56 60:9A7672 70:B99490 80:D9B3AF 90:FBD3CF 99:FFFBFA
 *   tertiary         10:050300 20:1C1600 30:372D00 40:554700 50:756200 60:957F1B 70:B49E41 80:D4BD62 90:F5DE82 99:FFFCEF
 *   neutral          10:060202 20:1C1413 30:352B2A 40:4F4544 50:6B605F 60:887D7C 70:A79B9A 80:C7BAB9 90:E7DBD9 99:FFFBFA
 *   neutral_variant  10:090101 20:211110 30:3A2927 40:554340 50:725E5C 60:8F7B78 70:AE9996 80:CEB8B5 90:EFD8D5 99:FFFBFA
 *   error            10:0D0000 20:320001 30:5D0004 40:8B000B 50:B02B27 60:D24D45 70:F66D62 80:FFA196 90:FFD2CC 99:FFFBFA
 */

/** 一整套 M3 颜色角色；浅色 / 深色各一份实例，由 [NumeTheme] 映射进 colorScheme。 */
data class NumeColorRoles(
    val primary: Color, val onPrimary: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
    val secondary: Color, val onSecondary: Color,
    val secondaryContainer: Color, val onSecondaryContainer: Color,
    val tertiary: Color, val onTertiary: Color,
    val tertiaryContainer: Color, val onTertiaryContainer: Color,
    val error: Color, val onError: Color,
    val errorContainer: Color, val onErrorContainer: Color,
    val surface: Color, val onSurface: Color,
    val surfaceDim: Color, val surfaceBright: Color,
    val surfaceContainerLowest: Color, val surfaceContainerLow: Color,
    val surfaceContainer: Color, val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceVariant: Color, val onSurfaceVariant: Color,
    val outline: Color, val outlineVariant: Color,
    val inverseSurface: Color, val inverseOnSurface: Color,
    val inversePrimary: Color, val surfaceTint: Color,
)

// ── 浅色 ────────────────────────────────────────────────────────
val NumeLightColors = NumeColorRoles(
    primary = Color(0xFFC92027),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD2CC),
    onPrimaryContainer = Color(0xFF0D0000),
    secondary = Color(0xFF5E3E3B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFBD3CF),
    onSecondaryContainer = Color(0xFF0D0000),
    tertiary = Color(0xFF554700),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5DE82),
    onTertiaryContainer = Color(0xFF050300),
    error = Color(0xFF8B000B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD2CC),
    onErrorContainer = Color(0xFF0D0000),
    surface = Color(0xFFFFF6F5),
    onSurface = Color(0xFF060202),
    surfaceDim = Color(0xFFDDD1CF),
    surfaceBright = Color(0xFFFFF6F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBEEED),
    surfaceContainer = Color(0xFFF4E8E6),
    surfaceContainerHigh = Color(0xFFEEE1E0),
    surfaceContainerHighest = Color(0xFFE7DBD9),
    surfaceVariant = Color(0xFFEFD8D5),
    onSurfaceVariant = Color(0xFF3A2927),
    outline = Color(0xFF725E5C),
    outlineVariant = Color(0xFFCEB8B5),
    inverseSurface = Color(0xFF1C1413),
    inverseOnSurface = Color(0xFFF8EBEA),
    inversePrimary = Color(0xFFFFA097),
    surfaceTint = Color(0xFFC92027),
)

// ── 深色 ────────────────────────────────────────────────────────
val NumeDarkColors = NumeColorRoles(
    primary = Color(0xFFFFA097),
    onPrimary = Color(0xFF320002),
    primaryContainer = Color(0xFF5C0007),
    onPrimaryContainer = Color(0xFFFFD2CC),
    secondary = Color(0xFFD9B3AF),
    onSecondary = Color(0xFF270D0B),
    secondaryContainer = Color(0xFF422522),
    onSecondaryContainer = Color(0xFFFBD3CF),
    tertiary = Color(0xFFD4BD62),
    onTertiary = Color(0xFF1C1600),
    tertiaryContainer = Color(0xFF372D00),
    onTertiaryContainer = Color(0xFFF5DE82),
    error = Color(0xFFFFA196),
    onError = Color(0xFF320001),
    errorContainer = Color(0xFF5D0004),
    onErrorContainer = Color(0xFFFFD2CC),
    surface = Color(0xFF1C1413),
    onSurface = Color(0xFFE7DBD9),
    surfaceDim = Color(0xFF1C1413),
    surfaceBright = Color(0xFF352B2A),
    surfaceContainerLowest = Color(0xFF0A0404),
    surfaceContainerLow = Color(0xFF150D0C),
    surfaceContainer = Color(0xFF211817),
    surfaceContainerHigh = Color(0xFF261D1C),
    surfaceContainerHighest = Color(0xFF352B2A),
    surfaceVariant = Color(0xFF3A2927),
    onSurfaceVariant = Color(0xFFCEB8B5),
    outline = Color(0xFF8F7B78),
    outlineVariant = Color(0xFF3A2927),
    inverseSurface = Color(0xFFE7DBD9),
    inverseOnSurface = Color(0xFF1C1413),
    inversePrimary = Color(0xFF8B000F),
    surfaceTint = Color(0xFFFFA097),
)
