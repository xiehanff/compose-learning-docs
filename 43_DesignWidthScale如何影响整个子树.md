# 第 43 章：DesignWidthScale 如何影响整个子树

## 1. 本章目标

先看一个现象：整个 App 的页面、导航和全部组件示例都没改一行尺寸代码，但在不同手机上整体比例都接近设计稿——原因在 `MainActivity` 里那几行：一个 `DesignWidthScale` 包住了整个 `DemoApp`。它凭什么影响整棵树？

沿根组合调用链理解一次 Density 覆盖为何影响主题、导航和全部 Demo。先划清边界：**DesignWidthScale 是原 Demo 的实验性方案，不是 Compose 官方 API**；Compose 没有内置的"按设计稿宽自动缩放"开关。本章把它的计算链和副作用算清楚。

学完后，你应当能够：

- 画出包裹关系
- 会算"设计稿宽 → 缩放系数 → 新 density → 物理像素"的完整计算链
- 理解后代 Dp 测量读取新 Density
- 理解 remember(scale,fontScale)
- 知道读取原始 displayMetrics 的原因
- 能列出全局覆盖 Density 的至少三类副作用

## 2. 项目中的位置

- `MainActivity.kt`
- `ui/adapter/DesignWidthScale.kt`

原工程未随本仓库分发，路径仅用于说明来源（见 README）；本章代码分两类：标注"项目节选"的来自原工程文档保留的片段，标注"独立示例"的可复制编译。

## 3. 项目源码片段

项目节选（教学推演，不可直接复制：`DemoTheme`、`DemoApp`、`designDensity`、`content()` 是原工程符号）：

```kotlin
setContent {
    DesignWidthScale {
        DemoTheme {
            DemoApp()
        }
    }
}

CompositionLocalProvider(LocalDensity provides designDensity) {
    content()
}
```

### 3.1 独立示例：手动覆盖 LocalDensity，观察子树 dp 变化

参与编译校验，不依赖原工程。用第 42 章的机制手动提供一份新 Density，验证"覆盖内 / 覆盖外"的换算差异。

<!-- compile -->
```kotlin
// 纸面设备（第 41 章）：屏宽 1080px、density 2.75 → 可用宽 1080 / 2.75 ≈ 392.7dp
// 设计稿宽 375：系数 = 392.7 / 375 ≈ 1.047；新 density = 2.75 × 1.047 ≈ 2.88
val deviceDensity = LocalDensity.current
val factor = 1.047f
val designDensity = Density(deviceDensity.density * factor, fontScale = deviceDensity.fontScale)
CompositionLocalProvider(LocalDensity provides designDensity) {
    Column {
        // 375.px 就是 375.dp（第 44 章）：375 × 2.88 ≈ 1080px，纸面设备上恰好铺满屏宽
        Box(Modifier.size(375.px).background(Color(0xFF90CAF9)))
        // Material 组件内部用 dp 标注的默认尺寸（按钮高度、内边距等）也按同一份 Density 换算
        Button(onClick = {}) { Text("我也被缩放") }
        Text("覆盖内 100dp ≈ ${with(designDensity) { 100.dp.roundToPx() }}px")
        Text("覆盖外 100dp ≈ ${with(deviceDensity) { 100.dp.roundToPx() }}px")
    }
}
```

预期观察：蓝条铺满可用宽度；两行文字分别报告约 288px 与 275px（纸面设备数值；真机数字随实际 density 变化，但"覆盖内按新密度换算"的关系不变——100 × 2.75 × 1.047 ≈ 287.9）。

## 4. 逐步解释

### 4.1 计算链：从设计稿宽到物理像素

DesignWidthScale 位于 DemoTheme 和 DemoApp 外层，它的 content 包含导航图、首页、详情页和全部组件示例。后代的 Dp 在测量时读取最近 LocalDensity（第 42 章），因此都会按设计稿 scale 转换。以纸面设备（第 41 章：屏宽 1080px、density 2.75、可用宽 ≈ 392.7dp）和 375 宽设计稿为例：

| 步骤 | 计算 | 数值 |
| --- | --- | --- |
| 设计稿宽 | — | 375（1 倍稿） |
| 设备可用 dp 宽 | 1080 ÷ 2.75 | ≈ 392.7dp |
| 缩放系数 | 392.7 ÷ 375 | ≈ 1.047 |
| 新 density | 2.75 × 1.047 | ≈ 2.88 |
| 设计稿 375 → 物理 | 375dp × 2.88 | ≈ 1080px（满宽） |

链条的含义：覆盖 LocalDensity 后，子树里所有 dp 都按新 density 换算；设计稿坐标经 `.px` 包装成 dp（第 44 章），线性放大到恰好满宽——视觉上设计稿比例复现。这不是逐个组件改尺寸，而是改变整棵后代树解析 Dp 的共同换算环境。

### 4.2 原项目的写法与等价性

原项目节选直接用 `metrics.widthPixels / 750f` 替换 density：750 是 2 倍设计稿宽，1080 ÷ 750 = 1.44，此时 750.dp 换算成 1080px 满宽。它与 4.1 的"375 基准 × 系数"数学上等价：把可用 dp 宽展开成 `物理宽 ÷ density`，`density × (可用dp宽 / 设计dp宽) = 物理宽 / 设计像素宽`；750 稿上同一元素的数值恰是 375 稿的 2 倍，两条路算出的物理像素一致。教学简化：原工程不在本仓库，以上按第 41、44 章留存片段推演，完整实现无法核对。

