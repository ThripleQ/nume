package com.thripleq.nume.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt

/**
 * 全局动效令牌 —— 伸展壳（`ExpandableShell`）与 dock（`PlayerDock`）**共用同一套**
 * 「时长 + 曲线 + 形状」词汇。相邻的两处开合动画必须读这里，不再各写各的魔数，
 * 否则并排看就是「两个不同 App 的手感」。
 *
 * ## 为什么是这几条曲线
 * 目标手感是 iOS 级「同一个物体在连贯开合」：
 * - 展开用 **Emphasized Decelerate**：出闸果断、尾巴长而平——物体"自己冲出去再滑停"，
 *   比 `FastOutSlowIn` 更有质量感，也不会匀速到显得死板。
 * - 收起用 **Emphasized**：起步利索、**末段减速停稳**。绝不能用 accelerate 系收尾，
 *   那会让壳「啪」一下撞回卡片位置——正是之前收尾难看的直接原因。
 */
object Motion {

    // ── 曲线族（Material3 Emphasized 家族） ──────────────────────────
    /** 展开 / 进入：快速起步 + 长减速尾。 */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 收起 / 归位：利索起步 + 减速停稳（不弹跳、不硬切）。 */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 微交互：对称的进出曲线，适合短时长淡入淡出/尺寸伸缩（不与 Emphasized 撞值）。 */
    val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ── 时长族（ms） ────────────────────────────────────────────────
    /** 壳展开总时长。 */
    const val ShellOpenMs = 420

    /** 壳收起总时长：短于展开，符合"回原处比去新地方快"的预期。 */
    const val ShellCloseMs = 320

    /** 微交互统一时长：dock 内浮层显隐用它，不再 180/200 混用导致成对动画不同步。 */
    const val MicroMs = 200

    // ── 形状族 ──────────────────────────────────────────────────────
    /**
     * 圆角保持"父级卡片真圆角"的时间占比。
     *
     * t < CornerHold 时圆角完全不收敛——此阶段壳仍是卡片比例，圆角必须与父级卡片
     * 逐像素一致（这是「与父级内容完美衔接」契约的一部分）；越过该点后 smoothstep
     * 平滑收敛到 0，既不提前变形，也不在末段"啪"地变方。
     *
     * 取 0.85（原为 0.55）：壳在「明显还是个小于全屏的窗口」的整段都保持圆角，只有临近
     * 铺满的最后 15% 才归零——否则后程圆角早早消失、窗口看着很尖锐。
     */
    const val CornerHold = 0.85f

    /**
     * hero 低清封面与内容里高清 banner 的交接阈值。
     *
     * 取 1（壳完全展开）而非更早：交接时内容封面要在**与 hero 像素对齐后**才变为不透明，
     * 若在 0.98 等壳尚未长到终态时交接，内容封面会比 hero 偏下若干 px、露出边缘。
     */
    const val HeroHandoffAt = 1f

    /** 交接淡出时长。 */
    const val HeroFadeMs = 220

    /** hero 封面运行时模糊的峰值半径（px）。 */
    const val HeroBlurMaxPx = 28f

    /** hero 模糊升到峰值所占的进度比例：之后一路衰减回 0。 */
    const val HeroBlurRise = 0.25f

    /**
     * hero 封面的运行时模糊半径（px）随展开进度 t——「拉开时轻微失焦、落定前重新合焦」。
     *
     * - `t=0` 为 0：首帧必须与起点卡片逐像素吻合，不能是糊的。
     * - 前 [HeroBlurRise] 段快速升到 [HeroBlurMaxPx]：此时图层还小、模糊最便宜。
     * - 之后 smoothstep 衰减，`t=1` 精确归零：交接前已清晰，与内容里的高清封面同形，
     *   淡化不可见；且全屏（图层最大）那一刻半径已≈0，调用方可直接摘掉 effect。
     */
    fun heroBlurPx(t: Float): Float {
        fun smooth(x: Float) = x * x * (3f - 2f * x)
        val rise = smooth((t / HeroBlurRise).coerceIn(0f, 1f))
        val fall = smooth((1f - t).coerceIn(0f, 1f))
        return HeroBlurMaxPx * rise * fall
    }

    /**
     * hero 文本（名字/元信息）淡出/淡入的进度窗口。
     *
     * 展开时窗口宽（[HeroTextFadeOutAt]）：壳一开始长大就让文本淡走，观感自然。
     * 收起时窗口窄（[HeroTextFadeInAt]）：hero 缩回过程中全程不放文本，直到**几乎等于卡片大小**
     * 才淡入——否则文本随盒子逐帧重排、换行/省略号来回移动，看着"跳（自动截断）"。
     */
    const val HeroTextFadeOutAt = 0.18f
    const val HeroTextFadeInAt = 0.06f

    /**
     * hero 文本透明度随进度 t：两端为 1（p=0 与卡片、p=1 与内容封面逐项吻合），中间为 0，
     * 避免 hero 盒子逐帧缩放时文本被每帧重新排版而"跳动"。
     *
     * @param closing 收起方向用更窄的窗口：只在末尾、盒子已≈卡片大小时才淡入，收起全程看不到重排。
     */
    fun heroTextAlpha(t: Float, closing: Boolean): Float {
        fun smooth(x: Float) = x * x * (3f - 2f * x)
        val window = if (closing) HeroTextFadeInAt else HeroTextFadeOutAt
        return 1f - smooth((t / window).coerceIn(0f, 1f))
    }

    // ── 壳动画 spec ─────────────────────────────────────────────────
    /** 壳展开（点击进入全屏）。 */
    fun shellOpen(): AnimationSpec<Float> =
        tween(ShellOpenMs, easing = EmphasizedDecelerate)

    /** 壳收起（回原处）。 */
    fun shellClose(): AnimationSpec<Float> =
        tween(ShellCloseMs, easing = Emphasized)

    /**
     * 跟手松手后的续跑 spec：时长按**剩余距离**等比给。
     *
     * 固定时长是错的——已经跟手拉到 0.9 再跑完整 300ms 会显得拖沓，
     * 只拉到 0.1 就只剩 300ms 又显得 rushed。手已停（零初速），故仍用减速曲线。
     */
    fun shellResume(from: Float): AnimationSpec<Float> {
        val remain = (1f - from.coerceIn(0f, 1f))
        return tween(
            delayMillis = 0,
            durationMillis = (ShellOpenMs * remain).roundToInt().coerceAtLeast(90),
            easing = EmphasizedDecelerate,
        )
    }

    // ── dock 档位 spec ──────────────────────────────────────────────
    /** 档位吸附 / 收起：干净无回弹。 */
    val SheetSettle: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 点击直达全屏：低阻尼带一点弹性，让"生长"过程看得见。 */
    val SheetExpand: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /**
     * 圆角收敛系数：`1` = 保持父级真圆角，`0` = 完全收敛。
     *
     * 前 [hold] 段恒为 1，之后以 smoothstep 落到 0——保证 p=0 与父级卡片像素级吻合，
     * 同时消除末段线性归零带来的"啪"感。
     */
    fun cornerTaper(t: Float, hold: Float = CornerHold): Float {
        if (t >= 1f) return 0f
        if (t <= hold) return 1f
        val k = (t - hold) / (1f - hold)
        return 1f - k * k * (3f - 2f * k)
    }
}
