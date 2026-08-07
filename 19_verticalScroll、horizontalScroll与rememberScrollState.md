# 第 19 章：verticalScroll、horizontalScroll 与 rememberScrollState

## 1. 本章目标

理解普通布局如何通过滚动 Modifier 获得滚动能力，以及 ScrollState 保存什么。

学完后，你应当能够：

- 为 Column 添加 verticalScroll
- 为 Row 添加 horizontalScroll
- 理解 rememberScrollState 保存滚动位置和状态
- 知道普通滚动容器会组合全部子项

## 2. 项目中的位置

- `components/ScrollViewDemo.kt`



## 3. 项目源码片段

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .height(220.px)
        .verticalScroll(rememberScrollState()),
) {
    repeat(20) { index -> Text("Item $index") }
}

Row(
    Modifier.horizontalScroll(rememberScrollState())
) { /* 宽内容 */ }
```

## 4. 逐步解释

`verticalScroll` 是 Modifier，它让一个普通布局能够在内容尺寸超过视口时沿垂直方向偏移。它需要 `ScrollState` 保存当前滚动值、最大值以及滚动进行状态。使用 `rememberScrollState()` 可让同一次组合生命周期内重组后继续保持位置。

与 LazyColumn 不同，Column 中的所有子项仍会一次性组合和测量，只是最终通过滚动偏移查看不同区域。因此它适合数量少、结构固定、需要把一个整体页面滚动起来的场景。水平滚动同理，只是常搭配 Row。

## 5. 实际执行过程

1. remember 创建 ScrollState
2. 普通 Column/Row 组合全部内容
3. 测量得到大于视口的内容尺寸
4. ScrollState 限制偏移范围
5. 手势改变 scroll value，布局按偏移绘制

## 6. Flutter / Dart 3 对照

Flutter 最接近 SingleChildScrollView + Column/Row，ScrollController 对应更强的 ScrollState/控制能力。两者都不适合无界大量条目。

```dart
SingleChildScrollView(
  scrollDirection: Axis.vertical,
  child: Column(
    children: List.generate(20, (i) => Text('Item $i')),
  ),
);
```

## 7. 常见错误

- 给一个没有有限视口尺寸的 Column 加 verticalScroll，却期待内部形成独立滚动区
- 大量数据仍使用 repeat + Column
- 同方向滚动容器嵌套导致手势和测量问题
- 每次重组手动创建新的 ScrollState，位置不断重置

## 8. 本章检查题

1. rememberScrollState 主要保存什么？
2. verticalScroll 是否让 Column 变成懒加载？
3. 20 个固定设置项页面更适合 Column+verticalScroll 还是 LazyColumn？

答案：

1. 当前滚动值、最大值以及滚动进行状态，使重组后能继续保持位置。
2. 不会。Column 中的所有子项仍会一次性组合和测量，只是通过滚动偏移查看不同区域。
3. Column + verticalScroll。数量少、结构固定，不需要懒加载。

## 9. 本章总结

verticalScroll/horizontalScroll 给普通布局增加滚动，但不会增加懒组合。ScrollState 保存位置；数据很多时应优先使用 Lazy 容器。
