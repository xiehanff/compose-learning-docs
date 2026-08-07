# 第 79 章：响应式布局与 WindowSizeClass

## 1. 本章目标

学完后，你应当能够：

- 理解当前 WindowSizeClass 的 compact、medium、expanded、large、extra-large 宽度体系
- 用 currentWindowAdaptiveInfo 获取窗口信息，按空间能力切换布局结构
- 掌握 NavigationBar / NavigationRail / NavigationDrawer 的取舍与切换
- 会用 BoxWithConstraints 做轻量响应式，避开"设计稿等比放大"的误区

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.material3.adaptive:adaptive`：`currentWindowAdaptiveInfo`、`WindowAdaptiveInfo` 与 `WindowSizeClass`
- `androidx.compose.material3.adaptive:adaptive-layout` / `adaptive-navigation`：规范的多窗格布局与导航
- `androidx.compose.material3`：NavigationBar、NavigationRail、NavigationDrawer、Scaffold
- `androidx.compose.foundation.layout.BoxWithConstraints`

## 3. 项目源码片段

根据窗口宽度类别切换三种导航结构，实现"一套代码适配手机、折叠屏、平板"。

```kotlin
@Composable
fun ResponsiveHome(windowSizeClass: WindowSizeClass) {
    when {
        // Expanded 及更宽窗口：双栏主从布局
        windowSizeClass.isWidthAtLeastBreakpoint(
            WIDTH_DP_EXPANDED_LOWER_BOUND
        ) -> Row(Modifier.fillMaxSize()) {
            ListPane(Modifier.weight(1f))
            DetailPane(Modifier.weight(2f))
        }

        // Medium：侧边导航栏
        windowSizeClass.isWidthAtLeastBreakpoint(
            WIDTH_DP_MEDIUM_LOWER_BOUND
        ) -> Scaffold { padding ->
            Row {
                NavigationRail { /* 三个 NavigationRailItem */ }
                MainContent(Modifier.padding(padding))
            }
        }

        // Compact：底部导航
        else -> Scaffold(
            bottomBar = { NavigationBar { /* 三个 NavigationBarItem */ } }
        ) { padding -> MainContent(Modifier.padding(padding)) }
    }
}

@Composable
fun App() {
    val windowSizeClass = currentWindowAdaptiveInfo(
        supportLargeAndXLargeWidth = true
    ).windowSizeClass
    ResponsiveHome(windowSizeClass)
}
```

轻量替代：不引入 window 库时，用 BoxWithConstraints 在组合内读可用尺寸。

```kotlin
BoxWithConstraints {
    if (maxWidth < 600.dp) CompactLayout()
    else ExpandedLayout()
}
```

## 4. 逐步解释

1. 断点体系：当前自适应指南把宽度分为 compact（<600dp）、medium（600~839dp）、expanded（840~1199dp）、large（1200~1599dp）和 extra large（≥1600dp）；高度仍主要使用 compact（<480dp）、medium（480~899dp）、expanded（≥900dp）。多数移动应用先处理前三档即可，桌面窗口和超大屏再启用 large/extra-large。断点依据是可用窗口空间，不是设备型号或物理分辨率。

2. 获取方式：Material 3 Adaptive 提供 `currentWindowAdaptiveInfo()`，在组合中返回当前窗口尺寸类别和姿态信息，并随窗口变化更新。它替代了教程中通过 `LocalContext.current as Activity` 调用旧 `calculateWindowSizeClass` 的写法，也避免 Context 强制转换。

3. 结构切换不是缩放：Compact 用底部导航是因为拇指区域方便；Expanded 用双栏主从是因为宽度足够放列表 + 详情。这是"布局结构"的切换，而不是把设计稿等比放大——等比放大只改字号和间距，不改变信息架构，平板屏幕利用率依然很低。这正是第 45 章"拒绝等比放大"结论的延续。

4. 导航的取舍：NavigationBar 适合紧凑空间，NavigationRail 适合中等宽度，更宽窗口可使用永久 Drawer 或 Material 3 Adaptive 的 NavigationSuiteScaffold。切换时复用同一份导航目的地和选中状态，不要维护三套业务状态。

5. 重组驱动：旋转、分屏、桌面窗口缩放或折叠屏展开时，`currentWindowAdaptiveInfo()` 提供的新窗口信息会触发重组，布局结构重新选择。不要在 Activity 启动时只计算一次，也不要使用设备型号判断布局。

6. 双栏主从：Expanded 常见模式是主从（master-detail）：左边列表、右边详情，点击列表项更新右侧详情。手机上走"列表页 → 点击 → 详情页"两步导航，平板上一屏同时呈现——共享同一份 ViewModel 与状态，只是导航方式不同。

记住：响应式 = 按空间能力换结构，不是按屏幕尺寸调字号。

## 5. 实际执行过程

1. App 启动，组合获取 WindowSizeClass；普通手机竖屏通常命中 Compact width。
2. when 分支命中 Compact，渲染 Scaffold + 底部 NavigationBar。
3. 用户展开折叠屏，窗口宽度变为约 700dp：window 库监听到窗口变化，重新计算类别为 Medium。
4. 类别变化触发重组，when 分支切到 Medium，底部导航换成 NavigationRail，内容区同步刷新。
5. 横屏平板（宽度约 1000dp）：类别为 Expanded，布局切换为 Row 双栏，左侧列表、右侧详情。
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
2. 在组合外只计算一次尺寸：窗口变化后不会更新。使用组合内的 currentWindowAdaptiveInfo，把窗口信息作为状态向下传递。
3. 用固定 dp 写死布局：写死 800.dp 之类的宽度判断，旋转或分屏立刻错位。一律走类别判断或 BoxWithConstraints。
4. 折叠屏展开时布局不刷新：没把窗口变化接到状态里。使用 currentWindowAdaptiveInfo，让系统驱动重组。
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

WindowSizeClass 使用 600、840、1200、1600dp 等断点描述从 compact 到 extra-large 的窗口能力。通过 currentWindowAdaptiveInfo、导航套件和规范多窗格布局，可以让同一份状态适应手机、折叠屏、平板和桌面窗口。核心原则始终是按当前空间换结构，而不是按设备名称等比缩放。

官方参考：

- [Use window size classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes)
- [Build a list-detail layout](https://developer.android.com/develop/adaptive-apps/guides/list-detail)
