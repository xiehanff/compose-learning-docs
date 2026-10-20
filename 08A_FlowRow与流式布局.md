# 第 8A 章：FlowRow 与流式布局

## 1. 本章目标

掌握 Compose 的流式（换行）布局，理解它与 `Row` 的本质区别，并按本仓库基线使用稳定的基础 API。

学完后，你应当能够：

- 用 `FlowRow` 让子项一行放不下时自动折行
- 区分 `horizontalArrangement` 与 `verticalArrangement` 各自控制的间距
- 用 `maxItemsInEachRow` 强制每行最多放几个子项
- 知道 `maxLines` / `overflow` 用于限制行数并处理超出的内容（裁剪或显示展开指示器）。注意这是 `FlowRow` 自己的参数，管的是「折行之后的行数」；第 11 章 `Text` 也有同名的 `maxLines` / `overflow`，那两个管的是「一段文字内部的行数与省略方式」，两者不是一回事
- 区分已稳定的基础重载与已弃用的 `overflow` 旧 API

## 2. 项目中的位置

- `components/FlowRowDemo.kt`（组件 id 为 `flow_row`，标题「FlowRow 流式布局」）

原 Demo 还演示动态增删标签；本章先用固定标签列表讲清楚换行规则。动态增删依赖第 21—22 章的状态知识，读完后再回到原 Demo 看第三块。

## 3. 项目源码片段

下面的代码是 Composable 函数体中的内容。标签直接用 `Box` 和 `Text` 表示，不需要先创建自定义组件。

<!-- compile -->
```kotlin
// 先用固定列表观察布局；动态增删会在第 21—22 章讲状态后再练
val tags = listOf("Compose", "Flutter", "Kotlin", "Dart", "Android", "iOS", "声明式 UI")

FlowRow(
    modifier = Modifier
        .fillMaxWidth()
        .background(Color(0xFFF2F2F2), RoundedCornerShape(8.dp))
        .padding(8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp), // 同一行内子项的水平间距
    verticalArrangement = Arrangement.spacedBy(8.dp),   // 换行后各行的垂直间距
) {
    tags.forEach { tag ->
        Box(
            modifier = Modifier
                .background(Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(text = tag, color = Color(0xFF424242), fontSize = 14.sp)
        }
    }
}

// 对照：即使宽度还够，第 4 个子项也会被强制换行
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    maxItemsInEachRow = 3,
) {
    tags.forEach { tag ->
        Box(
            modifier = Modifier
                .background(Color(0xFFE0E0E0), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(text = tag, color = Color(0xFF424242), fontSize = 14.sp)
        }
    }
}
```

原 Demo 的 `.px` / `.pxSp()` 是项目自定义单位；为方便空工程读者复制，本章用标准 `.dp` / `.sp` 表达相同意图。单位适配在第 41—44 章介绍。

## 4. 逐步解释

`FlowRow` 是「流式布局」：子项沿水平方向依次排列，一行放不下就自动折到下一行。适合子项数量不定、宽度不一的场景（标签流、筛选按钮组、搜索历史），这正是 `Row` 做不到的——`Row` 永远只有一行、不会自动换行；子项宽度超出时默认表现为溢出（被裁掉或在边界外绘制），想压缩必须自己给子项加 `Modifier.weight`（让同一行或同一列的子项按比例分掉剩余空间，第 14 章详解），想滚动则要套一层 `horizontalScroll`（横向滚动，第 19 章详解）。

它的两个 Arrangement 参数方向要分清楚：`horizontalArrangement = Arrangement.spacedBy(8.dp)` 控制同一行内相邻子项的间距；`verticalArrangement = Arrangement.spacedBy(8.dp)` 控制换行之后每一行之间的间距。这和 `Row`（只有 `horizontalArrangement`）不同，务必别混。两个参数也都可以换成 `Alignment` 类的对齐值：例如 `horizontalArrangement = Arrangement.End` 会让每一行内的子项整体靠右排；此外还有 `itemVerticalAlignment`（默认顶部对齐），控制同一行内高矮不一的子项怎么沿垂直方向对齐——相当于给「每一行」提供一个行内交叉轴对齐，对应关系与第 8 章 Row 的 `verticalAlignment` 类似。

