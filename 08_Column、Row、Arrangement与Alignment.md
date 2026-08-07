# 第 8 章：Column、Row、Arrangement 与 Alignment

## 1. 本章目标

掌握 Compose 两种最常用的线性布局，并从主轴、交叉轴两个方向理解排列和对齐。

学完后，你应当能够：

- 知道 Column 与 Row 的排列方向
- 区分 Arrangement 和 Alignment
- 看懂 spacedBy、CenterVertically、CenterHorizontally
- 理解子项级 Modifier.align 与容器级对齐

## 2. 项目中的位置

- `components/ColumnRowDemo.kt`
- `ui/home/HomeScreen.kt`



## 3. 项目源码片段

```kotlin
Column(
    verticalArrangement = Arrangement.spacedBy(12.px),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("左")
        Text("右")
    }
}
```

## 4. 逐步解释

`Column` 的主轴是垂直方向，所以主轴参数叫 `verticalArrangement`；交叉轴是水平方向，所以使用 `horizontalAlignment`。`Row` 正好相反：主轴是水平，使用 `horizontalArrangement`；交叉轴是垂直，使用 `verticalAlignment`。

`Arrangement.spacedBy(12.px)` 只负责相邻子项间距，不等于容器外侧 padding。`SpaceBetween` 会把剩余空间放到子项之间，首尾贴近容器边缘。`Alignment.CenterVertically` 则让 Row 中不同高度的子项沿垂直方向居中。

## 5. 实际执行过程

1. 父容器获得尺寸约束
2. 容器测量各个子项
3. 根据主轴 Arrangement 计算子项位置和间距
4. 根据交叉轴 Alignment 计算对齐位置
5. 按计算结果放置子项

## 6. Flutter / Dart 3 对照

Flutter 的 Column/Row 概念几乎一致：Compose 的 Arrangement 对应 MainAxisAlignment，Alignment 对应 CrossAxisAlignment。但 Compose 参数名会根据 Row/Column 的实际方向变化。

```dart
Column(
  children: [
    Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: const [Text('左'), Text('右')],
    ),
  ],
);
```

## 7. 常见错误

- 把 Row 的 verticalAlignment 理解为主轴排列
- 用 spacedBy 期待产生容器四周边距
- 在无限主轴空间中使用 SpaceBetween，却看不到明显效果
- 混淆容器整体对齐和单个子项的 align

## 8. 本章检查题

1. Column 的主轴是哪一个方向？
2. Row 中如何让三个子项间隔固定 8dp？
3. 为什么 SpaceBetween 必须有剩余空间才有明显效果？

答案：

1. 垂直方向，因此主轴参数叫 verticalArrangement；交叉轴是水平方向。
2. 使用 Arrangement.spacedBy(8.dp)（注意它只负责相邻子项间距，不等于容器外侧 padding）。
3. SpaceBetween 把剩余空间分配到子项之间；没有剩余空间时无处可分配，效果就不明显。

## 9. 本章总结

先确定容器的主轴，再选择 Arrangement；交叉轴使用 Alignment。Column 垂直排列，Row 水平排列，这是后续 weight、Spacer 和滚动布局的基础。
