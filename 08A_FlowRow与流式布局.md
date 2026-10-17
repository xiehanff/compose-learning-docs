# 第 8A 章：FlowRow 与流式布局

## 1. 本章目标

掌握 Compose 的流式（换行）布局，理解它与 `Row` 的本质区别，并学会用实验性 API 的 opt-in 写法。

学完后，你应当能够：

- 用 `FlowRow` 让子项一行放不下时自动折行
- 区分 `horizontalArrangement` 与 `verticalArrangement` 各自控制的间距
- 用 `maxItemsInEachRow` 强制每行最多放几个子项
- 知道 `maxLines` / `overflow` 用于限制行数并处理超出的内容（裁剪或显示展开指示器）。注意这是 `FlowRow` 自己的参数，管的是「折行之后的行数」；第 11 章 `Text` 也有同名的 `maxLines` / `overflow`，那两个管的是「一段文字内部的行数与省略方式」，两者不是一回事
- 区分已稳定的基础重载与已弃用的 `overflow` 旧 API
- 用 `mutableStateListOf`（可观察列表，第 21 章详解）动态增删子项并触发重新换行

## 2. 项目中的位置

- `components/FlowRowDemo.kt`（组件 id 为 `flow_row`，标题「FlowRow 流式布局」）

该 Demo 一共演示三块：自然换行、`maxItemsInEachRow = 3` 对照、动态增删标签。

## 3. 项目源码片段

<!-- compile decls
prelude: val selectedTag = "Compose"
prelude: @Composable
prelude: fun FlowTag(text: String, selected: Boolean, onClick: () -> Unit) = Unit
-->
```kotlin
@Composable
private fun FlowRowExample() {
    // 可观察列表：增删元素会自动触发重组（重组见第 22 章）
    val tags = remember {
        mutableStateListOf("Compose", "Flutter", "Kotlin", "Dart", "Android", "iOS", "声明式 UI")
    }

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF2F2F2), RoundedCornerShape(8.px))
            .padding(8.px),
        horizontalArrangement = Arrangement.spacedBy(8.px), // 同一行内子项的水平间距
        verticalArrangement = Arrangement.spacedBy(8.px),   // 换行后各行的垂直间距
    ) {
        tags.forEach { tag ->
            FlowTag(text = tag, selected = tag == selectedTag, onClick = { /* 切换选中 */ })
        }
    }

    // 对照：即使宽度还够，第 4 个子项也会被强制换行
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.px),
        verticalArrangement = Arrangement.spacedBy(8.px),
        maxItemsInEachRow = 3,
    ) {
        tags.forEach { tag -> FlowTag(text = tag, selected = false, onClick = {}) }
    }
}
```

单个标签本质是一个「文字 + 胶囊圆角底色」的 Box：

```kotlin
Box(
    modifier = Modifier
        .background(if (selected) Color(0xFF64B5F6) else Color(0xFFE0E0E0), RoundedCornerShape(16.px))
        .clickable(onClick = onClick)              // 点击事件挂在 padding 之前，整块胶囊都可点
        .padding(horizontal = 12.px, vertical = 6.px),
) {
    Text(text = text, color = if (selected) Color.White else Color(0xFF424242), fontSize = 20.pxSp())
}
```

## 4. 逐步解释

`FlowRow` 是「流式布局」：子项沿水平方向依次排列，一行放不下就自动折到下一行。适合子项数量不定、宽度不一的场景（标签流、筛选按钮组、搜索历史），这正是 `Row` 做不到的——`Row` 永远只有一行、不会自动换行；子项宽度超出时默认表现为溢出（被裁掉或在边界外绘制），想压缩必须自己给子项加 `Modifier.weight`（让同一行或同一列的子项按比例分掉剩余空间，第 14 章详解），想滚动则要套一层 `horizontalScroll`（横向滚动，第 19 章详解）。

它的两个 Arrangement 参数方向要分清楚：`horizontalArrangement = Arrangement.spacedBy(8.px)` 控制同一行内相邻子项的间距；`verticalArrangement = Arrangement.spacedBy(8.px)` 控制换行之后每一行之间的间距。这和 `Row`（只有 `horizontalArrangement`）不同，务必别混。

`maxItemsInEachRow = 3` 是 FlowRow 独有的约束：每行最多放 3 个子项，第 4 个即使宽度还有富余也会被挤到下一行。此外还有 `maxLines`（最多渲染几行）与 `overflow`（超出部分怎么处理），两者作用不同。先注意：这两个是 `FlowRow` 自己的参数，限制的是「折行之后的行数」；第 11 章 `Text` 也有同名的 `maxLines` / `overflow`，那两个限制的是「一段文字内部的行数与省略方式」，名字相同但作用对象完全不同，不要混在一起记。`maxLines` 只限制行数，本身不会产生折叠按钮或动画；`FlowRowOverflow.Clip` 会把超出的子项裁掉，`FlowRowOverflow.expandIndicator { ... }` / `expandOrCollapseIndicator(...)` 才会额外渲染「展开 / 收起」指示器，指示器里可以通过 `FlowRowOverflowScope` 的 `totalItemCount` / `shownItemCount` 得知一共多少项、当前显示了多少项，再配合自己的状态决定展开与否。也就是说，展开 / 折叠要靠显式使用这些 indicator 或自己用状态控制渲染哪些子项，单写 `maxLines` 做不到。另外要注意 API 现状：项目所用的 Compose BOM 2026.06.00（foundation-layout 1.11.3）里，`FlowRow` 的 `overflow` 参数以及 `FlowRowOverflow.Clip` / `Visible` 已被标为 deprecated（官方说明是 overflow 不再维护），所以更稳妥的做法是只用 `maxLines` 限制行数，再用自己的状态决定渲染哪些子项。

