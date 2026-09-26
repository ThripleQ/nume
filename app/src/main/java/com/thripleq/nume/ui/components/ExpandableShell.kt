package com.thripleq.nume.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 壳当前展开进度（0..1）的 [State]，供内容里的浮层（如关闭按钮、banner 内缩）读取。
 *
 * 刻意暴露 [State] 而非裸 Float：内容方在读 `value` 时可自行选择在**布局/绘制阶段**读取，
 * 从而避免动画每帧触发整棵内容树重组。
 */
val LocalShellProgress: androidx.compose.runtime.ProvidableCompositionLocal<State<Float>> =
    staticCompositionLocalOf { mutableStateOf(1f) }

/**
 * 壳的**打开动画是否已结束**（[State]，只在需要时读取）。内容可借此把「加载/组合重列表」推迟到
 * 动画之后，避免动画期间组合列表造成尖峰帧。首个值在动画完成后翻 true。
 *
 * 非壳环境（直接使用 [TrackListScreen] 的页面）默认恒 true，因此立即加载。
 */
val LocalShellSettled: androidx.compose.runtime.ProvidableCompositionLocal<State<Boolean>> =
    staticCompositionLocalOf { mutableStateOf(true) }

/**
 * 水平内缩随壳展开进度收缩：语义等价于 `padding(horizontal = maxInset * progress)`，
 * 但在 **layout 阶段**读取 [progress]——因此宿主 composable 不会被每帧重组，
 * 只触发这一处重排。banner 封面 / 骨架封面用它替代组合期的 `16.dp * p`。
 */
fun Modifier.shellInset(progress: State<Float>, maxInset: Dp): Modifier =
    layout { measurable, constraints ->
        val inset = (maxInset.toPx() * progress.value).roundToInt()
        val w = (constraints.maxWidth - 2 * inset).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
        layout(constraints.maxWidth, placeable.height) { placeable.place(inset, 0) }
    }

/**
 * 顶部内缩随壳展开进度增长：语义等价于 `padding(top = inset * progress)`，
 * 但在 **layout 阶段**读取 [progress]——宿主 composable 不会被每帧重组。
 *
 * 壳顶一路长到屏幕顶（0）后，用它在全屏时把内容推回状态栏下方；p=0 时内缩为 0，
 * 内容仍与起点胶囊内部布局对齐。
 */
fun Modifier.shellTopInset(progress: State<Float>, insetPx: Float): Modifier =
    layout { measurable, constraints ->
        val top = (insetPx * progress.value).roundToInt().coerceIn(0, constraints.maxHeight)
        val h = (constraints.maxHeight - top).coerceAtLeast(0)
        val placeable = measurable.measure(constraints.copy(minHeight = h, maxHeight = h))
        layout(constraints.maxWidth, h) { placeable.place(0, top) }
    }

