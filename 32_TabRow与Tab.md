# 第 32 章：TabRow 与 Tab

## 1. 本章目标

理解标签栏只提供标签（和选中指示器），内容区与左右滑动需要额外组合；同时认清 API 现状：旧的 `TabRow` 已废弃，新代码用 `PrimaryTabRow` / `SecondaryTabRow`。

学完后，你应当能够：

- 用 `PrimaryTabRow`（主标签）或 `SecondaryTabRow`（次级标签）搭标签栏
- 用 `selectedTabIndex` + `Tab(selected =, onClick =, text =)` 让所有标签共享同一选中状态
- 知道 `TabRow` 已被官方标记为 deprecated，旧代码/老教程里会遇到它
- 标签放不下时改用可滚动的 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`
- 用 `when` 切换简单内容，知道滑动页面通常使用 `HorizontalPager`
- 跑通标签栏与 `HorizontalPager` 的选中同步（以 `pagerState` 为单一真相）
- 能在标签栏与底部 `NavigationBar`（第 31 章）之间按场景选型

## 2. 项目中的位置

- `components/TabRowDemo.kt`（组件 id 为 `tabrow`，标题「TabRow」）

项目示例已经改用 `SecondaryTabRow`（源码里也留了注释说明），所以本章代码和项目现状一致。文件名与组件 id 保留了历史名字，第 40 章的新增步骤与第 99 章的源码索引都不受影响。

## 3. 项目源码片段

### 3.1 项目现有写法：SecondaryTabRow

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

### 3.2 换成主标签栏：PrimaryTabRow

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

### 3.3 标签栏 + HorizontalPager 联动：一个完整可运行的例子

`when` 切换没有手势、没有动画。要「点标签滚动过去、左右滑动时标签跟着走」，就接官方 Pager：

<!-- compile decls -->
```kotlin
// rememberPagerState/HorizontalPager 在 foundation.pager 子包，IMPORT_HEADER 未覆盖，需自行导入
import androidx.compose.foundation.pager.*

@Composable
fun TabPagerDemo() {
    val titles = listOf("关注", "推荐", "热点")
    // pageCount 是 lambda：页数以列表长度为准
    val pagerState = rememberPagerState { titles.size }
    val scope = rememberCoroutineScope()

    Column {
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,  // 标签栏直接读 pager：单一真相
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    // animateScrollToPage 是挂起函数，点击回调里要开协程（第 29 章）
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),  // 占满 Column 剩余高度（weight 见第 14 章）
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                Text("${titles[page]}页内容", Modifier.align(Alignment.Center))
            }
        }
    }
}
```

初始界面：顶部「关注」标签高亮，指示器在第一格，内容区显示「关注页内容」。

操作步骤：先点「热点」标签；等页面停稳后，再用手指在内容区横向拖动到一半松手。

期望观察：点标签时内容区带动画滚到第三页，指示器随之滑到「热点」下方；手势拖动过程中指示器就实时跟手移动，松手吸附到最近的标签后停稳。

## 4. 逐步解释

先说 API 现状，避免照着旧教程写出废弃代码：Material3 的 **`TabRow` 已被官方标记为已废弃（deprecated）**，API 参考里的原话是 "This function is deprecated. Replaced with PrimaryTabRow and SecondaryTabRow."；官方 Tabs 指南现在直接使用 `Tab` + `PrimaryTabRow` / `SecondaryTabRow`。项目示例也已经改用 `SecondaryTabRow`。旧项目和老文章里出现 `TabRow` 很正常，不用慌：它只是标签栏容器，核心参数含义（`selectedTabIndex` + `tabs` 内容）和现在的两个新组件一致，迁移基本就是把函数名换掉。

**主标签与次级标签的区别在于语义和使用位置。** `PrimaryTabRow` 是主标签，用来把页面级内容分区，通常贴在顶部内容区，选中指示器更突出；`SecondaryTabRow` 是次级标签，用于在一个内容区内部再分层（例如某个页面里再分「关注 / 推荐」）。两者都是**固定标签**（fixed tabs）：所有标签等宽、平均铺满整行、不滚动。标签多到一行放不下时，改用可滚动的 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`——它们不做等宽，可以横向滚动到屏幕外的标签。选中指示器用默认的即可（`PrimaryTabRow` 默认 `TabRowDefaults.PrimaryIndicator`，`SecondaryTabRow` 默认 `TabRowDefaults.SecondaryIndicator`，位置跟随 `selectedTabIndex`）；想换成自定义指示器可以传 `indicator` 参数，属于进阶内容，本章不展开。

