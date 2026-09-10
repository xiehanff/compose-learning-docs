# 第 18 章：LazyVerticalGrid 与 GridCells

## 1. 本章目标

掌握 Compose 懒网格的列规则、间距和条目生成方式。

学完后，你应当能够：

- 使用 GridCells.Fixed 固定列数
- 理解 GridCells.Adaptive 按最小格宽自适应
- 配置横向与纵向间距
- 理解网格同样是懒组合容器

## 2. 项目中的位置

- `components/GridViewDemo.kt`



## 3. 项目源码片段

<!-- compile -->
```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(3),
    modifier = Modifier
        .fillMaxWidth()
        .height(208.px),
    verticalArrangement = Arrangement.spacedBy(8.px),
    horizontalArrangement = Arrangement.spacedBy(8.px),
) {
    items(9) { index ->
        Box(Modifier.height(64.px)) { Text("$index") }
    }
}
```

## 4. 逐步解释

`GridCells.Fixed(3)` 表示交叉轴固定分成 3 列，扣除列间距后平均分配每列宽度。`GridCells.Adaptive(minSize)` 则根据可用宽度尽可能放入更多列，但保证每列不小于给定最小宽度。

LazyVerticalGrid 的 content 同样是懒布局 DSL。`items(9)` 直接按数量创建 9 个索引条目；真实项目也可传对象集合并提供 key。项目给网格固定高度，是因为整个 Demo 本身位于详情页 LazyColumn 中；明确高度可以避免同方向嵌套懒布局出现无限测量问题。

## 5. 实际执行过程

1. 网格获得有限宽度
2. GridCells 计算列数和每列宽度
3. 根据滚动位置确定可见行
4. 组合可见格子
5. Arrangement 应用行列间距

## 6. Flutter / Dart 3 对照

Flutter 的 GridView.builder + SliverGridDelegateWithFixedCrossAxisCount 对应 Fixed；SliverGridDelegateWithMaxCrossAxisExtent 更接近 Adaptive。

```dart
GridView.builder(
  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
    crossAxisCount: 3,
    mainAxisSpacing: 8,
    crossAxisSpacing: 8,
  ),
  itemCount: 9,
  itemBuilder: (context, index) => Center(child: Text('$index')),
);
```

## 7. 常见错误

- 在垂直 LazyColumn 中嵌套没有有限高度的 LazyVerticalGrid
- 把 Adaptive 的参数误解为固定列宽而不是最小尺寸
- 只设置 verticalArrangement，忘记列间距
- 大数据网格使用 Column + 多个 Row 手工一次性创建

## 8. 本章检查题

1. Fixed(3) 在宽屏上列数会变化吗？
2. Adaptive(100.dp) 的 100dp 表达什么？
3. 为什么项目给嵌套网格固定 height？

答案：

1. 不会。Fixed(3) 固定分成 3 列，扣除列间距后平均分配每列宽度。
2. 每列的最小宽度；网格会根据可用宽度放入尽可能多的列。
3. 网格嵌套在详情页的 LazyColumn 中，固定高度可以避免同方向嵌套懒布局出现无限测量问题。

## 9. 本章总结

LazyVerticalGrid 是懒加载网格；Fixed 控制固定列数，Adaptive 根据最小格宽自适应。嵌套在同方向滚动容器时必须认真处理尺寸约束。
