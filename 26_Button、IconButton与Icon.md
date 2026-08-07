# 第 26 章：Button、IconButton 与 Icon

## 1. 本章目标

分清交互组件与绘制组件：Button、IconButton 负责点击和语义，Icon 只负责画图标。

学完后，你应当能够：

- 理解 Button 的 content Lambda
- 使用 IconButton 提供正确触控区域
- 为 Icon 提供合适无障碍描述
- 避免重复 clickable

## 2. 项目中的位置

- `ui/detail/ComponentDetailScreen.kt`
- `components/AlertDialogDemo.kt`



## 3. 项目源码片段

```kotlin
Button(onClick = onBack) {
    Text("返回首页")
}

IconButton(onClick = onBack) {
    Icon(
        Icons.Filled.ArrowBack,
        contentDescription = "返回",
    )
}
```

## 4. 逐步解释

Button 自带 Material 形状、颜色、最小触控尺寸、禁用状态和按钮语义。IconButton 专门承载图标型操作，并提供合适的点击范围。Icon 自身只是读取 ImageVector 并绘制，没有 onClick 参数。

contentDescription 服务 TalkBack 等无障碍工具。图标承载独立信息时应写有意义描述；如果父级按钮已经提供完整语义，纯装饰 Icon 可以传 null，避免重复朗读。不要给 IconButton 外层再加 clickable，否则可能产生两层点击和语义冲突。

## 5. 实际执行过程

1. 交互组件组合内部内容
2. Icon 绘制矢量路径
3. 用户点击命中 Button/IconButton
4. 组件处理按压状态和语义
5. onClick 回调执行

## 6. Flutter / Dart 3 对照

Flutter 的 ElevatedButton、IconButton、Icon 几乎一一对应。Icon 同样不是点击组件。

```dart
IconButton(
  onPressed: onBack,
  tooltip: '返回',
  icon: const Icon(Icons.arrow_back),
);

ElevatedButton(onPressed: onBack, child: const Text('返回首页'));
```

## 7. 常见错误

- 给 Icon 直接添加 onClick 参数
- IconButton 外再套 Gesture/clickable
- 图标没有 contentDescription 或 tooltip
- 用 Text.clickable 模拟所有按钮，丢失标准语义

## 8. 本章检查题

1. Icon 与 IconButton 的职责分别是什么？
2. 返回图标的触控区域由谁提供？
3. 何时可将 contentDescription 设为 null？

## 9. 本章总结

优先使用语义正确的 Material 交互组件。Button/IconButton 处理交互，Icon 只绘制，二者职责不能混淆。