`maxItemsInEachRow = 3` 是 FlowRow 独有的约束：每行最多放 3 个子项，第 4 个即使宽度还有富余也会被挤到下一行。此外还有 `maxLines`（最多渲染几行）与 `overflow`（超出部分怎么处理），两者作用不同。先注意：这两个是 `FlowRow` 自己的参数，限制的是「折行之后的行数」；第 11 章 `Text` 也有同名的 `maxLines` / `overflow`，那两个限制的是「一段文字内部的行数与省略方式」，名字相同但作用对象完全不同，不要混在一起记。`maxLines` 只限制行数，本身不会产生折叠按钮或动画；`FlowRowOverflow.Clip` 会把超出的子项裁掉，`FlowRowOverflow.expandIndicator { ... }` / `expandOrCollapseIndicator(...)` 才会额外渲染「展开 / 收起」指示器，指示器里可以通过 `FlowRowOverflowScope` 的 `totalItemCount` / `shownItemCount` 得知一共多少项、当前显示了多少项，再配合自己的状态决定展开与否。也就是说，展开 / 折叠要靠显式使用这些 indicator 或自己用状态控制渲染哪些子项，单写 `maxLines` 做不到。另外要注意 API 现状：项目所用的 Compose BOM 2026.06.00（foundation-layout 1.11.3）里，`FlowRow` 的 `overflow` 参数以及 `FlowRowOverflow.Clip` / `Visible` 已被标为 deprecated（基线构件中的弃用信息为 "The overflow parameter has been deprecated"），所以更稳妥的做法是只用 `maxLines` 限制行数，再用自己的状态决定渲染哪些子项。

在本仓库基线（Compose BOM 2026.06.00）中，基础 `FlowRow` 重载已经稳定，无需 `@OptIn`。需要注意的是，带 `overflow` 的旧重载以及 `FlowRowOverflow.Clip` / `Visible` 已标记 deprecated（基线构件中的弃用信息为 "The overflow parameter has been deprecated"）；`expandIndicator` / `expandOrCollapseIndicator` 本身没有单独标弃用，但它们只服务于 `overflow` 参数，随参数一起不再推荐。新代码应优先使用基础重载、`maxLines` 和自己的状态控制可见项。

### 4.1 观察换行：容器宽度变了，换行位置跟着变

用四个**固定 80dp 宽**的色块做实验（固定尺寸是为了让换行位置可以精确预测），间距 8dp，放进两个宽度不同的容器：

<!-- compile -->
```kotlin
val colors = listOf(
    Color(0xFFE57373),
    Color(0xFF64B5F6),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
)

Column(
    modifier = Modifier.fillMaxWidth().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    Text("窄容器 176dp = 80 + 8 + 80：每行 2 个，共 2 行")
    FlowRow(
        modifier = Modifier.width(176.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), // 行内水平间距
        verticalArrangement = Arrangement.spacedBy(8.dp),   // 行与行之间
    ) {
        colors.forEach { c ->
            Box(Modifier.size(width = 80.dp, height = 36.dp).background(c))
        }
    }

    Text("宽容器 264dp = 80 × 3 + 8 × 2：每行 3 个，剩 1 个折到第二行")
    FlowRow(
        modifier = Modifier.width(264.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { c ->
            Box(Modifier.size(width = 80.dp, height = 36.dp).background(c))
        }
    }
}
```

初始界面：两组色块，各带一行说明文字。预期观察：

| 容器 | 换行计算 | 结果 |
| --- | --- | --- |
| 176dp | 80；80 + 8 + 80 = 168 ≤ 176 放得下第 2 个；168 + 8 + 80 超出 | 第一行红、蓝；第二行绿、橙 |
| 264dp | 前三个共占 80×3 + 8×2 = 256 ≤ 264；再加第 4 个超出 | 第一行红、蓝、绿；第二行橙 |