/**
 * 通用「胶囊壳 → 全屏面板」伸展覆盖层。
 *
 * 从一个胶囊的 [fromRect]（窗口坐标 Rect）平滑伸展到全屏，再缩回原位。
 * 用于「点击小胶囊 → 展开成面板」的过渡，收起动画保证最后一帧精确复位。
 *
 * ## 对齐契约（务必遵守，否则收起尾帧会跳位）
 * 1. [header] 必须与「起点胶囊的头部」是**同一个 composable**（颜色/文字/图标相对位置天然一致）。
 *    胶囊未展开时的头部由调用方渲染，展开壳顶部的标题栏由本组件渲染同一份 [header]。
 * 2. 起点胶囊的测量：`onGloballyPositioned` 的 bounds 必须是**视觉胶囊本身**——
 *    胶囊自身的 padding 不能加在 Card/bounds modifier 上，要放内部，否则 bounds 含 padding、终点偏。
 * 3. 壳内部布局必须与胶囊内部布局**同构**（同样的垂直 padding），否则内容相对壳顶的位置对不上。
 *
 * ## 打开/收起
 * - 打开：竖向、横向、内容淡入三轴并发展开。
 * - 收起：内容淡出 → 横向收窄 → 竖向缩回；全部完成后等两帧（[withFrameNanos]）让
 *   「完全复位」的画面真正绘制落地再调 [onDismiss]，避免最后一帧被跳过。
 *
 * @param fromRect  起点胶囊的窗口坐标 Rect；null 时用兜底几何（左/顶各 16dp、宽 328dp）
 * @param fullTopPx 内容需要避开的顶部高度（px），通常 = 状态栏高度。壳本身会一直长到屏幕顶
 *                  （含状态栏那段，不再另设填充块），内容在展开期间按此值下移
 * @param shapeCornerDp  壳圆角（px 动画不插值圆角，保持胶囊观感）
 * @param containerColor 壳背景色（应与胶囊 Card 颜色一致）
 * @param recessedBottom 内容区（窟窿）底部让位量：壳本身延伸到屏幕底，
 *                       内容区底部留出此高度露出底下导航岛，与岛同色融合
 * @param progress 外部受控展开进度（0..1）：非 null 时壳几何由它插值驱动（跟手），
 *                 自动开启动画被跳过；null 时用内部动画。关闭动画从当前进度续跑。
 * @param contentFromStart 内容从头可见、无独立 header：内容不淡入（alpha 恒 1），
 *                 让内容自身的第一项（如全宽封面）在 p=0 时恰好等于起点卡片、随壳生长；
 *                 header 槽不渲染，[recessedBottom] 也忽略（改由内容自行留底部空间）。
 *                 用于「大封面折进列表」——封面是列表第一项、随滚动移出。
 * @param heroTargetRect 内容首项（封面）的**窗口坐标**终态矩形（[State]，只在 layout/draw 阶段读取，
 *                 不订阅重组）。提供且 [contentFromStart] 为 false 时，在壳内叠加一张 hero 封面：
 *                 几何从起点胶囊矩形插值到该矩形，透明度与内容互补（内容淡入则 hero 淡出）。用于
 *                 「内容固定终态排版 + 壳裁剪」路径下恢复首尾与父级卡片的无缝对齐——p=0 时 hero
 *                 恰好等于卡片，p=1 时与内容里的 banner 封面重合。
 * @param heroReady     高清封面是否已绘制出来（[State]，只在 layout/draw 阶段读取）。为 true 时 hero
 *                      原地渐变淡出、交接给内容里的高清封面；为 false（数据/图片未到）时 hero 一直顶着。
 * @param heroContent   hero 覆盖层内容（通常与起点卡片封面同源）。null 则不绘制 hero。
 * @param heroCornerDp  hero 圆角（四角）。壳只圆顶角、底角是直角，hero 必须自带四角圆角，
 *                      否则 p=0 时底角与卡片对不上；应等于起点卡片/终态封面的圆角。
 * @param heroBlurDp    hero 未被交接时的模糊半径：低清封面被放大铺满，模糊可掩盖像素化。
 * @param onDismiss 关闭动画完全结束、壳复位后才回调（调用方借此移除本组件）
 * @param header    壳顶部标题栏（必须与胶囊头部同源）；接收 [onClose]，收起按钮应调它触发关闭动画。
 *                  [contentFromStart] 为 true 时不渲染。
 * @param content   壳内内容区（占剩余空间）
 */
