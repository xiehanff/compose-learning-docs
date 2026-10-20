# 第 41 章：Density 到底是什么

## 1. 本章目标

先看两个真实问题。其一：同一个按钮写死 `Modifier.width(100.dp)`，在 A 手机上占屏宽约四分之一，在 B 手机上只占五分之一——代码没变，变的是 dp 到物理像素的换算倍率。其二：设计稿标注字号 16，你直接抄成 `16.sp`，测试机上大小却和设计稿对不上。两个问题的答案都指向 Density：Compose 布局用 dp 书写，屏幕最终绘制物理像素，Density 是两者之间公开约定的换算环境。

从单位公式理解 Density 如何把 Dp 转成物理像素，并认识设计稿适配对无障碍和组件默认尺寸的影响。

学完后，你应当能够：

- 区分物理 px、Dp、sp 和设计稿坐标四种数字
- 掌握 px = dp × density 与 px = sp × fontScale × density 两条换算公式
- 由屏宽物理像素和 density 算出可用 dp 宽度
- 理解 fontScale 与系统字体缩放（无障碍）的关系
- 知道覆盖 LocalDensity 会影响整棵子树，且它不等于响应式设计

## 2. 项目中的位置

- `ui/adapter/DesignWidthScale.kt`
- `ui/adapter/Px.kt`

原工程未随本仓库分发，路径仅用于说明来源（见 README）。

## 3. 项目源码片段

> 警告：本组 41—45 章展示的是教学工程的实验性设计稿映射方案，不是 Compose 官方推荐的通用响应式适配方案。覆盖 `LocalDensity` 会连带改变普通 `dp`、Material 默认尺寸、系统触控目标、第三方 Compose 组件和部分文字换算。生产页面优先使用响应式布局、WindowSizeClass 和系统 `sp`；请先阅读第 45 章的风险与边界。

项目节选（教学推演，不可直接复制：`metrics`、`fontScale` 来自原工程上下文）：

```kotlin
val scale = metrics.widthPixels / 750f
val designDensity = Density(
    density = scale,
    fontScale = fontScale,
)

// 近似关系
physicalPx = dpValue * density
```

### 3.1 独立示例：在运行时验证换算公式

参与编译校验，不依赖原工程。显示的数值随设备变化，可以拿真机数字回代核对公式。

<!-- compile -->
```kotlin
val density = LocalDensity.current
// 两条公式（Compose 单位换算的公开约定，见 4.2）：
// px = dp × density；px = sp × fontScale × density
// 注释按"纸面设备"推算：density = 2.75、fontScale = 1.3（本章与 43、44 章共用它）
val widthPx = with(density) { 100.dp.toPx() } // 100 × 2.75 = 275.0
val fontPx = with(density) { 16.sp.toPx() }   // 16 × 1.3 × 2.75 = 57.2
val backDp = with(density) { 275f.toDp() }    // 275 / 2.75 = 100.0dp
Text("100dp = $widthPx px；16sp = $fontPx px；275px = $backDp dp")
```

首次出现的 Kotlin 语法：`with(对象) { ... }` 是 Kotlin 标准库的作用域函数。lambda 内部的 `this` 变成传入的对象，于是能直接调用它的成员；`toPx()` / `toDp()` 是 Density 接口上的成员扩展函数，必须处在 Density 环境里才能调用，`with(density)` 就是把当前环境借进来。

预期观察：真机上 Text 显示的三个数值满足 4.2 的公式；具体数字随设备的 density、fontScale 变化。

## 4. 逐步解释

### 4.1 四种数字：px、dp、sp、设计稿坐标

设备最终绘制物理像素，Compose 布局通常使用 Dp。先把四种容易混淆的数字分开：

| 数字 | 它是什么 | 换算关系 |
| --- | --- | --- |
| px（物理像素） | 屏幕真实发光点，绘制阶段的最终单位 | — |
| dp（Dp） | 密度无关的布局书写单位，随 density 缩放 | px = dp × density |
| sp（TextUnit） | dp 基础上再乘用户字体倍率，文字专用（第 11 章） | px = sp × fontScale × density |
| 设计稿坐标 | 设计文件里的原始数值（如 750 稿上的 32） | 本身没有单位语义，要先约定"按什么基准解释" |

Density.density 是 dp 与 px 之间的换算倍率：density = 3 时，100.dp 约为 300px。density 的来源是系统报告值，约等于「设备 dpi ÷ 160」，例如 440dpi 的设备 density = 2.75。同一物理宽度下，density 越大，可用 dp 越少。

设计稿坐标最容易被直接当 dp 或 sp 抄进代码。它本身没有单位语义——第 43、44 章的 `.px` / `.pxSp()` 就是项目给设计稿坐标补上解释基准的约定。

### 4.2 两条公式与算例（本章与 43、44 章共用同一台"纸面设备"）

纸面设备假设：屏宽 1080px、density 2.75、fontScale 1.3。

| 要算什么 | 公式 | 代入 | 结果 |
| --- | --- | --- | --- |
| 可用宽度（dp） | 物理宽 ÷ density | 1080 ÷ 2.75 | ≈ 392.7dp |
| 100dp 的物理像素 | dp × density | 100 × 2.75 | 275px |
| 16sp 的物理像素 | sp × fontScale × density | 16 × 1.3 × 2.75 | 57.2px |

这两条公式对应 Density 接口 `Dp.toPx()` 与 `TextUnit.toPx()` 的公开换算行为（官方 API 参考见本章末尾），是 Compose 单位换算的公开约定，不是实现巧合。反向换算用 `toDp()` / `toSp()`（见 3.1 示例）。由第一行还能得到一个常用估算：可用 dp 宽 = 屏宽物理像素 ÷ density，纸面设备约 392.7dp——第 43 章算缩放系数时直接用到。

