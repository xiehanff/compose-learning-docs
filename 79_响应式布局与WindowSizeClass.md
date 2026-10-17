# 第 79 章：响应式布局与 WindowSizeClass

## 1. 本章目标

学完后，你应当能够：

- 理解当前 WindowSizeClass 的 compact、medium、expanded、large、extra-large 宽度体系
- 用当前 Material 3 Adaptive API 获取窗口信息，按空间能力切换布局结构
- 掌握 NavigationBar / NavigationRail / NavigationDrawer 的取舍与切换
- 会用 BoxWithConstraints 做轻量响应式，避开"设计稿等比放大"的误区

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.material3.adaptive:adaptive:1.3.0-rc01`：`currentWindowAdaptiveInfoV2`、`WindowAdaptiveInfo`；`WindowSizeClass` 类型与断点常量来自它传递的 `androidx.window:window-core`。该依赖不在 Compose BOM 管理内，已纳入 snippets 编译工程（见 `snippets/gradle/libs.versions.toml`）
- `androidx.compose.material3.adaptive:adaptive-layout` / `adaptive-navigation`：规范的多窗格布局与导航（本章未使用）
- `androidx.compose.material3`：NavigationBar、NavigationRail、NavigationDrawer、Scaffold
- `androidx.compose.foundation.layout.BoxWithConstraints`

## 3. 项目源码片段

根据窗口宽度类别切换三种导航结构，实现"一套代码适配手机、折叠屏、平板"。示例中的 `ListPane`、`DetailPane`、`MainContent` 是同片段内的教学占位组件（只渲染占位文本），真实项目中替换为页面级实现；本示例只演示结构切换，不构成完整导航与运行验收。

<!-- compile decls
-->
```kotlin
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

@Composable
fun ResponsiveHome(windowSizeClass: WindowSizeClass) {
    when {
        // Expanded 及更宽窗口：双栏主从布局也放进 Scaffold content 并应用
        // innerPadding，让双栏避开系统栏。断点判断从大到小依次进行
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) ->
            Scaffold { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    ListPane(Modifier.weight(1f))
                    DetailPane(Modifier.weight(2f))
                }
            }

        // Medium：侧边导航栏。内容区要占满剩余宽度，并应用 Scaffold 的 innerPadding
        windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            Scaffold { padding ->
                Row(Modifier.fillMaxSize()) {
                    NavigationRail {
                        NavigationRailItem(
                            selected = true,
                            onClick = {},
                            icon = { Icon(Icons.Filled.Home, contentDescription = "首页") }
                        )
                        NavigationRailItem(
                            selected = false,
                            onClick = {},
                            icon = { Icon(Icons.Filled.Search, contentDescription = "搜索") }
                        )
                    }
                    MainContent(
                        Modifier
                            .weight(1f)
                            .padding(padding)
                    )
                }
            }

        // Compact：底部导航
        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = true,
                        onClick = {},
                        icon = { Icon(Icons.Filled.Home, contentDescription = "首页") }
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = {},
                        icon = { Icon(Icons.Filled.Search, contentDescription = "搜索") }
                    )
                }
            }
        ) { padding -> MainContent(Modifier.padding(padding)) }
    }
}

// 教学占位组件：替换为真实页面实现
@Composable
private fun ListPane(modifier: Modifier = Modifier) {
    Column(modifier) { Text("列表") }
}

@Composable
private fun DetailPane(modifier: Modifier = Modifier) {
    Column(modifier) { Text("详情") }
}

@Composable
private fun MainContent(modifier: Modifier = Modifier) {
    Column(modifier) { Text("内容") }
}

