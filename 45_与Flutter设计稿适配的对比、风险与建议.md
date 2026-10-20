# 第 45 章：与 Flutter 设计稿适配的对比、风险与建议

## 1. 本章目标

先回答一个工程问题：设计稿按 375 宽出图，真机可用宽度却是 392.7dp，平板 800dp（第 41、43 章的数字）。"把数字按宽度乘个系数"看起来最省事，第 43 章也确实这么做了。本章回答：这条路线什么时候能用、什么时候必须放弃，生产环境按什么顺序选方案。结论先行（作者建议级）：全局覆盖 LocalDensity 不推荐用于生产环境，理由见 4.2。

评估全局 Density 适配方案的优缺点，并建立生产项目的响应式优先原则。

学完后，你应当能够：

- 对比全局覆盖与显式缩放
- 识别平板、横屏、分屏风险
- 保护系统字体和最小触控尺寸
- 优先使用响应式布局（第 79 章详解）和窗口分类
- 按方案选择表为页面挑选适配手段

## 2. 项目中的位置

- `ui/adapter/DesignWidthScale.kt`
- `ui/adapter/Px.kt`
- `components/ScreenAdapterDemo.kt`

原工程未随本仓库分发，路径仅用于说明来源（见 README）。

## 3. 项目源码片段

项目节选与方案推演（教学简化，不可直接复制：`metrics`、`content()`、`designWidth` 是原工程 / 推演符号）：

```kotlin
// 当前方案：整个子树都改变
val scale = metrics.widthPixels / 750f
CompositionLocalProvider(
    LocalDensity provides Density(scale, fontScale)
) { content() }

// 保守方案：需要的位置显式使用受限倍率
val scale = (screenWidth / designWidth)
    .coerceIn(0.9f, 1.2f)
```

### 3.1 独立示例：受限倍率的显式换算

参与编译校验。演示保守方案的落点：倍率夹在上下限内，只作用于显式换算的设计尺寸，不动 LocalDensity。

<!-- compile -->
```kotlin
// 纸面设备（第 41 章）：可用宽 ≈ 392.7dp；实际取值用 BoxWithConstraints 或 WindowSizeClass
val screenWidthDp = 392.7f
// coerceIn：Kotlin 标准库函数，把数值限制在 [min, max] 区间内
val scale = (screenWidthDp / 375f).coerceIn(0.9f, 1.2f) // 392.7 / 375 ≈ 1.047，未触上下限
// 只在设计稿尺寸处使用：设计宽 320 → 320 × 1.047 ≈ 335dp
Box(
    Modifier
        .width((320 * scale).dp)
        .height(48.dp)
        .background(Color(0xFF90CAF9)),
)
```

预期观察：纸面设备上盒子宽约 335dp；把 `screenWidthDp` 改成 800（平板），scale 被上限 1.2 截住，盒子 384dp 而不是 683dp——这就是"倍率必须设上限"的直接演示。

## 4. 逐步解释

### 4.1 适配方案选择表

| 方案 | 适用场景 | 优点 | 风险 |
| --- | --- | --- | --- |
| 普通响应式布局（fillMax*、weight、BoxWithConstraints、约束） | 大多数业务页面 | Android 官方文档主线手段；无全局副作用 | 不能逐像素复现单一设计稿；断点之间仍需设计侧配合 |
| WindowSizeClass（[第 79 章](79_响应式布局与WindowSizeClass.md)） | 手机 / 折叠屏 / 平板多窗口形态 | 官方断点体系；按空间能力换页面结构 | 依赖不在 Compose BOM 内（基线 Material 3 Adaptive 1.3.0-rc01，见版本基线文档）；断点内仍有宽度差异，还需组件级响应式 |
| 单独设计缩放（`.px` 显式换算 + 受限倍率，3.1） | 强设计稿还原的局部页面、演示页 | 影响局部；可设上下限；不改系统换算环境 | 双单位并存有维护成本；字号仍要做 sp / fontScale 取舍（第 44 章） |
| 覆盖 LocalDensity（DesignWidthScale，第 43 章） | 教学 Demo、原型验证 | 一次包裹整树生效；设计稿数字直接用 | 全子树 dp、Material 默认尺寸、触控目标、第三方组件全部改变；多窗口与字体缩放交互复杂；不推荐生产 |

表中前两行是官方响应式路径，后两行是本项目 / 社区做法（作者归纳，证据等级见 `文档基线与审校规范.md`）。选择顺序自上而下：先用响应式结构，缺设计稿还原度再考虑受限缩放，覆盖 LocalDensity 留给教学与原型。

### 4.2 覆盖 LocalDensity：为什么不推荐生产

覆盖 LocalDensity 的优点是设计稿数字使用直接，所有尺寸统一缩放。最大风险也是影响过大：普通 dp、Material 默认尺寸、第三方组件、触控区域和文本换算全部改变（第 43 章 4.4 的副作用清单）。具体到设备形态：

- 平板可能出现巨大控件（第 43 章检查题 4：系数 2.13，48dp 触控目标等效 102dp）；
- 横屏可能因按宽度放大而没有垂直空间；
- 分屏会让全界面连续缩放，且系数可能按旧窗口宽失配；
- 文字侧 `.pxSp()` 抵消 fontScale 后不再响应系统字体缩放（第 44 章 4.2）。