**`Tab` 的用法。** 每个标签用 `Tab(selected = ..., onClick = ..., text = { Text(...) })` 声明。关键在于 `Tab` 自己**不持有**选中状态：`selected` 必须由外部状态算出来（通常是 `selectedTabIndex == index`），`onClick` 里更新的是那个外部状态。这和 `NavigationBarItem` 是同款模式——外部唯一真相，每个条目根据它计算自己的选中态。`text` 与 `icon` 都是可选 lambda，可以只给文字、只给图标，或者两个都给。

**内容区不在标签栏里。** `PrimaryTabRow` / `SecondaryTabRow` 只负责标签排列与选中指示器，不含页面内容，也不自动支持左右滑动。项目用 `when (selected)` 根据索引选一个内容分支，适合简单 Demo。需要滑动时接 `HorizontalPager`，并让 `pagerState.currentPage` 与标签点击互相同步：点标签时调 `pagerState.animateScrollToPage(index)`，页面滑动时把 `pagerState.currentPage` 写回标签状态。最好选一个状态作为主要真相（推荐以 Pager 为准），避免 `selected` 与 `pagerState` 双向更新形成循环。复杂页面还要考虑未选页面是否移出组合及状态保存（第 33、34 章）。

**Pager 的版本边界与来路。** 在本仓库基线（Compose BOM 2026.06.00，见 `snippets/gradle/libs.versions.toml`）中，`HorizontalPager` / `rememberPagerState` 已是稳定 API，不需要 `@OptIn(ExperimentalFoundationApi::class)`——官方 Compose Foundation 1.7.0（2024 年 9 月）起 Pager 系列转为稳定；更早版本的 foundation 里它们是实验 API，老教程代码里的 `@OptIn` 注解是版本痕迹。还有更老的教程用 Accompanist 的 `HorizontalPager`（`com.google.accompanist.pager`）：Accompanist 的定位就是「把 API 上游化进官方工具包后即废弃」（其仓库 README 原话），Pager 模块已随之废弃移除，新代码一律直接用 `androidx.compose.foundation.pager`。另外注意 `rememberPagerState` 的 `pageCount` 是 lambda（`() -> Int`），早期「直接传 Int」的重载已废弃。

**标签与 Pager 怎么同步：单一真相写法。** 3.3 的例子没有第二份 `selected` 状态：`selectedTabIndex`、每个 `Tab` 的 `selected`、内容页全部从 `pagerState` 派生。点击标签（事件）→ `animateScrollToPage` 滚动 → `currentPage` 变化（状态）→ 标签栏与指示器重组跟随（重组 → 界面）；手势滑动走的是同一条链，只是入口换成手势。没有第二条状态，自然没有「双向同步形成循环」的问题。另一种写法是自己维护 `selected`，再用 `LaunchedEffect`（第 25 章）观察 `pagerState` 把当前页写回——也能工作，但要小心点击与回写互相触发，初学阶段推荐单一真相。绑定 `currentPage` 还是 `settledPage` 有个细微差别（官方 KDoc）：`currentPage` 是「最接近吸附位置的页」，手势或动画滚动过程中就会更新，指示器跟手；`settledPage` 只在滚动停稳后更新，想要「拖一半标签不变、停稳才切换」就用它。

