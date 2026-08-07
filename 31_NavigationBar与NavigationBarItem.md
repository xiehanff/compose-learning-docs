# 第 31 章：NavigationBar 与 NavigationBarItem

## 1. 本章目标

理解底部导航栏的选中状态和内容导航并不是自动绑定的。

学完后，你应当能够：

- 使用 NavigationBar 包裹条目
- 由 selectedIndex 统一控制选中项
- 配置 icon、label、onClick
- 区分视觉选中和 NavController 返回栈

## 2. 项目中的位置

- `components/NavigationBarDemo.kt`



## 3. 项目源码片段

```kotlin
var selectedIndex by remember { mutableIntStateOf(0) }

NavigationBar {
    items.forEachIndexed { index, item ->
        NavigationBarItem(
            selected = selectedIndex == index,
            onClick = { selectedIndex = index },
            icon = { Icon(item.icon, contentDescription = item.label) },
            label = { Text(item.label) },
        )
    }
}
```

## 4. 逐步解释

NavigationBar 负责底栏容器，NavigationBarItem 表示一个入口。selectedIndex 是唯一真相，每个条目根据它计算 selected，点击后更新索引。

项目示例只演示组件视觉，并不会自动切换独立页面。真实 App 可让内容区根据 selectedIndex 切换，也可将底栏与 NavController 当前目的地绑定。使用 NavController 时还要处理 launchSingleTop、saveState/restoreState 和避免重复压栈。

## 5. 实际执行过程

1. 组合底栏与条目
2. selectedIndex 决定选中样式
3. 点击更新索引或导航
4. 底栏重组
5. 内容区按同一状态显示页面

## 6. Flutter / Dart 3 对照

Flutter 的 NavigationBar/BottomNavigationBar 同样接收 selectedIndex/currentIndex，并由回调更新；内容常配合 IndexedStack 或 Navigator。

```dart
NavigationBar(
  selectedIndex: index,
  onDestinationSelected: (value) => setState(() => index = value),
  destinations: const [
    NavigationDestination(icon: Icon(Icons.home), label: '首页'),
    NavigationDestination(icon: Icon(Icons.search), label: '搜索'),
  ],
);
```

## 7. 常见错误

- 只改变底栏颜色，内容区不变
- 每个 item 自己维护 selected
- 每次点击都压入重复路由
- 图标和标签缺乏无障碍语义

## 8. 本章检查题

1. NavigationBar 会自动切换页面吗？
2. selected 状态应放在哪里？
3. 使用 NavController 时为什么要避免重复压栈？

答案：

1. 不会。NavigationBar 只负责底栏容器与条目显示，页面切换由内容区逻辑或导航决定。
2. selectedIndex 作为唯一真相放在父级状态中，每个条目根据它计算 selected。
3. 重复压栈会让返回栈堆积相同页面，返回行为错乱；应使用 launchSingleTop、saveState/restoreState 等控制。

## 9. 本章总结

底栏是导航 UI，不是完整导航栈。必须把选中状态与内容或 NavController 明确连接。