### 4.3 remember(scale, fontScale) 与原始 displayMetrics

`remember(scale, fontScale)` 在屏幕宽或系统字体倍率变化时重新创建 Density。代码从 resources.displayMetrics 读取原始参数，而不是使用可能已经被覆盖的 LocalDensity，避免嵌套适配时根据已缩放值再次计算。旋转或分屏时 widthPixels 改变，整个子树换算规则也会变化。

### 4.4 副作用清单

覆盖的是整棵子树的换算环境，影响不可能只落在"设计稿数字"上：

1. **Material 组件内部尺寸一起缩放**：按钮高度、间距、图标尺寸等内部 dp 同按新 density 换算（3.1 的 Button），组件库的视觉规范被整体拉伸或压缩。
2. **最小触控目标漂移**：系统 48dp 触控基准的物理大小本应约 48/160 英寸 ≈ 7.6mm（教学简化，实际 dpi 报告值有偏差）。纸面设备按 750 稿覆盖后 density = 1.44：48dp → 48 × 1.44 = 69px，69 ÷ 440 英寸 ≈ 4.0mm，缩水近半；反方向在平板上放大时又远超基准。无障碍风险见第 80 章。
3. **多窗口 / 分屏失配**：系数按计算时的窗口宽得出；分屏后窗口变窄，若未触发重建则换算失配，即便重建，整树连续缩放的体验也差（第 45 章）。
4. **与系统字体缩放的交互**：fontScale 被原样带进新 Density，但 `.pxSp()` 又把它除掉（第 44 章），叠加之后文字行为难以向用户解释。

## 5. 实际执行过程

1. 根组合进入 DesignWidthScale
2. 计算 scale
3. Provider 包住后代
4. 后代声明 Dp
5. 测量读取自定义 Density
6. 窗口变化时更新 scale

## 6. Flutter / Dart 3 对照

Flutter 常在根部提供 ScreenUtil/自定义 Scale，再由 `.w` 显式读取。覆盖 Density 的影响更彻底：Flutter 侧系数只在显式调用 `.w` / `.sp` 的地方生效，`devicePixelRatio` 与 Widget 的逻辑像素不被重定义；DesignWidthScale 换掉的是整棵子树的换算环境，连没用设计稿单位的组件一起变。

```dart
ScreenScale(
  designWidth: 375,
  child: MaterialApp(home: const HomePage()),
);
```

## 7. 常见错误

- 只包局部组件却期待全局生效
- 横屏按宽度放大导致高度不足
- 嵌套多个适配容器
- 忽略第三方 Material 组件也受影响
- 忘了 DesignWidthScale 是项目方案，当成 Compose 官方 API 到处引用

错误示例（故意错误，不参与编译）：

```kotlin
// 错误示例：嵌套第二层适配——外层已把 density 换成屏宽/750，
// 内层再算一次系数等于全树二次缩放，所有 dp 被乘两遍
DesignWidthScale {
    DesignWidthScale {
        DemoApp()
    }
}
```

## 8. 本章检查题

1. 为什么 NavHost 也受适配影响？
2. 旋转后哪个值变化？
3. 为什么不从已覆盖 LocalDensity 反推屏宽？
4. 预测：设计稿宽 375，某平板可用宽 800dp，不设上限时系数是多少？48dp 触控目标等效放大到多少 dp？
5. 动手：把 3.1 的 factor 从 1.047f 改成 2f，运行观察蓝条宽度与按钮大小的变化。

答案：

1. NavHost 位于 DesignWidthScale 的子树内，后代测量时读取最近的 LocalDensity，所以导航、页面全部受适配影响。
2. 屏幕宽 widthPixels 变化（旋转、分屏）时 scale 变化，整个子树的换算规则随之变化。
3. LocalDensity 已被覆盖，用它反推会得到已缩放的值，导致嵌套适配时二次计算。
4. 系数 = 800 / 375 ≈ 2.13；48dp 等效放大到约 48 × 2.13 ≈ 102dp，界面整体巨大——这正是第 45 章反对平板同比放大的算例。
5. 纸面设备上蓝条宽度变成 375dp × (2.75 × 2) ≈ 2062px，远超屏宽被父级约束截断；按钮与其内部尺寸同步放大。同一数字的显示结果完全由 Provider 里的 density 决定。

## 9. 本章总结

DesignWidthScale 不是逐个修改组件，而是改变整个后代树解析 Dp 的共同环境：系数 = 可用 dp 宽 ÷ 设计稿宽，覆盖 LocalDensity 后全子树 dp 按新密度换算，设计稿比例得以复现。代价是 Material 默认尺寸、触控目标、多窗口与字体缩放一起卷入（4.4）。它是原 Demo 的实验性方案，生产环境的取舍见第 45 章。

官方参考：

- [Density API 参考（dp / sp 到 px 的换算定义）](https://developer.android.com/reference/kotlin/androidx/compose/ui/unit/Density)
- [androidx.compose.ui.platform 包参考（LocalDensity 所在包）](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary)
