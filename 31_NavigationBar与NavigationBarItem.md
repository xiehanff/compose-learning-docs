# 第 31 章：NavigationBar 与 NavigationBarItem

## 1. 本章目标

先看一个真实问题：App 底部要有「首页 / 搜索 / 设置」三个入口，点谁内容区就切换到谁，底栏高亮也跟着走。刚接触 Compose 的读者常以为 NavigationBar 自带页面切换——毕竟在命令式 UI 里「点 tab 换页」常常是控件包办的事。实际上它只画底栏，选中态和内容切换都要你自己用状态接起来。本章完成一个两个以上目的地的完整闭环，并分清「本地状态切换」与「导航路由切换」两种模式。

理解底部导航栏的选中状态和内容导航并不是自动绑定的。

学完后，你应当能够：

- 使用 NavigationBar 包裹条目
- 由 selectedIndex 统一控制选中项
- 配置 icon、label、onClick
- 区分视觉选中和 NavController 返回栈
- 预测系统返回键在「本地状态切换」与「navigate 路由切换」两种模式下的不同行为

## 2. 项目中的位置

- `components/NavigationBarDemo.kt`

## 3. 项目源码片段

### 3.1 项目里的底栏写法

<!-- compile
prelude: class NavItem(val icon: ImageVector, val label: String)
prelude: val items = listOf(NavItem(Icons.Filled.Home, "首页"), NavItem(Icons.Filled.Search, "搜索"))
-->
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

项目示例只画了底栏本身。要形成一个能点的完整页面，还需要内容区读同一个状态。

### 3.2 完整可运行示例：三个目的地的状态与事件闭环

<!-- compile decls -->
```kotlin
// 目的地描述：图标 + 文字。data class 语法见 00D
data class Dest(val icon: ImageVector, val label: String)

@Composable
fun NavigationBarSwitchDemo() {
    val items = listOf(
        Dest(Icons.Filled.Home, "首页"),
        Dest(Icons.Filled.Search, "搜索"),
        Dest(Icons.Filled.Settings, "设置"),
    )
    var selectedIndex by remember { mutableIntStateOf(0) }  // 唯一真相：当前在第几个目的地

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,   // 选中态由父状态计算
                        onClick = { selectedIndex = index }, // 点击只更新父状态
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // 内容区读同一个 selectedIndex：底栏与内容由同一状态驱动
            val content = when (selectedIndex) {
                0 -> "首页：这里是第 0 个目的地"
                1 -> "搜索：这里是第 1 个目的地"
                else -> "设置：这里是第 2 个目的地"
            }
            Text(content, Modifier.align(Alignment.Center))
        }
    }
}
```

初始界面：底栏三项，「首页」高亮，屏幕中间显示首页文案。

操作步骤：依次点「搜索」「设置」，再点回「首页」。

期望观察：每点一项，底栏高亮立即移动，中间文案同步换成对应目的地；高亮与内容永远一致，因为它们读的是同一个 `selectedIndex`。

## 4. 逐步解释

### 4.1 选择态的来源：selected 由父状态决定

NavigationBar 负责底栏容器，NavigationBarItem 表示一个入口。selectedIndex 是唯一真相，每个条目根据它计算 selected，点击后更新索引。

NavigationBarItem 的 `selected` 参数是外部传入的值，组件内部不保存「我是不是被选中」——这就是第 24 章的受控组件模式（value + callback）。如果把选中态拆给每个条目自己记，就会出现 7. 节反例里「两个同时亮」且父级无法回答「当前在哪页」的局面。父级持有索引还有一层含义：内容区、顶栏标题、埋点统计等一切「随当前目的地变化」的东西，都从这一个状态推导。

### 4.2 闭环的执行链：事件 → 状态 → 重组 → 界面

以 3.2 点「搜索」为例：点击 NavigationBarItem（事件）→ onClick 把 selectedIndex 置 1（状态）→ 读过该状态的底栏与内容区安排重组（重组）→ 高亮胶囊移到「搜索」、文案换成搜索页（界面）。组件间没有直接通信，全部通过状态间接汇合。

### 4.3 两种切换模式：本地状态 vs navigate 路由

项目示例只演示组件视觉，并不会自动切换独立页面。真实 App 可让内容区根据 selectedIndex 切换，也可将底栏与 NavController 当前目的地绑定。使用 NavController 时还要处理 `launchSingleTop`（避免同一目的地重复压栈）、`saveState` / `restoreState`（切换时保留并恢复子栈状态）；完整返回栈配置见第 34 章。

两种模式的行为差异：