在本仓库基线（Compose BOM 2026.06.00）中，基础 `FlowRow` 重载已经稳定，无需 `@OptIn`。需要注意的是，带 `overflow` 的旧重载以及 `FlowRowOverflow` 相关 API 已标记 deprecated；新代码应优先使用基础重载、`maxLines` 和自己的状态控制可见项。

最后是动态性：示例用 `remember { mutableStateListOf(...) }` 保存标签。`mutableStateListOf` 返回一个「可观察列表」——对它做增删时 Compose 能感知到（它是 Compose 的状态容器；`remember` 则负责让这份数据在下次重组时仍然保留，两者都在第 21 章详解）。`add`/`removeAt` 之后会触发重组，也就是「状态变化后，Compose 重新执行读取过该状态的 Composable 函数，用新值重新生成这部分界面」（详见第 22 章），`FlowRow` 就会按最新的子项集合重新计算换行位置——不需要任何手动布局或刷新代码。选中态用 `selectedTag: String?` 记录，点击同一个标签时置回 `null` 表示取消选中；删除标签时若删掉的正是选中项，也同步把选中态清空，避免状态指向一个已不存在的标签。

## 5. 实际执行过程

1. 组合阶段读取 `tags` 列表，`FlowRow` 的 lambda 为每个标签创建一个 `FlowTag`。
2. 测量阶段 `FlowRow` 依次测量子项宽度，累加已用宽度。
3. 当「已用宽度 + 下一个子项宽度」超过容器可用宽度（或该行已达 `maxItemsInEachRow`）时，换到下一行。
4. 行与行之间按其 `verticalArrangement` 留出间距，行内子项之间按 `horizontalArrangement` 留出间距。
5. 点击「添加标签」调用 `tags.add(...)`；点击「删除最后一个」调用 `tags.removeAt(tags.lastIndex)`（列表为空时按钮 `enabled = false` 已禁用）。
6. 列表变化触发重组，`FlowRow` 用新的子项集合重新测量、重新换行。

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
- 子项数量会变化的场景仍用普通 `List` 存放数据，增删后界面不刷新，误以为 FlowRow 没生效
- 用 `Row` 硬凑标签流，窄屏下标签直接溢出屏幕（未使用 `weight` 时 `Row` 不会自动压缩子项）
- 删除子项后不同步清理「选中项」状态，出现高亮指向已删除标签的幽灵状态
- 把 `FlowRow` 的 `Modifier.weight` 当成对全部子项统一分配空间：它按子项所在行分配剩余宽度，每一行独立计算

## 8. 本章检查题

1. `FlowRow` 与 `Row` 最本质的区别是什么？什么场景必须用 `FlowRow`？
2. `horizontalArrangement` 和 `verticalArrangement` 在 `FlowRow` 里分别控制什么？
3. 为什么不应继续使用 `FlowRow` 的 `overflow` / `FlowRowOverflow` 旧 API？对应的 Flutter 组件需要实验性声明吗？
4. 列表增删后 `FlowRow` 能自动重新换行，靠的是什么？

答案：

1. `Row` 永远只有一行、不会自动换行；子项过多时默认溢出（除非自己用 `weight` 压缩或套一个横向滚动容器）；`FlowRow` 一行放不下会自动折行。子项数量不定、宽度不一（如标签流）时必须用 `FlowRow`。
2. `horizontalArrangement` 控制同一行内相邻子项的水平间距，`verticalArrangement` 控制换行后各行之间的垂直间距。
3. 在本仓库基线中，基础 `FlowRow` 已稳定；`overflow` / `FlowRowOverflow` 旧 API 已 deprecated，应改用基础重载、`maxLines` 和自己的状态控制。对应的 Flutter `Wrap` 是稳定组件，不需要任何额外声明。
4. 靠 `remember { mutableStateListOf(...) }` 提供的可观察列表：`add`/`removeAt` 会触发重组，`FlowRow` 按新的子项集合重新测量并换行。

## 9. 本章总结

`FlowRow` / `FlowColumn` 负责「放不下就折行」的流式布局，`horizontalArrangement` 管行内间距、`verticalArrangement` 管行间距，`maxItemsInEachRow` 可强制每行数量上限，`maxLines` 用于限制行数，展开/收起可通过自己的状态控制渲染项；已弃用的 `overflow` / `FlowRowOverflow` 不应成为新代码的实现方向。它对应 Flutter 的 `Wrap`，但多了一个 `Wrap` 没有的 `maxItemsInEachRow`。在本仓库基线中基础 `FlowRow` 不需要 `@OptIn`；数据侧用 `mutableStateListOf` 就能让增删实时触发重新换行。

官方参考：[Flow layouts in Compose](https://developer.android.com/develop/ui/compose/layouts/flow)、[Compose Foundation 发布说明](https://developer.android.com/jetpack/androidx/releases/compose-foundation)。
