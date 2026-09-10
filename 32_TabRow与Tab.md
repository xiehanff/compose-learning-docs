# 第 32 章：TabRow 与 Tab

## 1. 本章目标

理解标签栏只提供标签（和选中指示器），内容区与左右滑动需要额外组合；同时认清 API 现状：旧的 `TabRow` 已废弃，新代码用 `PrimaryTabRow` / `SecondaryTabRow`。

学完后，你应当能够：

- 用 `PrimaryTabRow`（主标签）或 `SecondaryTabRow`（次级标签）搭标签栏
- 用 `selectedTabIndex` + `Tab(selected =, onClick =, text =)` 让所有标签共享同一选中状态
- 知道 `TabRow` 已被官方标记为 deprecated，旧代码/老教程里会遇到它
- 标签放不下时改用可滚动的 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`
- 用 `when` 切换简单内容，知道滑动页面通常使用 `HorizontalPager`

## 2. 项目中的位置

- `components/TabRowDemo.kt`（组件 id 为 `tabrow`，标题「TabRow」）

项目示例已经改用 `SecondaryTabRow`（源码里也留了注释说明），所以本章代码和项目现状一致。文件名与组件 id 保留了历史名字，第 40 章的新增步骤与第 99 章的源码索引都不受影响。

## 3. 项目源码片段

项目现有代码（节选：省略顶部 import、`Column` 的间隔与文字样式参数）：

<!-- compile -->
```kotlin
var selected by remember { mutableIntStateOf(0) }

SecondaryTabRow(selectedTabIndex = selected) {
    Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text("标签 A") })
    Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text("标签 B") })
    Tab(selected = selected == 2, onClick = { selected = 2 }, text = { Text("标签 C") })
}

when (selected) {
    0 -> Text("标签 A 的内容")
    1 -> Text("标签 B 的内容")
    2 -> Text("标签 C 的内容")
}
```

换成主标签栏只需改一个函数名，用法完全相同（节选：`selected` 沿用上一段，`titles` 是标签文字列表）：

<!-- compile
prelude: var selected by remember { mutableIntStateOf(0) }
-->
```kotlin
// PrimaryTabRow：主标签，通常放在页面顶部内容区；Tab 的写法一字不改
val titles = listOf("标签 A", "标签 B", "标签 C")

PrimaryTabRow(selectedTabIndex = selected) {
    titles.forEachIndexed { index, title ->
        Tab(
            selected = selected == index,
            onClick = { selected = index },
            text = { Text(title) },
        )
    }
}
```

## 4. 逐步解释

先说 API 现状，避免照着旧教程写出废弃代码：Material3 的 **`TabRow` 已被官方标记为已废弃（deprecated）**，API 参考里的原话是 "This function is deprecated. Replaced with PrimaryTabRow and SecondaryTabRow."；官方 Tabs 指南现在直接使用 `Tab` + `PrimaryTabRow` / `SecondaryTabRow`。项目示例也已经改用 `SecondaryTabRow`。旧项目和老文章里出现 `TabRow` 很正常，不用慌：它只是标签栏容器，核心参数含义（`selectedTabIndex` + `tabs` 内容）和现在的两个新组件一致，迁移基本就是把函数名换掉。

**主标签与次级标签的区别在于语义和使用位置。** `PrimaryTabRow` 是主标签，用来把页面级内容分区，通常贴在顶部内容区，选中指示器更突出；`SecondaryTabRow` 是次级标签，用于在一个内容区内部再分层（例如某个页面里再分「关注 / 推荐」）。两者都是**固定标签**（fixed tabs）：所有标签等宽、平均铺满整行、不滚动。标签多到一行放不下时，改用可滚动的 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`——它们不做等宽，可以横向滚动到屏幕外的标签。选中指示器用默认的即可（`PrimaryTabRow` 默认 `TabRowDefaults.PrimaryIndicator`，`SecondaryTabRow` 默认 `TabRowDefaults.SecondaryIndicator`，位置跟随 `selectedTabIndex`）；想换成自定义指示器可以传 `indicator` 参数，属于进阶内容，本章不展开。

**`Tab` 的用法。** 每个标签用 `Tab(selected = ..., onClick = ..., text = { Text(...) })` 声明。关键在于 `Tab` 自己**不持有**选中状态：`selected` 必须由外部状态算出来（通常是 `selectedTabIndex == index`），`onClick` 里更新的是那个外部状态。这和 `NavigationBarItem` 是同款模式——外部唯一真相，每个条目根据它计算自己的选中态。`text` 与 `icon` 都是可选 lambda，可以只给文字、只给图标，或者两个都给。

**内容区不在标签栏里。** `PrimaryTabRow` / `SecondaryTabRow` 只负责标签排列与选中指示器，不含页面内容，也不自动支持左右滑动。项目用 `when (selected)` 根据索引选一个内容分支，适合简单 Demo。需要滑动时接 `HorizontalPager`，并让 `pagerState.currentPage` 与标签点击互相同步：点标签时调 `pagerState.animateScrollToPage(index)`，页面滑动时把 `pagerState.currentPage` 写回标签状态。最好选一个状态作为主要真相（推荐以 Pager 为准），避免 `selected` 与 `pagerState` 双向更新形成循环。复杂页面还要考虑未选页面是否移出组合及状态保存（第 33、34 章）。