同一组子项，什么都没改，只改容器宽度，换行位置就变了。这正是流式布局的核心行为：**换行点由「已用宽度 + 间距 + 下一个子项宽度」与容器宽度的比较决定**，而不是由固定的列数决定。真实设备上屏幕宽度各不相同，标签流的换行结果因此跟着屏幕变化；想验证可以直接旋转屏幕或调整窗口大小，预期观察与上表同一套算法。上节文字标签的宽度取决于字体渲染，换行点不如固定 dp 色块直观，两种例子对照看更清楚。

### 4.2 FlowRow 与固定列 Grid 的区别

FlowRow 是「按内容测量、自然换行」；第 18 章的 `LazyVerticalGrid` + `GridCells.Fixed(3)` 是「先固定列数，再把子项逐个填进格子」：

| 维度 | FlowRow | LazyVerticalGrid（固定列） |
| --- | --- | --- |
| 换行依据 | 每个子项实测宽度放不下才换 | 固定列数，到数换行 |
| 子项宽度 | 各自不同，可以参差 | 通常取列宽，整列一致 |
| 滚动方向 | 不自带滚动（本身横向排布，整体随外层容器滚动） | 自带纵向滚动 |
| 懒加载 | 不是懒容器，所有子项立即组合与测量 | 是懒容器，屏幕外子项按需组合 |

选择依据：标签、筛选词这类**宽度不一、数量不定**的内容用 FlowRow；**规格统一的网格**（图片墙、设置项）用 Grid。两者不要互相替代——用 FlowRow 硬凑整齐网格会失去列对齐，用 Grid 装参差标签会浪费列宽。

动态标签是这套布局的常见应用，但需要先懂 `remember` 与可观察状态。首次阅读先观察固定 `tags` 如何按宽度换行；完成第 21—22 章后，再回到原 Demo 看列表增删如何触发界面更新。这样可以把「如何排布」与「数据如何变化」分开学。

## 5. 实际执行过程

1. 组合阶段读取 `tags` 列表，`FlowRow` 的 lambda 为每个标签创建一个 `Box` 和 `Text`。
2. 测量阶段 `FlowRow` 依次测量子项宽度，累加已用宽度。
3. 当「已用宽度 + 行内间距 + 下一个子项宽度」超过容器可用宽度（或该行已达 `maxItemsInEachRow`）时，换到下一行。
4. 行与行之间按其 `verticalArrangement` 留出间距，行内子项之间按 `horizontalArrangement` 留出间距。

## 6. Flutter / Dart 3 对照

Flutter 的 `Wrap` 就是流式布局，`FlowRow` 与它一一对应；`FlowColumn` 方向相反（纵向依次排列、放不下时折到下一列），相当于 `Wrap(axis: Axis.vertical)`。

```dart
Wrap(
  spacing: 8,      // 主轴（水平）方向子项间距   ≈ horizontalArrangement.spacedBy(8.dp)
  runSpacing: 8,   // 换行后各行之间的间距       ≈ verticalArrangement.spacedBy(8.dp)
  children: tags.map((tag) => TagChip(tag)).toList(),
);
```

参数对照：`spacing` 对应 `horizontalArrangement`，`runSpacing` 对应 `verticalArrangement`；`axis` 切换方向，对应 `FlowRow` / `FlowColumn` 两个类。

最大的差异是 `Wrap` 没有 `maxItemsInEachRow` 这样的参数——想实现「每行固定 N 个」必须在 Dart 侧先把 children 手动分组（例如每 3 个塞进一个 `Row`），而 `FlowRow` 传一个参数即可。另外 `Wrap` 是稳定组件；本仓库基线中的基础 `FlowRow` 也已稳定，不需要 `@OptIn`。如果使用仍属实验性的其他布局 API，才需要按 API 要求 opt-in。

