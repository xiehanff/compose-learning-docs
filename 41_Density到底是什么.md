# 第 41 章：Density 到底是什么

## 1. 本章目标

从单位公式理解 Density 如何把 Dp 转成物理像素，并认识设计稿适配对无障碍和组件默认尺寸的影响。

学完后，你应当能够：

- 区分物理 px、Dp 和 density
- 掌握 px≈dp×density
- 理解 fontScale
- 知道覆盖 LocalDensity 会影响子树

## 2. 项目中的位置

- `ui/adapter/DesignWidthScale.kt`
- `ui/adapter/Px.kt`



## 3. 项目源码片段

> 警告：本组 41—45 章展示的是教学工程的实验性设计稿映射方案，不是 Compose 官方推荐的通用响应式适配方案。覆盖 `LocalDensity` 会连带改变普通 `dp`、Material 默认尺寸、系统触控目标、第三方 Compose 组件和部分文字换算。生产页面优先使用响应式布局、WindowSizeClass 和系统 `sp`；请先阅读第 45 章的风险与边界。

```kotlin
val scale = metrics.widthPixels / 750f
val designDensity = Density(
    density = scale,
    fontScale = fontScale,
)

// 近似关系
physicalPx = dpValue * density
```

## 4. 逐步解释

设备最终绘制物理像素，Compose 布局通常使用 Dp。Density.density 是二者换算倍率：density=3 时，100.dp 约为 300px。

项目将 density 设置为 `屏幕宽物理像素 / 750`。1080px 宽屏得到 1.44，750.dp 会换算成 1080px。项目扩展名叫 `.px`，返回类型其实仍是 Dp；只是新的 Density 让设计稿上的数字按屏宽解释（设计稿适配详见第 43 章）。这会把所有普通 Dp 一起带入缩放，不能只理解为“设计稿数字转换器”。fontScale 单独表示用户系统字体倍率，不能简单忽略；如果为了还原设计稿而除以 fontScale，也是在尺寸一致性和无障碍字体放大之间做取舍，不等于完整支持无障碍。

## 5. 实际执行过程

1. 读取屏幕宽物理像素
2. 计算 width/750
3. 创建自定义 Density
4. 子树测量 Dp 时读取该倍率
5. 转换为物理像素绘制

## 6. Flutter / Dart 3 对照

Flutter 布局单位是逻辑像素，devicePixelRatio 决定逻辑像素到物理像素；设计稿缩放通常另算 scale，而不是替换全局 DPR。

```dart
final size = MediaQuery.sizeOf(context);
final scale = size.width / 375.0;
final width = 100 * scale;
```

## 7. 常见错误

- 把全局覆盖 Density 当成生产项目的通用适配方案
- 在 Material 页面、第三方组件、混合 View/Compose 页面或多窗口场景中无边界使用它
- 认为 1.dp 永远等于 1px
- 把 density 当成分辨率
- 覆盖 Density 后忽略 Material 默认 dp 也被缩放
- 把 fontScale 固定为 1

## 8. 本章检查题

1. 1080/750 的 scale 是多少？
2. 100.px 最终如何换算？
3. density 和 fontScale 各负责什么？

答案：

1. scale = 1080 / 750 ≈ 1.44。
2. 100.px 返回的是 Dp，真正的物理像素换算发生在布局测量阶段，由当前 Density 决定（1.44 倍率下约 144px）。
3. density 负责 dp 与 px 之间的换算倍率；fontScale 单独表示用户系统字体倍率，影响文字显示。

## 9. 本章总结

Density 是单位换算环境。项目通过替换它实现设计稿映射，但影响的是整个子树的所有 Dp；这是一种有明确代价的教学方案，不应取代生产项目的响应式布局和无障碍策略。
