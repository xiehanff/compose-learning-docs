# 第 14 章：weight、Spacer 与 Flutter Expanded

## 1. 本章目标

掌握 Row/Column 中剩余空间的权重分配，并准确理解 Compose 为什么没有单独的 Expanded 组件。

学完后，你应当能够：

- 理解 weight 只在 RowScope/ColumnScope 内使用
- 计算 1:2:1 权重比例
- 知道固定尺寸先测量、剩余空间再按权重分配
- 使用 Spacer 或空 Box 推开元素

## 2. 项目中的位置

- `components/ExpandedDemo.kt`
- `ui/home/HomeScreen.kt`



## 3. 项目源码片段

<!-- compile -->
```kotlin
Row(Modifier.fillMaxWidth()) {
    Box(Modifier.weight(1f).height(64.px))
    Box(Modifier.weight(2f).height(64.px))
    Box(Modifier.weight(1f).height(64.px))
}

Row(Modifier.fillMaxWidth()) {
    Text("左")
    Spacer(Modifier.weight(1f))
    Text("右")
}
```

## 4. 逐步解释

Row 会先处理没有 weight 的子项和自身固定开销，再把主轴剩余宽度按照权重总和分配。1 + 2 + 1 的总权重是 4，因此三段分别得到剩余空间的 1/4、2/4、1/4。Column 中同理，只是分配的是剩余高度。

`weight` 是 RowScope/ColumnScope 提供的作用域 Modifier，所以不能在普通 Box 内容或任意函数中直接使用。项目首页 `Column(Modifier.weight(1f))` 让文字区域吃掉剩余宽度，从而把箭头挤到右侧。只需要弹性空白时使用 `Spacer(Modifier.weight(1f))` 最能表达意图。

## 5. 实际执行过程

1. 容器测量非权重子项
2. 扣除固定尺寸和间距
3. 计算全部 weight 总和
4. 按比例给权重子项分配剩余主轴空间
5. 放置全部子项

## 6. Flutter / Dart 3 对照

Flutter 的 Expanded(flex: n) 与 Modifier.weight(n.toFloat()) 语义接近；Flutter Spacer(flex: n) 对应 Compose Spacer(Modifier.weight(...))。不同点是 Compose 用 Modifier 而不是额外包装组件。

```dart
Row(
  children: const [
    Expanded(flex: 1, child: ColoredBox(color: Colors.blue)),
    Expanded(flex: 2, child: ColoredBox(color: Colors.green)),
    Expanded(flex: 1, child: ColoredBox(color: Colors.blue)),
  ],
);
```

## 7. 常见错误

- 在没有有限主轴空间的滚动容器里使用 weight
- 以为 weight(2f) 表示固定 2 倍屏幕宽
- 在 RowScope 外调用 weight 导致无法解析
- 用 weight 解决本应由 Arrangement.SpaceBetween 解决的简单分布，却增加不必要布局

## 8. 本章检查题

1. 1:2:1 的三个子项各得到剩余空间多少比例？
2. 固定 100dp 子项是否参与权重比例？
3. 首页 EntryCard 为什么给左侧 Column 设置 weight(1f)？

答案：

1. 总权重为 4，三个子项分别得到剩余空间的 1/4、2/4、1/4。
2. 不参与。固定尺寸子项先被分配，weight 只分配剩余空间。
3. 让文字区域吃掉剩余宽度，把箭头图标挤到卡片右侧。

## 9. 本章总结

weight 是线性布局中对“剩余主轴空间”的比例分配，不是绝对尺寸。Compose 用 Modifier.weight 实现 Flutter Expanded，并可配合 Spacer 表达弹性空白。