## 7. 常见错误

- 继续使用已经 deprecated 的 `overflow` / `FlowRowOverflow` 旧 API；应优先使用基础重载、`maxLines` 和自己的状态控制
- 只写 `horizontalArrangement` 而漏了 `verticalArrangement`，换行后两行贴在一起
- 把 `maxItemsInEachRow` 当成「最多显示几个、其余隐藏」，实际它是强制换行，子项一个都不会少
- 用 `Row` 硬凑标签流，窄屏下标签直接溢出屏幕（未使用 `weight` 时 `Row` 不会自动压缩子项）
- 把 `FlowRow` 的 `Modifier.weight` 当成对全部子项统一分配空间：它按子项所在行分配剩余宽度，每一行独立计算
- 把 `FlowRow` 当懒容器用：它不是 Lazy 组件，所有子项会立即组合与测量；长列表场景应使用第 16、18 章的 `LazyColumn` / `LazyVerticalGrid`

## 8. 本章检查题

1. `FlowRow` 与 `Row` 最本质的区别是什么？什么场景必须用 `FlowRow`？
2. `horizontalArrangement` 和 `verticalArrangement` 在 `FlowRow` 里分别控制什么？
3. 为什么不应继续使用 `FlowRow` 的 `overflow` / `FlowRowOverflow` 旧 API？对应的 Flutter 组件需要实验性声明吗？
4. `maxItemsInEachRow = 3` 和容器宽度各自会在什么情况下触发换行？
5. 预测：4.1 节演示中把窄容器从 176dp 改成 264dp、宽容器改成 352dp（间距仍为 8dp，色块仍 80dp 宽），两组各怎么换行？再问：色块宽度不变、把 `horizontalArrangement` 的间距改成 16dp，264dp 容器一行还能放下 3 个吗？

答案：

1. `Row` 永远只有一行、不会自动换行；子项过多时默认溢出（除非自己用 `weight` 压缩或套一个横向滚动容器）；`FlowRow` 一行放不下会自动折行。子项数量不定、宽度不一（如标签流）时必须用 `FlowRow`。
2. `horizontalArrangement` 控制同一行内相邻子项的水平间距，`verticalArrangement` 控制换行后各行之间的垂直间距。
3. 在本仓库基线中，基础 `FlowRow` 已稳定；`overflow` 参数旧重载与 `FlowRowOverflow.Clip` / `Visible` 已 deprecated，应改用基础重载、`maxLines` 和自己的状态控制。对应的 Flutter `Wrap` 是稳定组件，不需要任何额外声明。
4. 当当前行已放满 3 项时，下一项无论剩余宽度多少都会换到下一行；未达到上限时，则在当前行放不下时换行。
5. 窄容器改 264dp 后每行 3 个（红蓝绿一行、橙一行），宽容器 352dp 时 80×4 + 8×3 = 344 ≤ 352，四个一行、不再换行。间距改 16dp 后，一行 3 个共需 80×3 + 16×2 = 272 > 264，放不下第 3 个，变成每行 2 个。

## 9. 本章总结

`FlowRow` / `FlowColumn` 负责「放不下就折行」的流式布局，`horizontalArrangement` 管行内间距、`verticalArrangement` 管行间距，`maxItemsInEachRow` 可强制每行数量上限，`maxLines` 用于限制行数，展开/收起可通过自己的状态控制渲染项；已弃用的 `overflow` / `FlowRowOverflow` 不应成为新代码的实现方向。它对应 Flutter 的 `Wrap`，但多了一个 `Wrap` 没有的 `maxItemsInEachRow`。在本仓库基线中基础 `FlowRow` 不需要 `@OptIn`。动态增删数据依赖状态知识，见第 21—22 章及其后的原 Demo 回访。

官方参考：[Flow layouts in Compose](https://developer.android.com/develop/ui/compose/layouts/flow)、[Compose Foundation 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-foundation)。
