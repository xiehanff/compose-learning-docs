# 第 9 章：Box、层叠布局与 Flutter Stack

## 1. 本章目标

理解 Box 的两个用途：作为单子项容器提供对齐，也作为多子项层叠容器实现覆盖布局。

学完后，你应当能够：

- 知道 Box 子项默认可以重叠
- 使用 contentAlignment 设置默认位置
- 使用 Modifier.align 单独定位子项
- 理解后写子项通常绘制在先写子项上方

## 2. 项目中的位置

- `components/BoxDemo.kt`
- `components/IndexedStackDemo.kt`



## 3. 项目源码片段

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(180.px),
) {
    Box(Modifier.matchParentSize().background(Color.LightGray))
    Text("左上", Modifier.align(Alignment.TopStart))
    Text("居中", Modifier.align(Alignment.Center))
    Text("右下", Modifier.align(Alignment.BottomEnd))
}
```

## 4. 逐步解释

Box 不会像 Column 或 Row 那样沿某个方向依次排开子项。多个子项会在同一块父区域内分别测量和放置，因此可以相互覆盖。`contentAlignment` 为所有没有单独指定位置的子项提供默认对齐；`Modifier.align` 则只改变当前子项。

绘制顺序通常与声明顺序一致：后声明的子项画在更上层。需要显式改变层级时可使用 `zIndex`。项目的 IndexedStack 示例正是把三个面板都放进同一个 Box，再通过 zIndex 控制可见层。

## 5. 实际执行过程

1. Box 接收父约束
2. 分别测量所有子项
3. 根据 contentAlignment 或子项 align 计算位置
4. 按声明顺序绘制
5. zIndex 较高的节点显示在上层

## 6. Flutter / Dart 3 对照

Flutter 中最接近的是 `Stack`。`Alignment` 对应 Stack 的 alignment，`Positioned` 更接近 Compose 的 offset 或自定义布局；Compose 的 `Modifier.align` 只能在 BoxScope 中使用。

```dart
Stack(
  alignment: Alignment.center,
  children: const [
    Positioned(top: 0, left: 0, child: Text('左上')),
    Text('居中'),
    Positioned(right: 0, bottom: 0, child: Text('右下')),
  ],
);
```

## 7. 常见错误

- 把 Box 当成 Flutter Container；Box 主要解决布局，不自带完整 decoration 参数
- 忘记固定或填充 Box 尺寸，导致可用于对齐的空间很小
- 在 BoxScope 外调用 Modifier.align
- 只用半透明遮盖未选中面板，却没有处理其点击事件

## 8. 本章检查题

1. Box 和 Column 在放置多个子项时最大的区别是什么？
2. 为什么 Modifier.align 只在 Box 的 content 中可用？
3. 如何确保遮罩层显示在内容之上？

答案：

1. Box 不沿某个方向依次排开子项，多个子项在同一块父区域内分别测量和放置，可以相互覆盖；Column 依次垂直排开。
2. align 是 BoxScope 提供的作用域 Modifier，只在 Box 的 content 中可用，用于给单个子项指定对齐。
3. 把遮罩子项声明在内容之后（绘制顺序通常与声明顺序一致），或显式使用 zIndex 提高层级。

## 9. 本章总结

Box 是 Compose 的基础层叠容器，对应 Flutter Stack 的核心能力。它既能统一对齐子项，也能让多个子项共享同一空间并控制层级。
