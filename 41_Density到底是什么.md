# 第 41 章：Density 到底是什么

## 1. 本章目标

从单位公式理解 Density 如何把 Dp 转成物理像素。

学完后，你应当能够：

- 区分物理 px、Dp 和 density
- 掌握 px≈dp×density
- 理解 fontScale
- 知道覆盖 LocalDensity 会影响子树

## 2. 项目中的位置

- `ui/adapter/DesignWidthScale.kt`
- `ui/adapter/Px.kt`



## 3. 项目源码片段

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

项目将 density 设置为 `屏幕宽物理像素 / 750`。1080px 宽屏得到 1.44，750.dp 会换算成 1080px。项目扩展名叫 `.px`，返回类型其实仍是 Dp；只是新的 Density 让设计稿数字按屏宽解释。fontScale 单独表示用户系统字体倍率，不能简单忽略。

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

Density 是单位换算环境。项目通过替换它实现设计稿映射，但影响的是整个子树的所有 Dp。