- **模式 A：本地状态切换（本章 3.2）**。selectedIndex 是普通组合内状态，when 直接换内容区。返回栈全程只有一个目的地；系统返回键不会回到上一个标签，而是按 Activity 的默认返回行为（通常是离开当前页面）。切换即重组，未选中的分支默认移出组合、其内部状态不保留——保活与恢复是第 33、34 章的主题。
- **模式 B：navigate 路由切换**。点击改为 `navController.navigate(route)`（第 4 章），每个目的地是返回栈里的独立条目，back 键逐层弹回上一个目的地；深链、每个目的地独立的返回栈也依赖这套机制。代价是配置更多：底栏切换通常要 `launchSingleTop = true` 加 `popUpTo(startDestination) { saveState = true }` 加 `restoreState = true` 三件套，否则会出现重复压栈或切换丢状态。

选型参考：单屏内的轻量分区、Demo、内容分支很少——模式 A 足够；App 主功能之间切换、需要返回语义和深链——模式 B。两种模式底栏代码几乎一样，差别只在 onClick 里更新状态还是发起导航。

## 5. 实际执行过程

1. 组合底栏与条目
2. selectedIndex 决定选中样式
3. 点击更新索引或导航
4. 底栏重组
5. 内容区按同一状态显示页面
6. 模式 B 下返回栈同步更新，系统返回键逐层弹出目的地

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

相似点：Flutter 的 NavigationBar 同样是受控组件——外部传 selectedIndex、回调上报选择，底栏自己不切换页面。

不等价点有二。其一，内容保活默认相反：Flutter 惯用 IndexedStack 把所有子页都留在树里（对应第 33 章的 Box 保活方案），Compose 的 when 切换默认把未选分支移出组合、状态丢弃。其二，事件形状不同：Flutter 用一个 `onDestinationSelected(int)` 回调统一收索引，Compose 给每个 NavigationBarItem 单独的 onClick。

## 7. 常见错误

- 只改变底栏颜色，内容区不变
- 每个 item 自己维护 selected
- 每次点击都压入重复路由
- 图标和标签缺乏无障碍语义（无障碍，第 80 章详解）

```kotlin
// 错误示例（故意错误，不参与编译）：每个条目各记一个选中布尔——
// 互斥要靠手写，两个同时亮也不报错，父级永远无法回答「当前在第几页」
var homeSelected by remember { mutableStateOf(true) }
var searchSelected by remember { mutableStateOf(false) }

NavigationBar {
    NavigationBarItem(
        selected = homeSelected,
        onClick = { homeSelected = !homeSelected },  // 只翻自己的布尔，不知道别人存在
        // icon、label 略
    )
    NavigationBarItem(
        selected = searchSelected,
        onClick = { searchSelected = !searchSelected },
        // icon、label 略
    )
}
```

- 在模式 B 里漏配 `launchSingleTop` / `popUpTo(saveState)` / `restoreState`：重复点击同一入口反复压栈，或切回时子页面状态丢失
- 拿 selectedIndex 之外的第二份状态（如顶栏标题变量）记录「当前在哪页」，两份状态各自漂移

## 8. 本章检查题

1. NavigationBar 会自动切换页面吗？
2. selected 状态应放在哪里？
3. 使用 NavController 时为什么要避免重复压栈？
4. 预测行为：在 3.2（模式 A）里点过「搜索」后按系统返回键，会发生什么？换成模式 B 又如何？
5. 动手修改：把 3.2 的 selectedIndex 从 remember 改成 rememberSaveable（第 23 章），旋转屏幕后观察选中项；再想一想为什么内容区里一个 TextField 的输入不会因此被保留。

答案：

1. 不会。NavigationBar 只负责底栏容器与条目显示，页面切换由内容区逻辑或导航决定。
2. selectedIndex 作为唯一真相放在父级状态中，每个条目根据它计算 selected。
3. 重复压栈会让返回栈堆积相同页面，返回行为错乱；应使用 launchSingleTop、saveState/restoreState 等控制。
4. 模式 A：不会回到「首页」——返回栈从头到尾只有一个目的地，系统返回按 Activity 默认行为离开页面。模式 B：返回键弹出栈顶目的地，回到上一个目的地（配置三件套后行为见第 34 章）。
5. rememberSaveable 保存并恢复了索引这个值本身，旋转重建后选中项还在；但 when 切换时未选分支已离开组合，内容区控件自身的输入状态不在保存范围，需要第 33/34 章的保活或保存方案。

## 9. 本章总结

底栏是导航 UI，不是完整导航栈。必须把选中状态与内容或 NavController 明确连接：本地状态切换适合轻量分区，navigate 切换才拥有返回栈语义；两种模式下 selected 都来自父级唯一真相，系统返回键的行为差异是选型时最容易忽略的一点。

官方参考：

- [NavigationBar（API 参考）](https://developer.android.com/reference/kotlin/androidx/compose/material3/NavigationBar)
- [Material 3 规范：Navigation bar（底部导航定位）](https://m3.material.io/components/navigation-bar/overview)
- [Navigation（官方导航指南，模式 B 的返回栈基础）](https://developer.android.com/guide/navigation)
