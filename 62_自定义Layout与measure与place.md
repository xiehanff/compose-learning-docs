# 第 62 章：自定义 Layout：measure 与 place

## 1. 本章目标

Column、Row、Box 覆盖了 90% 的场景，但遇到「标签流式换行」「瀑布流双列」这类布局，标准容器就无能为力了。本章解决「自己实现布局算法」的问题。

学完后，你应当能够：

- 用 `Layout` 组合 + MeasurePolicy 实现自定义布局
- 理解 `measure` 返回 Placeable、`place` 决定位置
- 理解测量顺序（子测量结果影响父尺寸）的意义
- 知道布局阶段与绘制、命中测试是分离的

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.foundation.layout.Layout`
- `androidx.compose.ui.layout.MeasurePolicy`
- `androidx.compose.ui.layout.Measurable` / `Placeable`
- `androidx.compose.ui.layout.layout`（修饰符版，见第 64 章）

> 官方参考：[Custom layouts 教程（Layout 与 MeasurePolicy）](https://developer.android.com/develop/ui/compose/layouts/custom)、[MeasurePolicy](https://developer.android.com/reference/kotlin/androidx/compose/ui/layout/MeasurePolicy)

## 3. 项目源码片段

场景：搜索页的关键词标签云，子项横向排列、放不下就换行——这就是一个简易 FlowLayout。

<!-- compile -->
```kotlin
@Composable
fun FlowLayout(
    modifier: Modifier = Modifier,
    horizontalGap: Dp = 8.dp,
    verticalGap: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val hGap = with(LocalDensity.current) { horizontalGap.toPx() }
    val vGap = with(LocalDensity.current) { verticalGap.toPx() }

    Layout(content = content, modifier = modifier) { measurables, constraints ->
        // constraints：父级给本布局划定的宽高范围（第 63 章详解）
        // 1. 逐个测量所有子项
        val placeables = measurables.map { it.measure(constraints) }
        val maxWidth = constraints.maxWidth.takeIf { it != Constraints.Infinity }
            ?: Int.MAX_VALUE

        // 2. 先算出每个子项的坐标，以及自身需要的宽高
        var x = 0f
        var y = 0f
        var rowHeight = 0f
        var totalWidth = 0f
        val positions = placeables.map { p ->
            if (x > 0 && x + p.width > maxWidth) {
                x = 0f
                y += rowHeight + vGap
                rowHeight = 0f
            }
            val position = x to y
            x += p.width + hGap
            rowHeight = maxOf(rowHeight, p.height.toFloat())
            totalWidth = maxOf(totalWidth, x - hGap)
            position
        }

        // 3. 声明自身尺寸，并在 PlacementScope 里真正摆放子项
        // 汇总出的尺寸必须裁剪回父级给的约束内：父级可能要求了非零的最小宽高，
        // 直接报 totalWidth 会违反约束（子项宽高为 0 时也不能报 0）
        layout(
            constraints.constrainWidth(totalWidth.roundToInt()),
            constraints.constrainHeight((y + rowHeight).roundToInt()),
        ) {
            placeables.forEachIndexed { index, p ->
                val (px, py) = positions[index]
                p.place(px.roundToInt(), py.roundToInt()) // place 只能在 layout {} 的 lambda 内调用
            }
        }
    }
}