**状态记忆。** `remember { mutableIntStateOf(0) }` 只在重组间保留；如果希望旋转屏幕等配置变化后选中项还在，用 `rememberSaveable { mutableIntStateOf(0) }`（第 23 章）。注意 `selectedTabIndex` 传的是索引，数量要与 `tabs` 列表长度保持一致。

## 5. 实际执行过程

1. 首次组合：选中状态初始为 0，标签栏绘制所有标签，指示器落在第一个标签上
2. 用户点击某个 `Tab`：`onClick` 把对应索引写回状态
3. 状态变化触发重组：`selectedTabIndex` 与每个 `Tab` 的 `selected` 判断一起刷新
4. 指示器跟随到新的选中标签（默认指示器由 `TabRowDefaults.PrimaryIndicator` / `SecondaryIndicator` 提供，位置用 `tabIndicatorOffset` 跟随）
5. 内容区 `when (selected)` 选出对应分支并显示
6. 若接了 `HorizontalPager`：手势滚动时把 `pagerState.currentPage` 同步回标签，或点击标签时用 `pagerState.animateScrollToPage(index)` 滚动过去

## 6. Flutter / Dart 3 对照

Flutter 的 `TabBar` + `TabBarView` 通常由 `TabController` 协调；Compose 的标签栏更轻，内容区和 Pager 都要自己组合。primary / secondary 的区分 Flutter 也有：`TabBar` 是主标签栏，`TabBar.secondary` 用于内容区分层，分别对应 `PrimaryTabRow` 与 `SecondaryTabRow`；`TabBar(isScrollable: true)` 对应可滚动的 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`。

```dart
DefaultTabController(
  length: 3,
  child: Column(children: const [
    TabBar(tabs: [Tab(text: 'A'), Tab(text: 'B'), Tab(text: 'C')]),
    Expanded(child: TabBarView(children: [Text('A'), Text('B'), Text('C')])),
  ]),
);
```

差别在于“谁来管选中索引”：Flutter 由 `TabController` / `DefaultTabController` 持有索引，`TabBarView` 会自动跟着切换；Compose 的选中索引就是你自己声明的一个 `Int` 状态，标签栏与内容区（`when` 或 `HorizontalPager`）都必须显式读它。

## 7. 常见错误

- 在新代码里继续用 `TabRow`：它已废弃，编译会给出 deprecation 警告，应换成 `PrimaryTabRow` 或 `SecondaryTabRow`
- 位置用错：把 `SecondaryTabRow` 当页面主标签，或把 `PrimaryTabRow` 塞进内容区做次级分层——按语义选
- 标签多到一行放不下还硬用固定标签栏：改用 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`
- 以为标签栏自带内容页
- `Tab` 和 `selectedTabIndex` 使用不同状态源
- Pager 与 Tab 双向同步互相触发，形成循环
- 切换复杂内容后忽略状态丢失

## 8. 本章检查题

1. `TabRow` 现在还能用吗？新代码应该写什么？
2. `PrimaryTabRow` 与 `SecondaryTabRow` 分别用在哪里？标签放不下怎么办？
3. 标签栏与 `HorizontalPager` 各负责什么？
4. 项目的标签栏为什么还需要 `when`？
5. Flutter 的 `TabBar` / `TabBarView` 在 Compose 中对应什么？

答案：

1. 已被官方标记为 deprecated（API 参考原话是 Replaced with `PrimaryTabRow` and `SecondaryTabRow`），旧代码里还能见到，但新代码要写 `PrimaryTabRow`（主标签）或 `SecondaryTabRow`（次级标签），迁移基本只是换函数名。
2. `PrimaryTabRow` 用于页面顶部的主标签分区；`SecondaryTabRow` 用于内容区内部的次级分层。两者都是等宽固定的，标签放不下时改用可滚动的 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`。
3. 标签栏负责标签排列与选中指示器（不含内容、不管滑动）；`HorizontalPager` 负责可滑动的页面内容，两者通过同一个选中状态同步。
4. 项目是简单 Demo，标签栏不包含内容区，所以用 `when (selected)` 选择一个内容分支，不需要引入滑动容器。
5. `TabBar`（及 `TabBar.secondary`）对应 `PrimaryTabRow` / `SecondaryTabRow`，`TabBarView` 对应 `HorizontalPager`，并与标签栏的选中状态同步（`pagerState.currentPage` 与 Tab 点击双向对齐，选一个作为主要真相）。

## 9. 本章总结

`TabRow` 已废弃，标签栏用 `PrimaryTabRow`（主标签、页面顶部）或 `SecondaryTabRow`（次级、内容区分层），放不下就用可滚动版本。`Tab` 靠每项自带 `selected` + `onClick` 声明选中态，索引由父级的 `selectedTabIndex` 统一持有，与 `NavigationBarItem` 同款模式。标签栏只是标签栏：简单内容用 `when`，手势切页用 `HorizontalPager`，并统一管理选中状态。