@Composable
fun ExpandableShell(
    fromRect: Rect?,
    fullTopPx: Float,
    shapeCornerDp: Dp,
    containerColor: androidx.compose.ui.graphics.Color,
    recessedBottom: Dp = 0.dp,
    progress: Float? = null,
    contentFromStart: Boolean = false,
    heroTargetRect: State<Rect?>? = null,
    heroReady: State<Boolean>? = null,
    heroContent: (@Composable () -> Unit)? = null,
    heroCornerDp: Dp = 16.dp,
    heroBlurDp: Dp = 16.dp,
    onDismiss: () -> Unit,
    header: @Composable (onClose: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var viewWidth by remember { mutableStateOf(0) }
    var viewHeight by remember { mutableStateOf(0) }
    // 非 contentFromStart：header 的实际高度，用于给内容区算固定的终态高度。
    var headerHeightPx by remember { mutableStateOf(0) }

    // 起点（胶囊）几何，兜底 16dp 边距 + 328dp 宽。
    val capsuleLeft = fromRect?.left ?: with(density) { 16.dp.toPx() }
    val capsuleTop = fromRect?.top ?: fullTopPx
    val capsuleWidthPx = fromRect?.width ?: with(density) { 328.dp.toPx() }
    val capsuleHeightPx = fromRect?.height ?: 0f
    // 终点（全屏）几何：铺满整屏（顶到 0、底到屏底）。壳顶越过状态栏长到屏幕顶，
    // 是为了让状态栏那段也属于壳本身——否则要另设一块"让位填充"，它与还在小的壳脱节，
    // 点击瞬间像顶部突然涂色。内容再用 [shellTopInset] 下移 fullTopPx：全屏时让开状态栏，
    // p=0 时内缩为 0、仍与起点胶囊内部布局对齐。
    val fullLeft = 0f
    val fullWidthPx = viewWidth.toFloat()
    val fullTop = 0f
    val fullHeightPx = viewHeight.toFloat()

    var closing by remember { mutableStateOf(false) }
    val vertical = remember { Animatable(0f) }
    val horizontal = remember { Animatable(0f) }
    // Hero 透明度：1 = 低清 hero 顶着，0 = 已交接给高清封面。
    val heroAlpha = remember { Animatable(1f) }
    // 暴露给内容的只读进度 State（Animatable 本身不是 State，用 derivedStateOf 包一层）。
    val horizontalState = remember { derivedStateOf { horizontal.value } }
    val verticalState = remember { derivedStateOf { vertical.value } }

    // 外部受控进度：跟手时直接驱动壳几何（snap），不受内部动画干扰。
    // 关闭动画从当前进度续跑；打开动画仅在无外部进度时自动跑。
    val followProgress = progress != null
    var hasFollowed by remember { mutableStateOf(false) }
    // 打开动画是否结束（供内容把重加载推迟到动画之后）。
    val settled = remember { mutableStateOf(false) }
    LaunchedEffect(progress) {
        if (progress != null) {
            hasFollowed = true
            vertical.snapTo(progress)
            horizontal.snapTo(progress)
            settled.value = true
        } else if (hasFollowed) {
            // 从跟手态定格（progress→null）：从当前进度续跑打开动画到全屏。
            if (!closing && (vertical.value < 1f || horizontal.value < 1f)) {
                coroutineScope {
                    launch { vertical.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
                    launch { horizontal.animateTo(1f, tween(280, easing = FastOutSlowInEasing)) }
                }
            }
            settled.value = true
        }
    }

    // 打开：仅无外部进度时三轴并发展开（点击胶囊进入全屏面板的默认动画）。
    if (!followProgress) {
        LaunchedEffect(Unit) {
            coroutineScope {
                launch { vertical.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
                launch { horizontal.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
            }
            settled.value = true
        }
    }

    // Hero 交接：高清封面就绪、且壳已基本展开后，hero 原地渐变淡出（数据没到就一直顶着，
    // 不查“加载状态”，只看封面是否已绘制出来）。关闭时立刻收回 hero，让收起尾帧仍能精确缩回卡片。
    // 等壳展开再淡出很关键：否则高清封面若本来就绪，hero 会在展开初期就消失，露出固定排版的
    // 裁切内容（看起来像没优化）。
    val ready = heroReady?.value == true
    LaunchedEffect(ready, closing) {
        when {
            closing -> heroAlpha.snapTo(1f)
            !ready -> Unit
            else -> {
                snapshotFlow { horizontal.value }.first { it >= 0.98f }
                heroAlpha.animateTo(0f, tween(220))
            }
        }
    }

    fun startClose() {
        if (closing) return
        closing = true
    }
    LaunchedEffect(closing) {
        if (closing) {
            // 关闭：横向收窄与竖向缩回并发展开。
            coroutineScope {
                launch { horizontal.animateTo(0f, tween(240, easing = FastOutSlowInEasing)) }
                launch { vertical.animateTo(0f, tween(380, easing = FastOutSlowInEasing)) }
            }
            // animateTo 返回时值已到位，但该值的画面还要等重组+绘制才落地；
            // 立即 onDismiss 会把最后一帧跳过，壳停在 vertical≈0 处（偏上）。
            // 等两帧让「完全复位」的画面真正画出来再移除覆盖层。
            withFrameNanos { }
            withFrameNanos { }
            onDismiss()
        }
    }

    BackHandler { startClose() }

    // 注意：动画值（horizontal / vertical / heroAlpha）一律不在组合阶段读取，
    // 只在 layout / draw 的 lambda 里读——否则每帧都会重组整个 ExpandableShell
    // （含内容子树：列表/网格），这是胶囊壳展开卡顿的主因。
    // 启动动画的 LaunchedEffect 不依赖这些值，读值下沉不影响动画本身。

    // 占位层：盖住底下页面、拦截触摸。
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewWidth = it.width; viewHeight = it.height },
    ) {
        // 触摸拦截层（在壳之下、底下页面之上）：吃掉所有落在壳外的指针事件。
        // 否则展开动画期间壳还小，手指会穿透去滑动底下的列表；底下页面一滚，收起时
        // fromRect（进入时捕获的卡片位置）就与实际位置错位了。
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    }
                },
        )
        // 壳：位置/宽高全由插值驱动（显式宽高 + layout 阶段平移）。
        // 壳顶越过状态栏一直长到屏幕顶（0），状态栏那段就是壳本身；底到屏底，底角直角
        // （只顶圆角），底部让位由内容区（窟窿）padding 实现。
        val cornerPx = with(density) { shapeCornerDp.toPx() }
        Box(
            modifier = Modifier
                // 壳尺寸随动画每帧变化：把 animatable 读取下沉到 layout 阶段，
                // 于是只触发重排（不重组）——内容子树的组合被跳过。
                .layout { measurable, _ ->
                    val w = lerp(capsuleWidthPx, fullWidthPx, horizontal.value)
                        .roundToInt().coerceAtLeast(0)
                    val h = lerp(capsuleHeightPx, fullHeightPx, vertical.value)
                        .roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                // 平移同样在 layout 阶段读取；用 offset（只改放置、不新建图层）而非 graphicsLayer，
                // 避免给整屏壳再套一层 offscreen。
                .offset {
                    IntOffset(
                        lerp(capsuleLeft, fullLeft, horizontal.value).roundToInt(),
                        lerp(capsuleTop, fullTop, vertical.value).roundToInt(),
                    )
                }
                // 裁剪/投影只在动画期间需要：稳态下壳铺满全屏，圆角与投影都不可见，
                // 保留会给整屏壳套一层 RenderNode/offscreen，让滚动时整个列表每帧重录
                // display list——这是列表卡顿的主因。
                // 顶角圆角随展开收敛到 0：壳顶贴到屏幕顶后若还留圆角，会在屏幕两角露出底下
                // 页面、收起裁剪时还会"跳方"。在 draw 阶段读 progress，不触发重组。
                .then(
                    if (!settled.value || closing) {
                        Modifier.graphicsLayer {
                            val r = (cornerPx * (1f - vertical.value)).coerceAtLeast(0f)
                            shape = RoundedCornerShape(
                                topStart = r.toDp(),
                                topEnd = r.toDp(),
                                bottomStart = 0.dp,
                                bottomEnd = 0.dp,
                            )
                            clip = true
                            shadowElevation = 1.dp.toPx()
                        }
                    } else {
                        Modifier
                    },
                )
                .background(containerColor),
        ) {
            Column(
                Modifier
                    // 壳顶已长到屏幕顶：内容整体下移「状态栏高度 × 展开进度」（layout 阶段读，
                    // 不重组），全屏时正好让开状态栏，p=0 时仍与起点胶囊内部布局对齐。
                    .shellTopInset(verticalState, fullTopPx)
                    .fillMaxSize()
                    .padding(vertical = if (contentFromStart) 0.dp else 4.dp),
            ) {
                // 头部标题栏：与起点胶囊的头部同一份 composable，颜色/图文相对位置天然一致。
                // 把关闭动画触发器传给 slot：收起按钮调它走完整关闭动画，而不是直接移除壳。
                // contentFromStart 时无独立 header（封面本身是内容的第一项）。
                if (!contentFromStart) {
                    Box(Modifier.onSizeChanged { headerHeightPx = it.height }) {
                        header(::startClose)
                    }
                }
                // 内容区：
                // - contentFromStart：随壳重排（封面从卡片尺寸生长），用 weight 占剩余空间。
                // - 否则：按终态尺寸（全宽 × 屏高-header-内边距）固定排版一次。内容约束在动画
                //   期间不变，Compose 会跳过 measure（OuterMeasurablePlaceable 同约束缓存），
                //   于是列表/网格每帧零重排；水平再用 -shellLeft 抵消壳平移，使内容在屏幕上
                //   静止，只由壳的裁剪窗口逐步露出。alpha 仍在 draw 阶段读。
                val contentModifier = if (contentFromStart) {
                    Modifier.weight(1f).fillMaxWidth()
                } else {
                    val vPadPx = with(density) { 8.dp.toPx() }
                    val fixedHeightPx =
                        (viewHeight - fullTopPx - headerHeightPx - vPadPx).coerceAtLeast(0f)
                    Modifier
                        .requiredWidth(with(density) { viewWidth.toDp() })
                        .requiredHeight(with(density) { fixedHeightPx.toDp() })
                }
                Box(
                    contentModifier
                        .padding(bottom = if (contentFromStart) 0.dp else recessedBottom)
                        .offset {
                            // 抵消壳的水平平移，使内容在屏幕上静止、只由壳裁剪露出；用 offset 而非
                            // graphicsLayer，避免内容子树再套一层 offscreen（layout 期读取，不重组）。
                            val x = if (contentFromStart) 0
                                else -lerp(capsuleLeft, fullLeft, horizontal.value).roundToInt()
                            IntOffset(x, 0)
                        },
                    content = {
                        CompositionLocalProvider(
                            LocalShellProgress provides horizontalState,
                            LocalShellSettled provides settled,
                        ) {
                            content()
                        }
                    },
                )
            }

            // Hero 封面：固定排版路径下恢复首尾对齐。
            // 起点 = 胶囊矩形（壳局部坐标 (0,0,capsuleW,capsuleH)），终点 = heroTargetRect 换算到
            // **壳局部坐标**（窗口矩形 - 壳绘制原点；壳原点随动画移动，相减后终点恒定）。
            // 几何在 layout 阶段读动画值，不触发重组；透明度与内容互补（内容淡入则 hero 淡出）。
            if (!contentFromStart && heroContent != null) {
                Box(
                    Modifier
                        .layout { measurable, _ ->
                            val target = heroTargetRect?.value
                            val tW = target?.width ?: capsuleWidthPx
                            val tH = target?.height ?: capsuleHeightPx
                            val w = lerp(capsuleWidthPx, tW, horizontal.value)
                                .roundToInt().coerceAtLeast(0)
                            val h = lerp(capsuleHeightPx, tH, vertical.value)
                                .roundToInt().coerceAtLeast(0)
                            val placeable = measurable.measure(Constraints.fixed(w, h))
                            layout(w, h) { placeable.place(0, 0) }
                        }
                        .graphicsLayer {
                            val shellLeftNow = lerp(capsuleLeft, fullLeft, horizontal.value)
                            val shellTopNow = lerp(capsuleTop, fullTop, vertical.value)
                            val target = heroTargetRect?.value
                            // 终点在壳局部坐标：窗口坐标 - 壳绘制原点（相减后终点不随壳移动）。
                            val targetLeft = (target?.left ?: capsuleLeft) - shellLeftNow
                            val targetTop = (target?.top ?: capsuleTop) - shellTopNow
                            translationX = lerp(0f, targetLeft, horizontal.value)
                            translationY = lerp(0f, targetTop, vertical.value)
                            alpha = heroAlpha.value
                        }
                        // 未交接时模糊（低清封面放大铺满，模糊掩盖像素化）；就绪后归零。
                        .blur(if (ready) 0.dp else heroBlurDp)
                        // 壳只圆顶角，hero 自带四角圆角才能与卡片/终态封面吻合。
                        .clip(RoundedCornerShape(heroCornerDp)),
                ) { heroContent() }
            }
        }
    }
}

