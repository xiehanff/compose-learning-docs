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
- `components/ButtonDemo.kt`
- `components/AlertDialogDemo.kt`



## 3. 项目源码片段

```kotlin
// components/ButtonDemo.kt：五种风格按钮，按视觉权重从高到低排列
Button(onClick = {}) { Text("Button（Filled）→ 主操作") }              // 主操作，视觉权重最高
FilledTonalButton(onClick = {}) { Text("FilledTonalButton → 次要操作") } // 低饱和次级实心
OutlinedButton(onClick = {}) { Text("OutlinedButton → 中等权重") }       // 描边，中等权重
TextButton(onClick = {}) { Text("TextButton → 最低权重") }             // 纯文字，权重最低
ElevatedButton(onClick = {}) { Text("ElevatedButton → 浮在内容之上") }  // 带阴影，适合浮在内容之上

// 可用 / 禁用对照：enabled = false 时按钮自动变灰且点不动
Button(onClick = {}) { Text("可用状态") }
Button(onClick = {}, enabled = false) { Text("禁用状态") }

// IconButton + Icon：图标随状态切换
IconButton(onClick = { liked = !liked }) {
    Icon(
        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = if (liked) "取消收藏" else "收藏",
        tint = if (liked) Color(0xFFE57373) else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// 项目其它位置（详情页返回键）
IconButton(onClick = onBack) {
    Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
}
```

## 4. 逐步解释

Button 自带 Material 形状、颜色、最小触控尺寸、禁用状态和按钮语义。IconButton 专门承载图标型操作，并提供合适的点击范围。Icon 自身只是读取 ImageVector 并绘制，没有 onClick 参数。

Material3 把按钮按「视觉权重」分成五个层级，它们的调用签名相同（都是 `onClick` + 尾随内容 lambda），共享核心参数，区别在默认颜色、边框、阴影、形状和内边距等默认参数上：`Button`（Filled 实心）是页面最高优先级的主操作，一屏通常只放一个；`FilledTonalButton` 是低饱和的次级实心按钮，适合「保存草稿」这类次要动作；`OutlinedButton` 是描边按钮，中等权重，浅色背景上更清爽；`TextButton` 是纯文字按钮，权重最低，常用于「取消」「跳过」；`ElevatedButton` 带阴影，适合浮在图片或卡片之上。

启用与禁用统一用 `enabled` 参数控制：`enabled = false` 时按钮自动变灰、点击无效，无需自己画禁用配色。`ButtonDemo.kt` 把可用态和禁用态并排放在一个 `Row` 里对照，就是为了看清这个差异。

内容 lambda 里可以放任意 Composable。示例用 `if (clickCount == 0) ... else ...` 让按钮文字随计数状态变化，直观看到「状态驱动 UI」：点击后 `clickCount++`、状态变化、按钮文字重绘，不需要手动刷新任何东西。

contentDescription 服务 TalkBack 等无障碍工具。图标承载独立信息时应写有意义描述；如果父级按钮已经提供完整语义，纯装饰 Icon 可以传 null，避免重复朗读。`ButtonDemo.kt` 的爱心图标把「收藏 / 取消收藏」写在 contentDescription 里并随状态切换，是对的做法。除此之外，`Icon` 还可以用 `tint` 染色：示例在收藏时把爱心染成红色，未收藏时使用主题的次要色。

不要给 IconButton 外层再加 clickable，否则可能产生两层点击和语义冲突。

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
- 一屏堆多个 `Button`（Filled）争当主操作，没利用 `FilledTonalButton` / `OutlinedButton` / `TextButton` 拉开权重
- 禁用态用 `alpha` 或自定义颜色自己模拟，而不用 `enabled = false`
- 收藏类图标切换时只换 `imageVector`，忘了同步改 `contentDescription`，TalkBack 仍读旧语义

## 8. 本章检查题

1. Icon 与 IconButton 的职责分别是什么？
2. 返回图标的触控区域由谁提供？
3. 何时可将 contentDescription 设为 null？
4. Material3 有哪五种按钮风格？禁用态怎么实现？

答案：

1. Icon 只读取 ImageVector 并绘制，自身没有点击能力；IconButton 承载图标型操作并提供合适的点击范围。
2. IconButton 自带的最小触控区域（Material 默认触控尺寸）。
3. 图标是纯装饰，或父级按钮已经提供完整语义时，避免 TalkBack 重复朗读。
4. `Button`（Filled 实心）、`FilledTonalButton`（低饱和次级实心）、`OutlinedButton`（描边）、`TextButton`（纯文字）、`ElevatedButton`（带阴影）。禁用态用 `enabled = false`，按钮自动变灰且点击无效。

## 9. 本章总结

优先使用语义正确的 Material 交互组件。Button/IconButton 处理交互，Icon 只绘制，二者职责不能混淆。
