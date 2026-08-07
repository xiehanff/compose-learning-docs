# 第 45 章：与 Flutter 设计稿适配的对比、风险与建议

## 1. 本章目标

评估全局 Density 适配方案的优缺点，并建立生产项目的响应式优先原则。

学完后，你应当能够：

- 对比全局覆盖与显式缩放
- 识别平板、横屏、分屏风险
- 保护系统字体和最小触控尺寸
- 优先使用响应式布局和窗口分类

## 2. 项目中的位置

- `ui/adapter/DesignWidthScale.kt`
- `ui/adapter/Px.kt`
- `components/ScreenAdapterDemo.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

覆盖 LocalDensity 的优点是设计稿数字使用直接，所有尺寸统一缩放。最大风险也是影响过大：普通 dp、Material 默认尺寸、第三方组件、触控区域和文本换算全部改变。平板可能出现巨大控件，横屏可能因按宽度放大而没有垂直空间，分屏会让全界面连续缩放。

Flutter ScreenUtil 常用显式 `.w/.sp`，标准 Widget 默认逻辑像素不被全局重定义，影响相对局部。但任何框架都不应把“设计稿同比放大”当成真正适配。生产建议优先用约束、weight、WindowSizeClass、断点和响应式结构；缩放只用于少量视觉尺寸并设置上下限；文字尊重系统 fontScale；覆盖手机、平板、横屏、分屏和大字体测试。

## 5. 实际执行过程

1. 判断窗口类别
2. 先确定响应式结构
3. 必要时计算受限倍率
4. 仅缩放明确设计尺寸
5. 保留标准组件和字体语义
6. 多设备和无障碍验证

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

## 7. 常见错误

- 追求所有设备像截图同比放大
- 字体按宽度缩放而忽略大字体
- 平板和横屏没有倍率上限
- 第三方组件异常时没意识到 Density 被改写
- 同时使用多套单位规则

## 8. 本章检查题

1. 覆盖 Density 最大的作用范围风险是什么？
2. 平板为何不应同比放大所有控件？
3. 生产适配应优先倍率还是响应式布局？

## 9. 本章总结

当前方案适合教学和强设计稿还原；生产环境应以响应式结构和无障碍为先，把设计稿倍率限制为辅助工具。
