#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
nume 调色板生成器 —— 单一 seed 推导全套 M3 角色色，并用 WCAG 对比度逐对验证。

产物：app/src/main/java/com/thripleq/nume/ui/theme/Palette.kt（纯字面量，勿手改）

## 为什么是「生成器 + 字面量」而不是运行时算色
本仓库落到 Kotlin/Compose，但开发环境无 Android SDK、编译不了。把 HCT/OKLab 色彩引擎
移植进 Kotlin，一旦笔误只能在真机上以「颜色怪」的形式暴露，排查极贵。所以色彩数学全部
留在本脚本（可执行、可验证），Kotlin 侧只有常量。
换品牌色 = 改 SEED 一行 + 重跑，Kotlin 零改动。

## 三条不可动摇的原则（都是踩过坑定下来的）
1. **不偷改品牌色**：浅色 primary 锚到 seed 本色，而不是 M3 惯例的 tone 40。
   实测白字压 #C92027 = 5.6:1，本来就达 AA；压到 tone 40(#8B000F) 是纯粹的去品牌化。
   只有当品牌色亮到白字不达标时，resolve_on 才会为了无障碍把它压暗——那是被迫，不是惯例。
2. **不偷改明度**：暗色 surface 锚到现网实测档（#111214 ≈ tone 17 / #191A1C ≈ tone 22 →
   取 20），而不是 M3 规范的 tone 6(#020000)。跟tone 6 会把整个 App 静默调暗一大截。
   这次只把**色相**从冷蓝灰拧成品牌红调，明度基本不动。
3. **层级不许被打平**：on_* 先试 M3 规范 tone（次要文字用 30/80 而非极端 10/90），
   只有对比度不达标才向大反差方向修正。否则 on_surface_variant 会撞上 on_surface，
   主次文字同色 —— 那也是一种「不协调」。

## 色空间
OKLCH（感知均匀，L 直接当 M3 tone 用）。不是 Google 的 HCT/CAM16：那需要约 150 行
CAM16 实现，盲写风险不划算。代价是色值与 Material Theme Builder 不逐位相同，
但**色相协调关系与对比度**才是本项目要的，且二者都被本脚本严格验证。

超色域处理：保持 L、H 不变，二分收缩 C 直到入 sRGB 域（比直接 clamp RGB 干净得多）。

## 用法
    python3 tools/gen_palette.py            # 打印对比度验证报告
    python3 tools/gen_palette.py --write    # 另写入 Palette.kt
"""
from __future__ import annotations

import math
import sys
from pathlib import Path

# ══ 设计输入：改这里即可整体换色 ═══════════════════════════════════
SEED = "#C92027"            # 品牌红（沿用现有 primary，呼应网易云识别）
TERTIARY_HUE_OFFSET = 70     # 第三色旋转角：拉开层次但不同调打架
ERROR_HUE = 27               # 错误色固定色相（橙红），与品牌红可区分
DARK_SURFACE_TONE = 20       # 见文件头原则 2：锚现网明度，不套 M3 tone 6
CONTRAST_TARGET = 4.5         # WCAG AA 正文
CONTRAST_TARGET_SECONDARY = 3.0   # 次要文字/图标（M3 onSurfaceVariant 语义）

# 各音阶彩度（OKLCH C）。secondary / neutral 刻意压低：品牌感交给 primary/tertiary，
# 表面与正文保持中性——这是「协调」的关键，否则整屏都在抢话。
CHROMA = {
    "primary": 0.19,
    "secondary": 0.045,
    "tertiary": 0.115,
    "neutral": 0.014,
    "neutral_variant": 0.026,
    "error": 0.17,
}
TONES = [0, 4, 6, 10, 12, 17, 20, 22, 24, 30, 40, 50, 60,
         70, 80, 87, 90, 92, 94, 95, 96, 98, 99, 100]
# ═══════════════════════════════════════════════════════════════════


# ── sRGB ⇄ OKLab / OKLCH ─────────────────────────────────────────
def srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def linear_to_srgb(c: float) -> float:
    return c * 12.92 if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055


def oklch_to_linear(L: float, C: float, h_deg: float):
    """OKLCH → 线性 sRGB（Ottosson 正向矩阵）。"""
    h = math.radians(h_deg)
    a, b = C * math.cos(h), C * math.sin(h)
    l_ = L + 0.3963377774 * a + 0.2158037612 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    return (
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    )


def linear_to_oklab(lr: float, lg: float, lb: float):
    l, m, s = (
        0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb,
        0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb,
        0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb,
    )
    l_, m_, s_ = math.cbrt(l), math.cbrt(m), math.cbrt(s)
    return (
        0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
        1.9779984950 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
        0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_,
    )


def in_gamut(r: float, g: float, b: float) -> bool:
    e = 1e-6
    return -e <= r <= 1 + e and -e <= g <= 1 + e and -e <= b <= 1 + e


def oklch_to_hex(L: float, C: float, h_deg: float) -> str:
    """tone(0..100) + 彩度 + 色相 → 大写 RRGGBB；超域则保 L/H 二分收缩 C。"""
    if L <= 0:
        return "000000"
    if L >= 100:
        return "FFFFFF"
    Lo = L / 100.0
    r, g, b = oklch_to_linear(Lo, C, h_deg)
    if not in_gamut(r, g, b):
        lo, hi = 0.0, C
        for _ in range(24):
            mid = (lo + hi) / 2
            rr, gg, bb = oklch_to_linear(Lo, mid, h_deg)
            lo, hi = (mid, hi) if in_gamut(rr, gg, bb) else (lo, mid)
        r, g, b = oklch_to_linear(Lo, lo, h_deg)
    return "".join(f"{round(max(0.0, min(1.0, linear_to_srgb(v))) * 255):02X}"
                   for v in (r, g, b)).upper()


def hex_to_lch(color: str) -> tuple[float, float, float]:
    """#RRGGBB / RRGGBB → (L×100, C, H°)。前缀无关：踩过偏移错位的坑。"""
    h = color.lstrip("#")
    rgb = tuple(srgb_to_linear(int(h[i:i + 2], 16) / 255) for i in (0, 2, 4))
    Lo, A, B = linear_to_oklab(*rgb)
    return Lo * 100, math.hypot(A, B), math.degrees(math.atan2(B, A)) % 360


def rel_lum(color: str) -> float:
    """WCAG 相对亮度（与色调空间无关，故可独立验证对比度）。前缀无关。"""
    h = color.lstrip("#")
    lin = [srgb_to_linear(int(h[i:i + 2], 16) / 255) for i in (0, 2, 4)]
    return 0.2126 * lin[0] + 0.7152 * lin[1] + 0.0722 * lin[2]


def contrast(a: str, b: str) -> float:
    la, lb = rel_lum(a), rel_lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def build_ramp(hue: float, chroma: float) -> dict[int, str]:
    return {t: oklch_to_hex(float(t), chroma, hue) for t in TONES}


def resolve_on(ramp: dict[int, str], bg: str, preferred: int, target: float):
    """
    先试 M3 规范 tone（保住主次层级），不达标才在所有 tone 里取反差最大者
    （并列时取更接近规范值的）。返回 (hex, 是否达标)。
    无障碍优先于层级，但只在必要时牺牲层级。
    """
    if contrast(ramp[preferred], bg) >= target:
        return ramp[preferred], True
    best = max(sorted(ramp),
               key=lambda t: (contrast(ramp[t], bg), -abs(t - preferred)))
    return best, contrast(ramp[best], bg) >= target


# ── 角色 → (色板, 浅tone, 暗tone) 显式对照表 ────────────────────────
# 早期版本用「role 名里含 surface/container 就归到某色板」的字符串启发式，
# 结果 on_surface_variant 取错端、暗色 tertiary_container 对比度 1.5:1。显式表可读可审。
# 特殊标记：浅色 primary 直接用 SEED 本色（既不套 M3 tone 40，也不退到就近档）。
# 就近档曾把品牌红 #C92027 换成 tone 50 的 #B71921 —— 仍然偏暗、仍是改品牌色。
# 白字压 #C92027 实测 5.6:1 达 AA，所以没有理由不动它。
PRIMARY = "seed_color"
BASE_ROLES: dict[str, tuple[str, object, object]] = {
    "primary": ("primary", PRIMARY, 80),
    "primary_container": ("primary", 90, 30),
    "secondary": ("secondary", 40, 80),
    "secondary_container": ("secondary", 90, 30),
    "tertiary": ("tertiary", 40, 80),
    "tertiary_container": ("tertiary", 90, 30),
    "error": ("error", 40, 80),
    "error_container": ("error", 90, 30),
    # 表面全部取 neutral（彩度 0.014 的品牌底色）——这修掉了「dock/壳用的
    # surfaceContainer* 根本没传、回落 M3 内置紫灰、与品牌红不同色相」这一大问题。
    # 暗色各档由 DARK_SURFACE_TONE 派生（越高越浮起 → 逐级提亮）。
    "surface": ("neutral", 98, DARK_SURFACE_TONE),
    "surface_dim": ("neutral", 87, DARK_SURFACE_TONE),
    "surface_bright": ("neutral", 98, DARK_SURFACE_TONE + 12),
    "surface_container_lowest": ("neutral", 100, DARK_SURFACE_TONE - 6),
    "surface_container_low": ("neutral", 96, DARK_SURFACE_TONE - 3),
    "surface_container": ("neutral", 94, DARK_SURFACE_TONE + 2),
    "surface_container_high": ("neutral", 92, DARK_SURFACE_TONE + 6),
    "surface_container_highest": ("neutral", 90, DARK_SURFACE_TONE + 10),
    "surface_variant": ("neutral_variant", 90, 30),
    "outline": ("neutral_variant", 50, 60),
    "outline_variant": ("neutral_variant", 80, 30),
    "inverse_surface": ("neutral", 20, 90),
    "inverse_primary": ("primary", 80, 40),
    "surface_tint": ("primary", PRIMARY, 80),
}

# on_* : (压着的背景角色, 取哪条音阶, 首选 tone(浅,暗))
# 次要文字首选 30/80 而非极端 10/90，才有主次层级。
ON_ROLES = {
    "on_primary": ("primary", "primary", (100, 20)),
    "on_primary_container": ("primary_container", "primary", (10, 90)),
    "on_secondary": ("secondary", "secondary", (100, 20)),
    "on_secondary_container": ("secondary_container", "secondary", (10, 90)),
    "on_tertiary": ("tertiary", "tertiary", (100, 20)),
    "on_tertiary_container": ("tertiary_container", "tertiary", (10, 90)),
    "on_error": ("error", "error", (100, 20)),
    "on_error_container": ("error_container", "error", (10, 90)),
    "on_surface": ("surface", "neutral", (10, 90)),
    "on_surface_variant": ("surface_variant", "neutral_variant", (30, 80)),
    "inverse_on_surface": ("inverse_surface", "neutral", (95, 20)),
}


def generate():
    seed_l, seed_c, seed_h = hex_to_lch(SEED)
    hues = {
        "primary": seed_h,
        "secondary": seed_h,
        "tertiary": (seed_h + TERTIARY_HUE_OFFSET) % 360,
        "neutral": seed_h,
        "neutral_variant": seed_h,
        "error": ERROR_HUE,
    }
    ramps = {k: build_ramp(h, CHROMA[k]) for k, h in hues.items()}
    # 品牌本色对应档：保住 #C92027 的明度，不去 M3 的 tone 40
    seed_lum = rel_lum(SEED)
    seed_tone = min(ramps["primary"],
                    key=lambda t: abs(rel_lum(ramps["primary"][t]) - seed_lum))

    schemes = {}
    for mode, mi in (("light", 0), ("dark", 1)):
        s = {}
        for role, (pal, *tones) in BASE_ROLES.items():
            if tones[mi] == PRIMARY:
                s[role] = SEED.lstrip("#").upper()   # 品牌本色，逐位不动
                continue
            want = tones[mi]
            ramp = ramps[pal]
            # 派生 tone（DARK_SURFACE_TONE±n）未必在预置音阶上，就近取一档
            s[role] = ramp[want] if want in ramp else ramp[min(ramp, key=lambda t: abs(t - want))]
        for role, (bg_role, pal, pref) in ON_ROLES.items():
            target = CONTRAST_TARGET if role != "on_surface_variant" else CONTRAST_TARGET_SECONDARY
            hexv, passed = resolve_on(ramps[pal], s[bg_role], pref[mi], target)
            if not passed:
                print(f"  WARN  {mode} {role} 压在 {bg_role} 上达不到 {target}:1，已取最大反差档")
            s[role] = hexv
        s["scrim"] = "000000"
        schemes[mode] = s
    return seed_h, seed_tone, ramps, schemes


# ── 验证：只测真正会成对出现的 fg/bg ═───────────────────────────────
PAIRS = [
    ("on_primary", "primary", 4.5), ("on_primary_container", "primary_container", 4.5),
    ("on_secondary", "secondary", 4.5), ("on_secondary_container", "secondary_container", 4.5),
    ("on_tertiary", "tertiary", 4.5), ("on_tertiary_container", "tertiary_container", 4.5),
    ("on_error", "error", 4.5), ("on_error_container", "error_container", 4.5),
    ("on_surface", "surface", 4.5),
    ("on_surface", "surface_container_lowest", 4.5),
    ("on_surface", "surface_container_low", 4.5),
    ("on_surface", "surface_container", 4.5),
    ("on_surface", "surface_container_high", 4.5),
    ("on_surface", "surface_container_highest", 4.5),
    ("on_surface_variant", "surface", 3.0),
    ("on_surface_variant", "surface_container_high", 3.0),
    ("on_surface_variant", "surface_container_highest", 3.0),
    ("on_surface_variant", "surface_variant", 3.0),
    ("outline", "surface", 1.6),
    ("inverse_on_surface", "inverse_surface", 4.5),
]
CONTAINER_ORDER = ["surface_container_lowest", "surface_container_low", "surface_container",
                   "surface_container_high", "surface_container_highest"]


def verify(schemes: dict) -> bool:
    ok = True
    for mode in ("light", "dark"):
        s = schemes[mode]
        print(f"\n── {mode} ──")
        for fg, bg, target in PAIRS:
            r = contrast(s[fg], s[bg])
            if r < target:
                ok = False
            print(f"  {'PASS' if r >= target else 'FAIL'}  {fg:>22} on {bg:<24} {r:5.2f}:1 (≥{target})")
        # 主次容器必须可区分（旧代码里 primaryContainer == secondaryContainer 同为 FFDAD6）
        for a, b in (("primary_container", "secondary_container"),
                     ("primary_container", "tertiary_container")):
            if s[a] == s[b]:
                print(f"  FAIL  {a} 与 {b} 同色，无法区分")
                ok = False
        # 主次文字层级必须可区分（旧 pick_on 会把两者都推到极端 → 同色）
        if s["on_surface"] == s["on_surface_variant"]:
            print("  FAIL  on_surface 与 on_surface_variant 同色，主次文字层级被打平")
            ok = False
        # 容器明度必须单调（越高越浮起）
        lv = [rel_lum(s[k]) for k in CONTAINER_ORDER]
        mono = all(a > b for a, b in zip(lv, lv[1:])) if mode == "light" \
            else all(a < b for a, b in zip(lv, lv[1:]))
        if not mono:
            ok = False
        print(f"  {'PASS' if mono else 'FAIL'}  容器明度单调: {[s[k] for k in CONTAINER_ORDER]}")
    return ok


# ── 产出 Kotlin ───────────────────────────────────────────────────
KOTLIN = '''package com.thripleq.nume.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * **机器生成，请勿手改** —— 由 `tools/gen_palette.py` 从单一 seed 推导。
 * 换品牌色：改脚本里 `SEED` 一行 + 重跑，本文件与所有调用方零改动。
 *
 * seed = {seed}（品牌红）｜tertiary 色相 = seed + {tert_off}°｜暗色 surface 锚 tone {dst}
 *
 * 色相与明度音阶取自 OKLCH（感知均匀，tone = L×100）；与 Material Theme Builder 的
 * HCT 产出不逐位相同，但**所有 on_* 正文色对都按 WCAG 相对亮度验证过 ≥4.5:1**
 * （次要文字 ≥3.0:1），且主次容器、主次文字均强制可区分。
 *
 * 三条刻意的设计选择（详见脚本文件头）：
 * 1. 浅色 primary 用品牌本色而非 M3 惯例 tone 40 —— 白字压品牌红实测 {seed_white_ratio}:1，
 *    本就达 AA，压暗反而是去品牌化。
 * 2. 暗色 surface 锚现网明度（tone {dst}）而非 M3 的 tone 6，只把色相从冷蓝灰拧到品牌红调。
 * 3. on_* 先试 M3 规范 tone，不达标才修正 —— 保证次要文字不与主文字同色。
 *
 * 音阶留档（tone: RRGGBB）：
{ramp_doc}
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
{light})

// ── 深色 ────────────────────────────────────────────────────────
val NumeDarkColors = NumeColorRoles(
{dark})
'''

# 生成顺序：与 data class 参数顺序一致（snake_case → camelCase）
EMIT_ORDER = [
    "primary", "on_primary", "primary_container", "on_primary_container",
    "secondary", "on_secondary", "secondary_container", "on_secondary_container",
    "tertiary", "on_tertiary", "tertiary_container", "on_tertiary_container",
    "error", "on_error", "error_container", "on_error_container",
    "surface", "on_surface", "surface_dim", "surface_bright",
    "surface_container_lowest", "surface_container_low", "surface_container",
    "surface_container_high", "surface_container_highest",
    "surface_variant", "on_surface_variant", "outline", "outline_variant",
    "inverse_surface", "inverse_on_surface", "inverse_primary", "surface_tint",
]


def camel(snak: str) -> str:
    head, *rest = snak.split("_")
    return head + "".join(w.capitalize() for w in rest)


def emit_roles(scheme: dict) -> str:
    return "".join(f"    {camel(k)} = Color(0xFF{scheme[k]}),\n"
                   for k in EMIT_ORDER if k in scheme)


def emit_ramp_doc(ramps: dict) -> str:
    """音阶只作注释留档：真正常量在两套角色里，落成 val 就是无人引用的死代码。"""
    return "\n".join(
        " *" + f"   {name:<16} " + " ".join(f"{t}:{ramps[name][t]}" for t in (10, 20, 30, 40, 50, 60, 70, 80, 90, 99))
        for name in ("primary", "secondary", "tertiary", "neutral", "neutral_variant", "error")
    )


OUT = Path("app/src/main/java/com/thripleq/nume/ui/theme/Palette.kt")


def main() -> int:
    seed_h, seed_tone, ramps, schemes = generate()
    print(f"seed={SEED}  hue={seed_h:.1f}°  →  primary tone {seed_tone} (#{ramps['primary'][seed_tone]})")
    ok = verify(schemes)
    print("\n对比度与区分度校验:", "✅ 全部通过" if ok else "❌ 有不达标项")

    if "--write" in sys.argv:
        if not ok:
            print("校验未通过，拒绝写入 Palette.kt")
            return 1
        body = KOTLIN.format(
            seed=SEED, tert_off=TERTIARY_HUE_OFFSET, dst=DARK_SURFACE_TONE,
            seed_white_ratio=f"{contrast('FFFFFF', SEED):.1f}",
            ramp_doc=emit_ramp_doc(ramps),
            light=emit_roles(schemes["light"]),
            dark=emit_roles(schemes["dark"]),
        )
        OUT.write_text(body, encoding="utf-8")
        print(f"已写入 {OUT} ({len(body)} bytes)")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