### 4.3 fontScale：字体倍率是独立的一条轴

fontScale 单独表示用户系统字体倍率，不能简单忽略。用户在系统设置里调大字体，fontScale 从 1.0 变成 1.3：16sp 的物理大小从 44px（16 × 2.75）涨到 57.2px，而所有 dp 布局完全不动。这正是 sp 的无障碍意义（第 11 章）。如果为了还原设计稿而在换算时除以 fontScale（第 44 章 `.pxSp()` 就是这种做法），文字大小就不再跟随用户设置——这是在尺寸一致性和无障碍字体放大之间做取舍，不等于完整支持无障碍。

### 4.4 项目做法：把 density 本身换掉

项目将 density 设置为 `屏幕宽物理像素 / 750`。1080px 宽屏得到 1.44，750.dp 会换算成 1080px。项目扩展名叫 `.px`，返回类型其实仍是 Dp；只是新的 Density 让设计稿上的数字按屏宽解释（设计稿适配详见第 43 章）。这会把所有普通 Dp 一起带入缩放，不能只理解为"设计稿数字转换器"。

### 4.5 覆盖 LocalDensity 不等于响应式设计

覆盖 LocalDensity 改变的是整棵子树的 dp / sp 到 px 的换算比例：所有普通 dp、Material 组件默认尺寸、最小触控目标、第三方组件一起变。它只回答"数字乘多少"，不回答响应式设计要回答的问题——"空间变大变小时，页面结构要不要变"（换布局、换导航、加栏）。结构层面的适配要用布局手段，见第 79 章；覆盖密度的全部副作用在第 43、45 章展开。把两者混为一谈，是设计稿适配最常见的方向性错误。

## 5. 实际执行过程

1. 读取屏幕宽物理像素
2. 计算 width/750
3. 创建自定义 Density
4. 子树测量 Dp 时读取该倍率
5. 转换为物理像素绘制

## 6. Flutter / Dart 3 对照

Flutter 布局单位是逻辑像素，devicePixelRatio 决定逻辑像素到物理像素——与 density 概念相似：都是「逻辑单位 → 物理像素」的倍率，Flutter 的逻辑像素大致对应 dp。但两边机制并不等价：Flutter 没有与 sp 对应的独立单位，文字缩放由 TextScaler 承担（第 11 章对照）；设计稿缩放通常另算 scale，而不是替换全局 DPR——这正是第 43 章方案在 Flutter 侧的对照写法。

```dart
final size = MediaQuery.sizeOf(context);
final scale = size.width / 375.0;
final width = 100 * scale;
```

## 7. 常见错误

- 把全局覆盖 Density 当成生产项目的通用适配方案
- 在 Material 页面、第三方组件、混合 View/Compose 页面或多窗口场景中无边界使用它
- 认为 1.dp 永远等于 1px
- 把 density 当成分辨率（分辨率是物理像素数，density 是换算倍率）
- 覆盖 Density 后忽略 Material 默认 dp 也被缩放
- 把 fontScale 固定为 1
- 把设计稿坐标直接当 dp / sp 抄进代码，不声明解释基准

错误示例（故意错误，不参与编译）：

```kotlin
// 编译错误：toPx()/roundToPx() 是 Density 的成员扩展，脱离 Density 环境无法调用；
// 这里还把 dp 当成了物理像素（density 2.75 下 2dp 是 5.5px）
val strokePx: Int = 2.dp.roundToPx()
```

## 8. 本章检查题

1. 1080/750 的 scale 是多少？
2. 预测：density 3.0、fontScale 1.2 的设备上，20dp 和 20sp 各是多少物理像素？
3. 100.px 最终如何换算？
4. density 和 fontScale 各负责什么？
5. 判断：覆盖 LocalDensity 之后，页面就完成了"响应式适配"。对吗？
6. 动手：在 3.1 示例里把 `100.dp` 换成 `100.px`（第 44 章的扩展），运行后数值会变吗？为什么？

答案：

1. scale = 1080 / 750 ≈ 1.44。
2. 20dp = 20 × 3.0 = 60px；20sp = 20 × 1.2 × 3.0 = 72px。sp 比 dp 多乘一个 fontScale。
3. 100.px 返回的是 Dp，真正的物理像素换算发生在布局测量阶段，由当前 Density 决定（1.44 倍率下约 144px）。
4. density 负责 dp 与 px 之间的换算倍率；fontScale 单独表示用户系统字体倍率，影响文字显示。
5. 不对。覆盖 LocalDensity 只改单位换算比例；响应式要解决"空间变化后结构怎么变"，用约束、weight、WindowSizeClass 等布局手段（第 79 章），副作用另见第 43、45 章。
6. 不变。`.px` 的定义就是 `toFloat().dp`，100.px 与 100.dp 是同一个 Dp 值；同一份 Density 下换算结果相同。要让数字变，得让它处于被覆盖的 Density 子树里（第 43 章）。

## 9. 本章总结

Density 是单位换算环境：px = dp × density、px = sp × fontScale × density 是公开约定，可用 dp 宽 = 物理宽 ÷ density。fontScale 是独立的字体倍率轴，绕过它就是绕过无障碍。项目通过替换 Density 实现设计稿映射，但影响的是整个子树的所有 Dp；这是一种有明确代价的教学方案，不应取代生产项目的响应式布局和无障碍策略。

官方参考：

- [Density API 参考（toPx / toDp / toSp 的换算定义）](https://developer.android.com/reference/kotlin/androidx/compose/ui/unit/Density)
- [androidx.compose.ui.platform 包参考（LocalDensity 所在包）](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary)