// 使用
FlowLayout {
    listOf("Compose", "Kotlin", "Material3", "动画", "手势").forEach { tag ->
        Surface(shape = RoundedCornerShape(50), color = Color(0xFFE3F2FD)) {
            Text(tag, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}
```

## 4. 逐步解释

`Layout(content, modifier, measurePolicy)` 的核心是第三个参数 MeasurePolicy，以 lambda 形式给出：`{ measurables, constraints -> ... }`。`measurables` 是 content 里每个子项的测量入口（Measurable）；`constraints`（约束）是**父级发给当前这个 Layout 自身**的尺寸范围——宽高各自的最小值与最大值（第 63 章详解）。它约束的是你，不是直接约束子项：子项收到什么约束，由你在下面调用 `measurable.measure(...)` 时自己决定。整个 lambda 就是你的布局算法。

测量：对每个子项调用 `measurable.measure(constraints)`，返回 `Placeable`——它带 `width`/`height`，代表「子项在给定约束下最终要多大」。这里的实参就是**你为这个子项挑的约束**：示例把父级给自己的约束原样传下去，所以子项能在整块可用区域里挑尺寸——但注意原样传递会把父级 `minWidth > 0` 的最小约束也一起压给子项（子项不能比它更小）；想让子项自由收缩，就传 `constraints.copy(minWidth = 0, minHeight = 0)` 的宽松版。另一个硬性规则是**同一个 Measurable 在一次测量里只能 measure 一次**：普通 `Layout` 不支持多趟测量，测第二次会直接抛运行时异常（错误信息会提示改用 `minIntrinsicWidth()` 这类固有尺寸查询，见第 63 章；确实需要「先看内容再决定布局」时用 `SubcomposeLayout`）。注意约束从父传到子，**子项必须在收到的那份约束范围内选尺寸**，它无权反过来要求父级。

放置：place 不是独立于 layout 的一步——它只能在 `layout(width, height) { ... }` 的摆放 lambda（`Placeable.PlacementScope`）里调用，编译期就由作用域类型挡在外面乱用。在 lambda 里对每个 Placeable 调 `place(x, y)`，把它放到**当前布局自身坐标系**里的具体位置；place 是坐标偏移，不改变尺寸。RTL 语言环境下用 `placeRelative`，它会按布局方向自动镜像水平坐标。

声明自身尺寸：所有 Placeable 到手、自身宽高算完之后，调用 `layout(width, height) { ... }` 告诉父级「我占多大」，并在它的摆放 lambda 里完成上面说的 place。只有调用了 layout，放置才会执行，父级才能继续向上汇报。所以三步的真实调用顺序是：**先 measure 全部子项，再调用 `layout(width, height)` 声明自身尺寸，place 在 layout 的摆放 lambda 内执行**——不是「先 place 再 layout」，顺序反了连编译都过不了。

测量顺序的意义：父级先量所有子项、再根据汇总结果决定自己的尺寸；因此「子项多大」影响「父级多大」，而「父级给什么约束」影响「子项能多大」。这个单向的信息流（约束下行、尺寸上行）是整个布局系统正确性的根基。

布局阶段与绘制、命中测试分离：布局只算一次尺寸和位置；绘制每帧执行；命中测试（点击落在哪个组件上）基于放置结果。自定义布局只需关心布局阶段，绘制和命中自动基于 place 结果工作。

记住：**约束向下传，尺寸向上报；measure 算尺寸、layout 报自身、place 在 layout 的摆放 lambda 里定位置。**

## 5. 实际执行过程

1. 组合阶段执行 FlowLayout，content 中的子项被注册为 measurables
2. 布局阶段调用 MeasurePolicy，逐个 measure 子项拿到 Placeable
3. 循环计算每个子项的行/列坐标，放不下时换行
4. 调用 `layout(...)` 声明自身尺寸，并在它的摆放 lambda 里依次 `place` 所有 Placeable
5. 父级拿到 `layout` 报告的尺寸后继续向上布局
6. 绘制阶段按 place 结果绘制，点击按 place 结果命中

## 6. Flutter / Dart 3 对照

先讲相似点：两边都遵循「父给约束、子报尺寸、父放位置」的布局模型，只是 API 形态不同。

```dart
class FlowDelegate extends MultiChildLayoutDelegate {
  @override
  void performLayout(Size size) {
    double x = 0, y = 0;
    for (final id in ['a', 'b', 'c']) {
      if (!hasChild(id)) continue;
      // 真实 API：按 id 测量与放置，不能直接遍历 children
      final childSize = layoutChild(id, BoxConstraints.loose(size));
      positionChild(id, Offset(x, y));
      x += childSize.width + 8;
    }
  }

  @override
  bool shouldRelayout(covariant FlowDelegate oldDelegate) => true;
}

// 子项必须用 LayoutId 标记，delegate 才能按 id 找到它们
CustomMultiChildLayout(
  delegate: FlowDelegate(),
  children: [
    LayoutId(id: 'a', child: const Text('Compose')),
    LayoutId(id: 'b', child: const Text('Kotlin')),
  ],
)
```

本质区别：Flutter 的自定义布局要么写完整的 `RenderObject`（performLayout），要么写 `MultiChildLayoutDelegate` 类——要处理 shouldRelayout、`LayoutId` id 寻址这类细节，较繁琐。Compose 把布局算法收进一个 lambda，即插即用，可以理解为「轻量版 RenderObject」：同样能控制测量与放置，但不需要维护独立渲染类，也不用给子项编号寻址。

记住：**Compose 的 Layout 组合是函数式的 RenderObject。**

## 7. 常见错误

1. 忘记处理无限约束：父级给的是无限约束时 `constraints.maxWidth == Constraints.Infinity`，直接拿它参与计算会溢出或死循环。应像示例里那样 `takeIf` 或 `minOf` 兜底。
2. `place` 超出自身声明的 size：不会自动报错，默认也没有裁剪（没有 clip 时常能画出去），但超出部分不保证被绘制与命中测试覆盖，还打破了父级对「声明尺寸 = 内容范围」的假设。放置应尽量落在声明尺寸内。
3. 循环依赖：父按子尺寸决定尺寸，子又要求父约束完全固定，两者互相等对方——约束无法收敛。打破方法是子项测量必须接受父给的约束，不再向上要信息。
4. 忘记调用 `layout()`：不声明尺寸，整个测量结果无效，子项不会显示。
5. 不设置最小尺寸导致塌陷：子项宽高都是 0 时，自身 `layout` 的宽高也是 0，视觉上完全消失。用 `constraints.constrainWidth(...)` / `constrainHeight(...)` 把汇总尺寸夹回父级约束，既不会突破父级上限，也能拿到父级要求的最小尺寸。

## 8. 本章检查题

1. `measurable.measure(constraints)` 返回什么？它携带哪些信息？
2. `place(x, y)` 的作用是什么？
3. 为什么说「测量顺序」重要？
4. 自身尺寸在哪里声明？不声明会怎样？

答案：

1. 返回 `Placeable`，携带子项在给定约束下确定的 `width` 与 `height`。
2. 把子项放到当前布局自身坐标系中的指定位置，决定子项最终落在哪；它必须在 `layout` 的摆放 lambda 内调用。
3. 因为约束下行、尺寸上行是单向的：父必须先量完所有子项才能汇总出自己的尺寸，子项则在父给的约束内选尺寸——顺序颠倒会导致布局无法收敛。
4. 在最后调用 `layout(width, height) {}` 声明；不声明测量结果无效，子项无法显示。

## 9. 本章总结

自定义布局的完整套路只有三步：measure 所有子项拿到尺寸、计算自身尺寸并调 `layout(width, height)` 声明、在 layout 的摆放 lambda 里把子项 place 到坐标。理解「约束下行、尺寸上行」的单向信息流，任何复杂布局都能用 Layout 组合拼出来。
