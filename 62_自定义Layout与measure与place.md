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

## 3. 项目源码片段

场景：搜索页的关键词标签云，子项横向排列、放不下就换行——这就是一个简易 FlowLayout。

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
        // 1. 逐个测量所有子项
        val placeables = measurables.map { it.measure(constraints) }

        // 2. 放入行，放不下则换行
        var x = 0f
        var y = 0f
        var rowHeight = 0f
        var totalWidth = 0f
        val maxWidth = constraints.maxWidth.takeIf { it != Constraints.Infinity }
            ?: Int.MAX_VALUE

        placeables.forEach { p ->
            if (x > 0 && x + p.width > maxWidth) {
                x = 0f
                y += rowHeight + vGap
                rowHeight = 0f
            }
            p.place(x.roundToInt(), y.roundToInt())
            x += p.width + hGap
            rowHeight = maxOf(rowHeight, p.height.toFloat())
            totalWidth = maxOf(totalWidth, x - hGap)
        }

        // 3. 声明自身尺寸
        layout(totalWidth.roundToInt(), (y + rowHeight).roundToInt()) {}
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

`Layout(content, modifier, measurePolicy)` 的核心是第三个参数 MeasurePolicy，以 lambda 形式给出：`{ measurables, constraints -> ... }`。`measurables` 是 content 里每个子项的测量入口（Measurable），`constraints` 是父级传下来的约束。整个 lambda 就是你的布局算法。

测量：对每个子项调用 `measurable.measure(constraints)`，返回 `Placeable`——它带 `width`/`height`，代表「子项在给定约束下最终要多大」。注意约束从父传到子，**子项必须在约束范围内选尺寸**，它无权反过来要求父级。

放置：得到所有 Placeable 后，调用 `place(x, y)` 把每个子项放到自己坐标系里的具体位置。place 是坐标偏移，不改变尺寸。RTL 语言环境下可以用 `placeRelative`，它会按布局方向自动镜像水平坐标。

声明自身尺寸：最后必须调用 `layout(width, height) {}` 告诉父级「我占多大」。只有调用了 layout，放置才生效，父级才能继续向上汇报。**measure → place → layout 三步缺一不可。**

测量顺序的意义：父级先量所有子项、再根据汇总结果决定自己的尺寸；因此「子项多大」影响「父级多大」，而「父级给什么约束」影响「子项能多大」。这个单向的信息流（约束下行、尺寸上行）是整个布局系统正确性的根基。

布局阶段与绘制、命中测试分离：布局只算一次尺寸和位置；绘制每帧执行；命中测试（点击落在哪个组件上）基于放置结果。自定义布局只需关心布局阶段，绘制和命中自动基于 place 结果工作。

记住：**约束向下传，尺寸向上报；measure 算尺寸、place 定位置、layout 报自身。**

## 5. 实际执行过程

1. 组合阶段执行 FlowLayout，content 中的子项被注册为 measurables
2. 布局阶段调用 MeasurePolicy，逐个 measure 子项拿到 Placeable
3. 循环计算每个子项的行/列坐标，放不下时换行
4. 依次 `place` 所有 Placeable，确定子项位置
5. `layout` 声明自身尺寸，父级拿到结果后继续向上布局
6. 绘制阶段按 place 结果绘制，点击按 place 结果命中

## 6. Flutter / Dart 3 对照

先讲相似点：两边都遵循「父给约束、子报尺寸、父放位置」的布局模型，只是 API 形态不同。

```dart
class FlowDelegate extends MultiChildLayoutDelegate {
  @override
  void performLayout(Size size) {
    double x = 0, y = 0, rowHeight = 0;
    for (final child in children) {
      child.layout(constraints, parentUsesSize: true);
      child.position(Offset(x, y));
      x += child.size.width + 8;
      rowHeight = math.max(rowHeight, child.size.height);
    }
  }

  @override
  bool shouldRelayout(covariant FlowDelegate oldDelegate) => true;
}

CustomMultiChildLayout(children: [...], delegate: FlowDelegate());
```

本质区别：Flutter 的自定义布局要么写完整的 `RenderObject`（performLayout），要么写 `MultiChildLayoutDelegate` 类——要处理 shouldRelayout、parentUsesSize 等细节，较繁琐。Compose 把布局算法收进一个 lambda，即插即用，可以理解为「轻量版 RenderObject」：同样能控制测量与放置，但不需要维护独立渲染类。

记住：**Compose 的 Layout 组合是函数式的 RenderObject。**

## 7. 常见错误

1. 忘记处理无限约束：父级给的是无限约束时 `constraints.maxWidth == Constraints.Infinity`，直接拿它参与计算会溢出或死循环。应像示例里那样 `takeIf` 或 `minOf` 兜底。
2. `place` 超出自身声明的 size：子项会被裁剪。父级只保证你声明的 `layout(width, height)` 区域可见，放出去的部分不保证绘制。
3. 循环依赖：父按子尺寸决定尺寸，子又要求父约束完全固定，两者互相等对方——约束无法收敛。打破方法是子项测量必须接受父给的约束，不再向上要信息。
4. 忘记调用 `layout()`：不声明尺寸，整个测量结果无效，子项不会显示。
5. 不设置最小尺寸导致塌陷：子项宽高都是 0 时，自身 `layout` 的宽高也是 0，视觉上完全消失。必要时用 `constraints.constrainWidth(...)` 保证最小值。

## 8. 本章检查题

1. `measurable.measure(constraints)` 返回什么？它携带哪些信息？
2. `place(x, y)` 的作用是什么？
3. 为什么说「测量顺序」重要？
4. 自身尺寸在哪里声明？不声明会怎样？

答案：

1. 返回 `Placeable`，携带子项在给定约束下确定的 `width` 与 `height`。
2. 把子项放到父坐标系中的指定位置，决定子项最终落在哪。
3. 因为约束下行、尺寸上行是单向的：父必须先量完所有子项才能汇总出自己的尺寸，子项则在父给的约束内选尺寸——顺序颠倒会导致布局无法收敛。
4. 在最后调用 `layout(width, height) {}` 声明；不声明测量结果无效，子项无法显示。

## 9. 本章总结

自定义布局的完整套路只有三步：measure 所有子项拿到尺寸、按算法 place 到坐标、layout 声明自身尺寸。理解「约束下行、尺寸上行」的单向信息流，任何复杂布局都能用 Layout 组合拼出来。