@Composable
fun App() {
    // 1.3.0-rc01 中 currentWindowAdaptiveInfoV2() 默认支持全部五档宽度；
    // 旧签名 currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true) 在本版本已弃用
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    ResponsiveHome(windowSizeClass)
}
```

轻量替代：不引入 window 库时，用 BoxWithConstraints 在组合内读可用尺寸。

<!-- compile -->
```kotlin
BoxWithConstraints {
    // maxWidth 是当前约束下的最大宽度，随窗口/父容器变化触发重新执行。
    // 这是简化的二分示例：只区分 Compact 与非 Compact（600dp 及以上），
    // 不是完整的五档分类；需要 600/840 断点时用上文的 WindowSizeClass
    if (maxWidth < 600.dp) {
        Text("Compact 布局")
    } else {
        Text("非 Compact（Medium 及以上）布局")
    }
}
```

## 4. 逐步解释

1. 断点体系：Android 自适应指南把宽度分为 compact（<600dp）、medium（600~839dp）、expanded（840~1199dp）、large（1200~1599dp）和 extra large（≥1600dp）；高度分为 compact（<480dp）、medium（480~899dp）、expanded（≥900dp）。多数移动应用先处理前三档即可，桌面窗口和超大屏再处理 large/extra-large。断点依据是分配给应用的窗口空间，不是设备型号或物理分辨率。这些数值在代码中以 `WindowSizeClass.Companion` 的常量（`WIDTH_DP_MEDIUM_LOWER_BOUND` 等）提供，以当前 window-core 版本为准。

2. 获取方式：本章基线 adaptive 1.3.0-rc01 使用 `currentWindowAdaptiveInfoV2()`：它由 1.3.0-alpha10 引入（同版本弃用旧的 `currentWindowAdaptiveInfo`），1.3.0-rc01 已包含，默认支持 large/extra-large 五档宽度。若你的项目还停在 1.2.x 等旧版本，则应使用带 `supportLargeAndXLargeWidth = true` 的旧签名并允许 L/XL——迁移以对应版本的发布说明为准，不要跨版本照抄。

3. 结构切换不是缩放：Compact 用底部导航是因为拇指区域方便；Expanded 用双栏主从是因为宽度足够放列表 + 详情。这是"布局结构"的切换，而不是把设计稿等比放大——等比放大只改字号和间距，不改变信息架构，平板屏幕利用率依然很低。这正是第 45 章"拒绝等比放大"结论的延续。

4. 导航的取舍：NavigationBar 适合紧凑空间，NavigationRail 适合中等宽度，更宽窗口可使用永久 Drawer 或 Material 3 Adaptive 的 NavigationSuiteScaffold。切换时复用同一份导航目的地和选中状态，不要维护三套业务状态。Insets 的关键是消费归属唯一：Scaffold 的 innerPadding 要被消费、且只消费一次。示例 Medium 分支把 `padding` 施加在 Rail 右侧的内容区上（Rail 贴边排列），`MainContent` 用 `Modifier.weight(1f)` 占满剩余宽度；Expanded 分支的双栏 Row 整体在 Scaffold content 内应用 `padding`（行内没有导航组件，整行消费一次即可）。漏掉 innerPadding 会让内容顶进系统栏区域；而「把 padding 施加到包含 Rail 的整个 Row」并非错误方案——它让 Rail 也避开系统栏，只是布局取向不同——真正要避免的是同一方向上重复消费造成双重留白。

5. 重组驱动：旋转、分屏、桌面窗口缩放或折叠屏展开时，`currentWindowAdaptiveInfoV2()` 提供的新窗口信息会触发重组，布局结构重新选择。不要在 Activity 启动时只计算一次，也不要使用设备型号判断布局。

6. 双栏主从：Expanded 常见模式是主从（master-detail）：左边列表、右边详情，点击列表项更新右侧详情。手机上走"列表页 → 点击 → 详情页"两步导航，平板上一屏同时呈现——共享同一份 ViewModel 与状态，只是导航方式不同。规范实现可进一步使用 adaptive-navigation 的 ListDetailPaneScaffold（本章未展开）。

记住：响应式 = 按空间能力换结构，不是按屏幕尺寸调字号。

## 5. 实际执行过程

1. App 启动，组合获取 WindowSizeClass；普通手机竖屏通常命中 Compact width。
2. when 分支命中 Compact，渲染 Scaffold + 底部 NavigationBar。
3. 用户展开折叠屏，窗口宽度变为约 700dp：窗口信息变化触发重组，重新计算类别为 Medium。
4. when 分支切到 Medium，底部导航换成 NavigationRail，内容区占满剩余宽度并保持 innerPadding，内容同步刷新。
5. 横屏平板（宽度约 1000dp）：类别为 Expanded，布局切换为 Scaffold content 内的双栏 Row（应用 innerPadding），左侧列表、右侧详情。
6. 用户点击列表项，主从状态更新，右侧详情联动刷新——状态与布局结构完全解耦。

## 6. Flutter / Dart 3 对照

Flutter 用 LayoutBuilder 或 MediaQuery 拿宽度，按 Material 3 断点分支（Dart 3 关系模式）：

```dart
Widget build(BuildContext context) {
  final width = MediaQuery.sizeOf(context).width;
  return switch (width) {
    < 600 => const BottomNavScaffold(), // compact
    < 840 => const RailScaffold(),      // medium
    _ => const TwoPaneLayout(),         // expanded
  };
}

