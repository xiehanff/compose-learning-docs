# 第 79 章：响应式布局与 WindowSizeClass

## 1. 本章目标

学完后，你应当能够：

- 理解 Material 3 的 WindowSizeClass 断点体系（600 / 840dp）
- 用 calculateWindowSizeClass 获取窗口尺寸类别，按类别切换布局结构
- 掌握 NavigationBar / NavigationRail / NavigationDrawer 的取舍与切换
- 会用 BoxWithConstraints 做轻量响应式，避开"设计稿等比放大"的误区

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.window:window`：calculateWindowSizeClass、WindowSizeClass、WindowWidthSizeClass、WindowHeightSizeClass；新版还提供 LocalWindowInfo 供组合内直接读取窗口信息
- `androidx.compose.material3`：NavigationBar、NavigationRail、NavigationDrawer、Scaffold
- `androidx.compose.foundation.layout.BoxWithConstraints`

## 3. 项目源码片段

根据窗口宽度类别切换三种导航结构，实现"一套代码适配手机、折叠屏、平板"。

```kotlin
@Composable
fun ResponsiveHome(windowSizeClass: WindowSizeClass) {
    when (windowSizeClass.widthSizeClass) {
        // 手机竖屏：底部导航
        WindowWidthSizeClass.Compact -> Scaffold(
            bottomBar = { NavigationBar { /* 三个 NavigationBarItem */ } }
        ) { padding -> MainContent(Modifier.padding(padding)) }

        // 中等宽度（折叠屏展开、平板竖屏）：侧边导航栏
        WindowWidthSizeClass.Medium -> Scaffold { padding ->
            Row {
                NavigationRail { /* 三个 NavigationRailItem */ }
                MainContent(Modifier.padding(padding))
            }
        }

        // 宽屏（平板横屏）：双栏主从布局
        WindowWidthSizeClass.Expanded -> Row(Modifier.fillMaxSize()) {
            ListPane(Modifier.weight(1f))   // 左侧列表
            DetailPane(Modifier.weight(2f)) // 右侧详情
        }
    }
}

@Composable
fun App() {
    val activity = LocalContext.current as Activity
    val windowSizeClass = calculateWindowSizeClass(activity)
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

1. 断点体系：Material 3 把窗口宽度分为 compact（<600dp）、medium（600~839dp）、expanded（≥840dp）三档，高度同样分三档（<480dp、480~899dp、≥900dp）。断点依据是"可用空间能容纳什么交互"，而不是设备分辨率。windowSizeClass 同时给出 widthSizeClass 与 heightSizeClass 两个维度。

2. 获取方式：calculateWindowSizeClass(activity) 基于当前窗口计算尺寸类别，内部处理了多窗口、分屏、折叠屏姿态等复杂情况；新版 androidx.window 提供 LocalWindowInfo，在组合内直接读取窗口信息，避免把 activity 传进组合。注意尺寸类别必须在组合内获取，才能作为响应式依赖参与重组。

3. 结构切换不是缩放：Compact 用底部导航是因为拇指区域方便；Expanded 用双栏主从是因为宽度足够放列表 + 详情。这是"布局结构"的切换，而不是把设计稿等比放大——等比放大只改字号和间距，不改变信息架构，平板屏幕利用率依然很低。这正是第 45 章"拒绝等比放大"结论的延续。

4. 三类导航的取舍：NavigationBar（底部、单层，适合紧凑空间）；NavigationRail（侧边、纵向，节省纵向空间、适合中屏）；NavigationDrawer（侧滑抽屉，适合宽屏把导航收起来、内容区最大化）。切换时复用同一份导航条目数据，只是渲染成不同组件，选中状态无需迁移。

5. 重组驱动：旋转屏幕或折叠屏展开时，窗口尺寸变化会让 calculateWindowSizeClass 返回新类别，触发重组，when 分支重新执行，布局结构自动切换。前提是尺寸类别是组合内计算出的响应式状态，而不是在 Activity 里一次性算好存成字段。

6. 双栏主从：Expanded 常见模式是主从（master-detail）：左边列表、右边详情，点击列表项更新右侧详情。手机上走"列表页 → 点击 → 详情页"两步导航，平板上一屏同时呈现——共享同一份 ViewModel 与状态，只是导航方式不同。

记住：响应式 = 按空间能力换结构，不是按屏幕尺寸调字号。

## 5. 实际执行过程

1. App 启动，组合计算 windowSizeClass：手机竖屏返回 widthSizeClass = Compact、heightSizeClass = Compact。
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

相似点：断点完全一致（Material 3 的 600 / 840dp 是跨框架的标准）；都是"读窗口尺寸 → 分支切结构"的模式；Flutter 的 Material 3 同样推荐按 WindowSizeClass 思路组织 NavigationBar / Rail / Drawer。

本质区别：Compose 侧有官方 window 库把"窗口状态 → 尺寸类别"封装成完整方案（自动处理多窗口、折叠屏姿态），组合内拿到一等公民的 WindowSizeClass；Flutter 侧没有内置 WindowSizeClass，标准做法是自己用 MediaQuery / LayoutBuilder 实现断点（或借助第三方包），折叠屏等异形窗口需要额外监听。另外 Compose 用声明式 when 分支 + 重组自动刷新，Flutter 的 build 方法同样随约束变化重建，机制相似但 API 形态不同。

## 7. 常见错误

1. 只适配宽度忽略高度类别：横屏手机上宽度是 medium 但高度很矮，底部导航仍占纵向空间。应同时考虑 heightSizeClass，矮屏优先用 NavigationRail 或抽屉。
2. 在组合外计算尺寸：把 calculateWindowSizeClass 结果存进普通类字段，窗口变化后不会更新。必须在组合内调用，让它成为可重组的依赖。
3. 用固定 dp 写死布局：写死 800.dp 之类的宽度判断，旋转或分屏立刻错位。一律走类别判断或 BoxWithConstraints。
4. 折叠屏展开时布局不刷新：没把窗口变化接到状态里。使用组合内 calculateWindowSizeClass / LocalWindowInfo，让系统驱动重组。
5. 把响应式与缩放混为一谈：还在做"大屏放大 1.5 倍"。响应式改的是结构，缩放改的是尺寸，只有前者真正解决问题。

## 8. 本章检查题

1. 三个宽度类别的断点分别是什么？高度类别呢？
2. 什么时候该把 NavigationBar 换成 NavigationRail？
3. 主从双栏适合什么类别？有什么好处？
4. 为什么不能在组合外计算尺寸类别？

答案：

1. 宽度：compact <600dp、medium 600~839dp、expanded ≥840dp；高度：compact <480dp、medium 480~899dp、expanded ≥900dp。
2. 当窗口宽度达到 medium（600dp 以上）且纵向空间紧张时——底部导航在宽屏上利用率低，NavigationRail 沿侧边排列，节省纵向空间、露出更多内容。
3. Expanded（≥840dp）。好处是一屏同时呈现列表与详情，减少页面跳转，信息架构更高效；手机上则用两步导航承载同一份状态。
4. 因为尺寸类别必须随窗口变化（旋转、分屏、折叠）实时更新；在组合外一次性计算就失去了响应式能力，布局永远不会刷新。

## 9. 本章总结

WindowSizeClass 用 600 / 840dp 断点把窗口抽象成 compact / medium / expanded 三档，配合 NavigationBar / Rail / Drawer 与双栏主从，实现"一套代码适配所有形态"。核心原则是：类别是响应式状态、切换的是布局结构而非缩放比例。把握住"按空间能力换结构"，响应式就不再是玄学。