**TabRow 系与 NavigationBar 怎么选。** 两组组件都是「一排可选项 + 选中指示」，差异在 Material 3 给它们的定位：

| 维度 | `PrimaryTabRow` / `SecondaryTabRow` | `NavigationBar`（第 31 章） |
| --- | --- | --- |
| 位置 | 页面顶部或内容区上方 | 屏幕底部 |
| Material 3 定位 | 同一页面内的内容分区（primary 页面级 / secondary 区内分层） | App 顶层目的地切换 |
| 选中指示 | 指示条跟随选中标签 | 图标激活 + 胶囊底色 |
| 内容配套 | `when` 切换或 `HorizontalPager` 滑动 | `when` 保活或 NavController 路由（第 31、34 章） |
| 返回键语义 | 本身不产生返回栈条目 | 配 navigate 切换时返回键逐层弹出 |
| 典型场景 | 「关注 / 推荐 / 热点」并列内容流 | 「首页 / 搜索 / 设置」主功能区 |

一句话：标签栏回答「这页内容看哪一部分」，底栏回答「去 App 的哪个功能区」。两者可以同屏共存——底部 `NavigationBar` 切主功能区，页面顶部再放 `PrimaryTabRow` 分内容，互不冲突。

**状态记忆。** `remember { mutableIntStateOf(0) }` 只在重组间保留；如果希望旋转屏幕等配置变化后选中项还在，用 `rememberSaveable { mutableIntStateOf(0) }`（第 23 章）。注意 `selectedTabIndex` 传的是索引，数量要与 `tabs` 列表长度保持一致。

## 5. 实际执行过程

1. 首次组合：选中状态初始为 0，标签栏绘制所有标签，指示器落在第一个标签上
2. 用户点击某个 `Tab`：`onClick` 把对应索引写回状态
3. 状态变化触发重组：`selectedTabIndex` 与每个 `Tab` 的 `selected` 判断一起刷新
4. 指示器跟随到新的选中标签（默认指示器由 `TabRowDefaults.PrimaryIndicator` / `SecondaryIndicator` 提供，位置用 `tabIndicatorOffset` 跟随）
5. 内容区 `when (selected)` 选出对应分支并显示
6. 若接了 `HorizontalPager`：手势滚动时把 `pagerState.currentPage` 同步回标签，或点击标签时用 `pagerState.animateScrollToPage(index)` 滚动过去
7. `animateScrollToPage` 是挂起函数：点击回调里用 `rememberCoroutineScope().launch` 启动（第 29 章同款做法）

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

```kotlin
// 错误示例（故意错误，不参与编译；这段代码本身能通过编译，问题出在运行时行为）：
// 两份状态互相回写——手势拖动中 currentPage 就在变，selected 跟着变，
// LaunchedEffect(selected) 又立刻 animateScrollToPage，与用户正在进行的拖动抢页面
var selected by remember { mutableIntStateOf(0) }
val pagerState = rememberPagerState { 3 }

PrimaryTabRow(selectedTabIndex = selected) {
    Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text("关注") })
    Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text("推荐") })
}

LaunchedEffect(selected) {                    // 点击 → 滚动
    pagerState.animateScrollToPage(selected)
}
LaunchedEffect(pagerState.currentPage) {      // 滚动 → 回写：两条链路互相触发
    selected = pagerState.currentPage
}
```

修复思路就是 3.3 的写法：删掉 `selected`，标签栏直接读 `pagerState`，让「当前页」只有一份真相（LaunchedEffect 见第 25 章）。

## 8. 本章检查题