生产环境的明确风险说明（作者建议级）：全局缩放副作用难审计（第三方组件、无障碍触控、多窗口），维护成本随页面数上升，且与系统行为（字体缩放、显示大小设置）冲突时难以向用户解释。任何框架都不应把"设计稿同比放大"当成真正适配。

### 4.3 与 Flutter 方案对照

Flutter ScreenUtil 常用显式 `.w/.sp`，标准 Widget 默认逻辑像素不被全局重定义，影响相对局部。flutter_screenutil 与 DesignWidthScale 的思路同源——都按设计稿宽算系数（`屏幕宽 / 设计稿宽`）；差别在生效方式：前者只在显式调用处换算，不改 `devicePixelRatio`，后者直接替换整棵子树的换算环境。边界也一样：flutter_screenutil 是第三方库，Flutter 官方文档的适配主线同样是 MediaQuery、LayoutBuilder、约束与断点——两边都不把设计稿缩放当作默认推荐手段。

### 4.4 生产建议：响应式优先

生产建议优先用约束、weight、WindowSizeClass、断点和响应式结构；缩放只用于少量视觉尺寸并设置上下限；文字尊重系统 fontScale；覆盖手机、平板、横屏、分屏和大字体测试。

一个最小的 Compose 响应式例子是按父容器宽度切换排列方向：

<!-- compile decls -->
```kotlin
@Composable
fun AdaptiveSummary(title: String, detail: String) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 600.dp) {
            Column { Text(title); Text(detail) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(title)
                Text(detail)
            }
        }
    }
}
```

`BoxWithConstraints` 把父容器的最大宽度作为 `maxWidth` 提供给内容；这里的 `600.dp` 只是演示断点。完整应用应先读第 79 章的窗口分类与布局策略，再决定断点和页面结构。

## 5. 实际执行过程

1. 判断窗口类别
2. 先确定响应式结构
3. 必要时计算受限倍率
4. 仅缩放明确设计尺寸
5. 保留标准组件和字体语义
6. 多设备与无障碍验证（无障碍见第 80 章）

## 6. Flutter / Dart 3 对照

Flutter 同样应优先 MediaQuery、LayoutBuilder、Expanded/Flexible、Wrap 和断点，而不是机械乘 ScreenUtil。

```dart
LayoutBuilder(
  builder: (context, constraints) {
    return constraints.maxWidth < 600
        ? const PhoneLayout()
        : const TabletLayout();
  },
);
```

与 Compose 的 `BoxWithConstraints` 概念对应（都暴露父约束）、机制不同（LayoutBuilder 是 Builder 回调重建子树，BoxWithConstraints 依赖作用域与重组），只做概念对照。

## 7. 常见错误

- 追求所有设备像截图同比放大
- 字体按宽度缩放而忽略大字体
- 平板和横屏没有倍率上限
- 第三方组件异常时没意识到 Density 被改写
- 同时使用多套单位规则
- 把设计稿缩放方案原样带进新工程（它是项目扩展，不是 SDK 能力，第 44 章）

错误示例（故意错误，不参与编译）：

```kotlin
// 错误示例：为"一稿走天下"在根部覆盖 LocalDensity，不设范围、不设上限
setContent {
    DesignWidthScale {          // 平板、横屏、分屏全部同比放大（第 43 章 4.4）
        DemoTheme { DemoApp() } // 字体缩放也被 pxSp 抵消（第 44 章 4.2）
    }
}
```

## 8. 本章检查题

1. 覆盖 Density 最大的作用范围风险是什么？
2. 平板为何不应同比放大所有控件？
3. 生产适配应优先倍率还是响应式布局？
4. 预测：`coerceIn(0.9f, 1.2f)` 的受限倍率在"可用宽 800dp、设计稿 375"的平板上取多少？设计宽 320 的元素实际多少 dp？
5. 动手：把 3.1 里硬编码的 `screenWidthDp` 换成 `BoxWithConstraints` 的 `maxWidth.value`，让倍率跟随容器宽度。

答案：

1. 全局改写 Density 会改变普通 dp、Material 默认尺寸、第三方组件、触控区域和文本换算，影响面过大。
2. 平板同比放大所有控件会浪费空间、破坏响应式结构和无障碍语义，平板应利用空间而非放大。
3. 响应式布局优先（约束、weight、WindowSizeClass、断点），设计稿倍率只作为辅助工具并设置上下限。
4. 800 / 375 ≈ 2.13 触上限，取 1.2；320 × 1.2 = 384dp。没有上限时按精确比例 320 × 800 ÷ 375 ≈ 683dp——这就是 3.1 与全局方案的分界。
5. 提示：`BoxWithConstraints { val scale = (maxWidth.value / 375f).coerceIn(0.9f, 1.2f); ... }`。倍率随容器变化，分屏、横屏都自动跟随，这也是"受限缩放优于全局覆盖"的工程理由之一。

## 9. 本章总结

当前方案适合教学和强设计稿还原；生产环境应以响应式结构和无障碍为先，把设计稿倍率限制为辅助工具并设上下限。方案选择按 4.1 的表自上而下：响应式布局、WindowSizeClass、受限显式缩放，最后才是覆盖 LocalDensity。Flutter 侧的 ScreenUtil 思路同源、同样不是官方默认推荐，对照仅作概念参照。

官方参考：

- [Compose 布局基础](https://developer.android.com/develop/ui/compose/layouts/basics)
- [使用窗口尺寸类别构建自适应布局](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
