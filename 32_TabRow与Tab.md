# 第 32 章：TabRow 与 Tab

## 1. 本章目标

理解 TabRow 只提供标签栏，内容区和滑动需要额外组合。

学完后，你应当能够：

- 配置 selectedTabIndex
- 让 Tab 共享同一选中状态
- 用 when 切换简单内容
- 知道滑动页面通常使用 HorizontalPager

## 2. 项目中的位置

- `components/TabRowDemo.kt`



## 3. 项目源码片段

```kotlin
var selectedTab by remember { mutableIntStateOf(0) }

TabRow(selectedTabIndex = selectedTab) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            text = { Text(title) },
        )
    }
}

when (selectedTab) {
    0 -> Text("A 内容")
    1 -> Text("B 内容")
    else -> Text("C 内容")
}
```

## 4. 逐步解释

TabRow 负责标签排列、选中指示器和分割线，不包含页面内容，也不自动支持左右滑动。项目使用 when 根据 selectedTab 选择一个内容分支，适合简单 Demo。

需要滑动时接 HorizontalPager，并让 pagerState.currentPage 与 Tab 点击同步。最好选一个状态作为主要真相，避免 selectedTab 和 pagerState 双向更新形成循环。复杂页面还要考虑未选页面是否移出组合及状态保存。

## 5. 实际执行过程

1. TabRow 根据 selectedTabIndex 绘制
2. Tab 点击更新索引
3. 内容区选择对应分支
4. 若使用 Pager则执行滚动
5. Pager 页变化再同步标签

## 6. Flutter / Dart 3 对照

Flutter 的 TabBar + TabBarView 通常由 TabController 协调；Compose 的 TabRow 更轻，内容和 Pager 要自己组合。

```dart
DefaultTabController(
  length: 3,
  child: Column(children: const [
    TabBar(tabs: [Tab(text: 'A'), Tab(text: 'B'), Tab(text: 'C')]),
    Expanded(child: TabBarView(children: [Text('A'), Text('B'), Text('C')])),
  ]),
);
```

## 7. 常见错误

- 以为 TabRow 自带内容页
- Tab 和 selectedTabIndex 使用不同状态源
- Pager 与 Tab 双向同步互相触发
- 切换复杂内容后忽略状态丢失

## 8. 本章检查题

1. TabRow 与 HorizontalPager 各负责什么？
2. 项目为什么需要 when？
3. Flutter TabBarView 在 Compose 中对应什么？

## 9. 本章总结

TabRow 只是标签栏。简单内容用 when，手势切页用 HorizontalPager，并统一管理选中状态。
