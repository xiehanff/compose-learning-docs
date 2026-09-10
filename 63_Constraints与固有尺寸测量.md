# 第 63 章：Constraints 与固有尺寸测量

## 1. 本章目标

上一章我们用了 `constraints`，但没细讲它是什么。本章解决「约束如何从父传到子，以及受限时如何做决策」。

学完后，你应当能够：

- 理解 Constraints 的四要素与常用构造方式
- 用 `BoxWithConstraints` 在组合阶段做响应式决策
- 理解固有尺寸测量（Intrinsic）的用途与代价
- 在自定义布局中正确地约束子项

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.unit.Constraints`
- `androidx.compose.foundation.layout.BoxWithConstraints`
- `androidx.compose.ui.layout.Measurable`（含 Intrinsic 系列方法）
- `androidx.compose.ui.layout.MeasurePolicy`

## 3. 项目源码片段

场景一：面板宽度小于 600dp 时竖排，否则横排——用 BoxWithConstraints 在组合阶段做响应式决策。

<!-- compile -->
```kotlin
@Composable
fun ResponsivePanel() {
    BoxWithConstraints {
        val narrow = maxWidth < 600.dp
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("窄屏：纵向排列")
                Button(onClick = {}) { Text("按钮 A") }
                Button(onClick = {}) { Text("按钮 B") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("宽屏：横向排列")
                Button(onClick = {}) { Text("按钮 A") }
                Button(onClick = {}) { Text("按钮 B") }
            }
        }
    }
}
```

场景二：自定义 Layout 中给子项收紧约束——期望 300px 宽，但必须夹在父约束的合法区间内。

<!-- compile
prelude: @Composable fun Child() {}
-->
```kotlin
Layout(content = { Child() }) { measurables, constraints ->
    val child = measurables.first()
    // coerceIn：期望值与约束冲突时取边界值
    val targetWidth = 300.coerceIn(constraints.minWidth, constraints.maxWidth)
    val placeable = child.measure(
        constraints.copy(minWidth = 0, minHeight = 0, maxWidth = targetWidth)
    )
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
```

## 4. 逐步解释

`Constraints` 是布局阶段父级传给子级的「允许范围」，四要素：`minWidth`、`maxWidth`、`minHeight`、`maxHeight`。子项测量时必须返回一个落在该范围内的尺寸，越界会被强制夹取。

常用构造：`Constraints.fixed(width, height)`（min == max，即「必须这么大」）；`Constraints.finite` 相关工厂（比如限定最大宽高）；`Constraints.Infinity` 是常量 `Int.MAX_VALUE`，表示「不限」。注意父级给子级无限约束时，子项若自身内容有限一般没问题，但子项向孙子传递时要注意别把 Infinity 直接当具体值用。

冲突处理：当期望值超出约束范围（比如想 300 但最大只有 240），用 `coerceIn(min, max)` 夹取，或者用 `constraints.constrainWidth(value)` / `constraints.constrain(IntSize)` 直接得到夹取后的结果。约束本身不会「报错」，只会让你拿到被压缩的值，所以决策逻辑里要处理这种压缩。

`BoxWithConstraints` 与普通 Box 的区别：它在**组合阶段**就能读到 `maxWidth`/`maxHeight`/`minWidth` 等约束值（单位是 Dp），可以直接用于 if 分支、remember 计算。注意它读的是约束而非测量结果——它在组合阶段做响应式决策，适合「宽了就横排、窄了就竖排」这种分支，但拿不到精确的像素测量值。

固有尺寸测量（Intrinsic）：`measurable.minIntrinsicWidth(height)`、`maxIntrinsicWidth`、`minIntrinsicHeight` 等，允许父级在真正测量子项**之前**先「估算」子项在给定尺寸下至少/至多需要多大。典型场景是「父尺寸要包裹子内容，但子内容又依赖父约束」的两难（比如宽度 wrap 的 Column 里放一个高度无限的文本）。代价是 Intrinsic 测量要额外跑一遍测量逻辑，性能差，能避免就避免。

记住：**约束是父给子的边界，夹取是默认行为；Intrinsic 是测量前的估算，贵，慎用。**

## 5. 实际执行过程

1. 组合阶段执行 BoxWithConstraints，接收父级约束并记录 min/max
2. `maxWidth < 600.dp` 分支成立，组合对应的竖排/横排内容
3. 布局阶段父级把约束传给自定义 Layout
4. Layout 内用 coerceIn / constrainWidth 处理冲突后传给子项
5. 子项在约束内测量并返回尺寸，结果沿链向上汇报
6. 需要估算的父级先走 Intrinsic 通道，再进入正式测量

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 也有「组合阶段读约束」的等价物，就是 LayoutBuilder。

```dart
LayoutBuilder(
  builder: (context, constraints) {
    if (constraints.maxWidth < 600) {
      return const Column(children: [Text('窄屏：纵向排列')]);
    }
    return const Row(children: [Text('宽屏：横向排列')]);
  },
)
```

本质区别：Flutter 的 `BoxConstraints` 带 tight / loose 语义——tight 约束（min == max）意味着「只能这么大」，loose 约束（min 为 0）意味着「可以小」。Compose 的 Constraints 没有 tight/loose 这对术语，统一用 min/max 表达，`Constraints.fixed` 就是「min == max」的固定尺寸，语义上更直观、少一层概念。另外 Flutter 的 RenderObject 有 `computeMinIntrinsicWidth` 等对应方法，但日常几乎不用；Compose 的 Intrinsic 在自定义布局里更常见。

记住：**Compose 的约束只有 min/max，没有 tight/loose 的概念包袱。**

## 7. 常见错误

1. 给子项传了自己都满足不了的约束：比如 min > max、负值、或者把 Infinity 直接传给 `Constraints.fixed`，测量结果会异常或抛错。
2. 只处理 maxWidth 忽略 minWidth：约束是 min/max 成对的，父级给固定尺寸（min == max）时，忽略 min 会导致子项尺寸算错。
3. 在测量阶段调用组合函数：测量阶段不允许发起组合，会直接报错；组合只能在组合阶段做。
4. 滥用 Intrinsic：每次布局 Intrinsic 都要额外跑一遍测量，嵌套使用会成倍放大开销，卡顿的常见来源。
5. 在 BoxWithConstraints 里做需要精确像素的计算：它给的是约束（Dp），不是测量结果，精确尺寸仍要等布局阶段。

## 8. 本章检查题

1. Constraints 的四要素是什么？
2. BoxWithConstraints 在哪个阶段读取约束？
3. 固有尺寸测量（Intrinsic）何时使用？代价是什么？

答案：

1. `minWidth`、`maxWidth`、`minHeight`、`maxHeight`。
2. 组合阶段——它在组合时就能读到 Dp 单位的约束值，用于响应式分支，而不是在测量阶段。
3. 父级需要在真正测量子项之前估算其尺寸（如 wrap 内容且子项依赖约束的两难场景）时使用；代价是每次要额外跑一遍测量，性能差，应尽量少用。

## 9. 本章总结

Constraints 用 min/max 四个值把父级的「允许范围」传给子项，子项必须在范围内测量，冲突用 coerceIn 或 constrain 系列夹取。组合阶段的响应式决策交给 BoxWithConstraints，测量前的尺寸估算才用 Intrinsic。理解约束的下行传递，自定义布局才能写得正确。
