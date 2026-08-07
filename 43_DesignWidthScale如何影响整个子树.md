# 第 43 章：DesignWidthScale 如何影响整个子树

## 1. 本章目标

沿根组合调用链理解一次 Density 覆盖为何影响主题、导航和全部 Demo。

学完后，你应当能够：

- 画出包裹关系
- 理解后代 Dp 测量读取新 Density
- 理解 remember(scale,fontScale)
- 知道读取原始 displayMetrics 的原因

## 2. 项目中的位置

- `MainActivity.kt`
- `ui/adapter/DesignWidthScale.kt`



## 3. 项目源码片段

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

## 4. 逐步解释

DesignWidthScale 位于 DemoTheme 和 DemoApp 外层，它的 content 包含导航图、首页、详情页和全部组件示例。后代的 Dp 在测量时读取最近 LocalDensity，因此都会按设计稿 scale 转换。

`remember(scale, fontScale)` 在屏幕宽或系统字体倍率变化时重新创建 Density。代码从 resources.displayMetrics 读取原始参数，而不是使用可能已经被覆盖的 LocalDensity，避免嵌套适配时根据已缩放值再次计算。旋转或分屏时 widthPixels 改变，整个子树换算规则也会变化。

## 5. 实际执行过程

1. 根组合进入 DesignWidthScale
2. 计算 scale
3. Provider 包住后代
4. 后代声明 Dp
5. 测量读取自定义 Density
6. 窗口变化时更新 scale

## 6. Flutter / Dart 3 对照

Flutter 常在根部提供 ScreenUtil/自定义 Scale，再由 `.w` 显式读取。覆盖 Density 的影响更彻底。

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

## 8. 本章检查题

1. 为什么 NavHost 也受适配影响？
2. 旋转后哪个值变化？
3. 为什么不从已覆盖 LocalDensity 反推屏宽？

答案：

1. NavHost 位于 DesignWidthScale 的子树内，后代测量时读取最近的 LocalDensity，所以导航、页面全部受适配影响。
2. 屏幕宽 widthPixels 变化（旋转、分屏）时 scale 变化，整个子树的换算规则随之变化。
3. LocalDensity 已被覆盖，用它反推会得到已缩放的值，导致嵌套适配时二次计算。

## 9. 本章总结

DesignWidthScale 不是逐个修改组件，而是改变整个后代树解析 Dp 的共同环境。