// 或 LayoutBuilder 按约束宽度分支
LayoutBuilder(builder: (context, constraints) {
  if (constraints.maxWidth < 600) return const CompactLayout();
  if (constraints.maxWidth < 840) return const MediumLayout();
  return const ExpandedLayout();
});
```

相似点：都可以采用 600/840dp 作为移动端前三档的核心断点，遵循“读窗口尺寸 → 分支切结构”的模式；如果支持桌面级超大窗口，还应继续处理 1200/1600dp 边界。

本质区别：Compose 的 Material 3 Adaptive 库把窗口尺寸、姿态、规范多窗格布局和导航整合成官方方案；Flutter 通常使用 MediaQuery/LayoutBuilder 自行维护断点和窗格策略。两端都必须响应“当前窗口”，而不是读取一次整块屏幕尺寸。

## 7. 常见错误

1. 只适配宽度忽略高度类别：横屏手机上宽度是 medium 但高度很矮，底部导航仍占纵向空间。应同时考虑 heightSizeClass，矮屏优先用 NavigationRail 或抽屉。
2. 在组合外只计算一次尺寸：窗口变化后不会更新。使用组合内的 `currentWindowAdaptiveInfoV2()`（或旧版本的 `currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true)`），把窗口信息作为状态向下传递。
3. 用固定 dp 写死布局：写死 800.dp 之类的宽度判断，旋转或分屏立刻错位。一律走类别判断或 BoxWithConstraints。
4. Insets 重复消费或漏消费：Scaffold 的 innerPadding 必须被消费且只消费一次。漏掉它内容会顶进系统栏；施加到包含 NavigationRail 的整行是另一种可行方案（Rail 也避开系统栏），但不能与内容区上的 padding 叠加，否则同一方向双重留白。
5. 把响应式与缩放混为一谈：还在做"大屏放大 1.5 倍"。响应式改的是结构，缩放改的是尺寸，只有前者真正解决问题。

## 8. 本章检查题

1. 五个宽度类别的断点分别是什么？多数移动应用最先处理哪三档？
2. 什么时候该把 NavigationBar 换成 NavigationRail？
3. 主从双栏适合什么类别？有什么好处？
4. 为什么不能在 Activity 启动时只计算一次尺寸类别？

答案：

1. 宽度：compact <600dp、medium 600~839dp、expanded 840~1199dp、large 1200~1599dp、extra large ≥1600dp；多数移动应用先处理 compact、medium、expanded。
2. 当窗口宽度达到 medium（600dp 以上）且纵向空间紧张时——底部导航在宽屏上利用率低，NavigationRail 沿侧边排列，节省纵向空间、露出更多内容。
3. Expanded（≥840dp）。好处是一屏同时呈现列表与详情，减少页面跳转，信息架构更高效；手机上则用两步导航承载同一份状态。
4. 因为尺寸类别必须随旋转、分屏、折叠和桌面窗口缩放实时更新；启动时一次性计算会失去响应式能力。

## 9. 本章总结

WindowSizeClass 使用 600、840、1200、1600dp 等断点描述从 compact 到 extra-large 的窗口能力。通过本章基线（adaptive 1.3.0-rc01）的 `currentWindowAdaptiveInfoV2()`、导航组件和主从双栏结构，可以让同一份状态适应手机、折叠屏、平板和桌面窗口。核心原则始终是按当前空间换结构，而不是按设备名称等比缩放。

官方参考：

- [Use window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Material 3 Adaptive 发布说明（1.3.0-alpha10 引入 currentWindowAdaptiveInfoV2 并弃用旧签名，1.3.0-rc01 已包含）](https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive#1.3.0-alpha10)
- [currentWindowAdaptiveInfoV2 API](https://developer.android.com/reference/kotlin/androidx/compose/material3/adaptive/currentWindowAdaptiveInfoV2.composable)
- [Build a list-detail layout](https://developer.android.com/develop/adaptive-apps/guides/list-detail)