1. `TabRow` 现在还能用吗？新代码应该写什么？
2. `PrimaryTabRow` 与 `SecondaryTabRow` 分别用在哪里？标签放不下怎么办？
3. 标签栏与 `HorizontalPager` 各负责什么？
4. 项目的标签栏为什么还需要 `when`？
5. Flutter 的 `TabBar` / `TabBarView` 在 Compose 中对应什么？
6. 预测行为：在 3.3 中用手指把页面拖到「关注」和「推荐」中间松手，吸附到「推荐」的过程中，指示器什么时候移到第二个标签？如果把 `pagerState.currentPage` 全部换成 `pagerState.settledPage`，观感有什么变化？
7. 动手修改：给 3.3 增加第四个标签「视频」（titles 加一项即可），验证标签栏、Pager 页数与内容页是否自动对齐；再故意把 `rememberPagerState { titles.size }` 里的 `titles.size` 写成固定 `3`，观察第四页还能不能滑到。

答案：

1. 已被官方标记为 deprecated（API 参考原话是 Replaced with `PrimaryTabRow` and `SecondaryTabRow`），旧代码里还能见到，但新代码要写 `PrimaryTabRow`（主标签）或 `SecondaryTabRow`（次级标签），迁移基本只是换函数名。
2. `PrimaryTabRow` 用于页面顶部的主标签分区；`SecondaryTabRow` 用于内容区内部的次级分层。两者都是等宽固定的，标签放不下时改用可滚动的 `PrimaryScrollableTabRow` / `SecondaryScrollableTabRow`。
3. 标签栏负责标签排列与选中指示器（不含内容、不管滑动）；`HorizontalPager` 负责可滑动的页面内容，两者通过同一个选中状态同步。
4. 项目是简单 Demo，标签栏不包含内容区，所以用 `when (selected)` 选择一个内容分支，不需要引入滑动容器。
5. `TabBar`（及 `TabBar.secondary`）对应 `PrimaryTabRow` / `SecondaryTabRow`，`TabBarView` 对应 `HorizontalPager`，并与标签栏的选中状态同步（`pagerState.currentPage` 与 Tab 点击双向对齐，选一个作为主要真相）。
6. currentPage 在手势与吸附动画过程中就持续更新，所以指示器从拖动起就实时跟手，吸附途中已在第二格；换成 settledPage 后，滚动全程指示器停在原处，直到页面完全停稳才一次性跳到第二格（两者语义区别见 4 节引用的官方 KDoc）。
7. titles 加到四项后，pageCount lambda 返回 4，标签栏四个标签、可滑四页、内容一一对应——这正是 pageCount 用 lambda 的好处，页数跟随数据。固定写 3 时 Pager 只认三页：第四个标签仍显示、点击也会尝试滚动到第 3 页，但页面滑不过去或行为不完整，页数与标签数不一致从来不是受支持的状态。

## 9. 本章总结

`TabRow` 已废弃，标签栏用 `PrimaryTabRow`（主标签、页面顶部）或 `SecondaryTabRow`（次级、内容区分层），放不下就用可滚动版本。`Tab` 靠每项自带 `selected` + `onClick` 声明选中态，索引由父级的 `selectedTabIndex` 统一持有，与 `NavigationBarItem` 同款模式。标签栏只是标签栏：简单内容用 `when`，手势切页用 `HorizontalPager`（本仓库基线中已是稳定 API），同步首选「以 `pagerState` 为单一真相」的写法。选型上记住分工：标签栏管页面内分区，底栏 `NavigationBar` 管顶层目的地。

官方参考：

- [PrimaryTabRow（API 参考）](https://developer.android.com/reference/kotlin/androidx/compose/material3/PrimaryTabRow)
- [Tab（API 参考）](https://developer.android.com/reference/kotlin/androidx/compose/material3/Tab)
- [Material 3 规范：Tabs（primary / secondary 定位）](https://m3.material.io/components/tabs/overview)
- [Pager in Compose（官方指南：HorizontalPager 与 rememberPagerState）](https://developer.android.com/develop/ui/compose/layouts/pager)
- [Compose Foundation 版本说明（1.7.0 起 Pager 转稳定）](https://developer.android.com/jetpack/androidx/releases/compose-foundation)
- [Accompanist 仓库（上游化后即废弃的定位说明）](https://github.com/google/accompanist)