/**
 * 通用「大封面卡 → 全屏内容」伸展壳：把卡片（窗口坐标 [fromRect]）长成全屏，期间用 hero 封面
 * 从卡片位置插值到内容里的 banner 封面，首尾无缝；左上角浮一个随进度淡入的收起按钮。
 *
 * 探索页（歌单/榜单 → 曲目列表）与「我的」页（大卡 → 曲目列表/歌单网格）共用本组件。
 *
 * **对齐契约**：内容首项若是方形 banner 封面，必须位于「左右 16dp 内缩、状态栏下 4dp」处
 * （同 [TrackListScreen] 的 banner 头）；本组件按此**预测** hero 终点矩形，不再每帧测量回写。
 *
 * @param coverUrl hero 封面 URL（应与起点卡片同源）；null 时 hero 用占位底
 * @param title    hero 封面上的名字
 * @param watermarkIcon hero 的内容属性水印图标：与起点卡片、内容 banner 传同一个，
 *                 缺封面时三处都显示同一枚图标（否则 p=0 的 hero 与卡片对不上）
 * @param content  面板内容；参数 `onCoverReady` 在内容里的高清 banner 封面画出来后调用，
 *                 触发 hero 交接淡出（数据/图片未到则 hero 一直顶着）
 */
@Composable
fun CoverExpandShell(
    fromRect: Rect?,
    coverUrl: String?,
    title: String,
    onDismiss: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    shapeCornerDp: Dp = 16.dp,
    watermarkIcon: ImageVector? = null,
    content: @Composable (onCoverReady: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val statusBarTopPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    // hero 终态矩形用「预测值」：16dp 内缩、方形、内容顶（状态栏下 + 4dp 内边距）下方，
    // 与内容里 banner 封面同位。不再每帧测量回写（省掉每帧 onGloballyPositioned）。
    val coverSidePx = LocalConfiguration.current.screenWidthDp * density.density -
        with(density) { 32.dp.toPx() }
    val coverLeftPx = with(density) { 16.dp.toPx() }
    val coverTopPx = statusBarTopPx + with(density) { 4.dp.toPx() }
    val coverRect = remember {
        mutableStateOf<Rect?>(
            Rect(coverLeftPx, coverTopPx, coverLeftPx + coverSidePx, coverTopPx + coverSidePx),
        )
    }
    val coverReady = remember { mutableStateOf(false) }

    ExpandableShell(
        fromRect = fromRect,
        fullTopPx = statusBarTopPx,
        shapeCornerDp = shapeCornerDp,
        containerColor = containerColor,
        contentFromStart = false,
        heroTargetRect = coverRect,
        heroReady = coverReady,
        heroContent = {
            BigCoverVisual(coverUrl, title, Modifier.fillMaxSize(), watermarkIcon = watermarkIcon)
        },
        onDismiss = onDismiss,
        header = {},
        content = {
            Box(Modifier.fillMaxSize()) {
                content { coverReady.value = true }
                // 关闭按钮：浮在左上、不随列表滚，随展开进度淡入（p=0 不可见、不响应点击）。
                val progress = LocalShellProgress.current
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(36.dp)
                        .graphicsLayer { alpha = progress.value }
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.38f))
                        .clickable { if (progress.value > 0.5f) onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
    )
}
