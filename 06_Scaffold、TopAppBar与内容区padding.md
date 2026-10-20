# 第 6 章：Scaffold、TopAppBar 与内容区 padding

## 1. 本章目标

理解 Compose 如何用 `Scaffold` 组织一个标准页面，以及为什么内容区域必须使用它传出的 `padding`。

学完后，你应当能够：

- 知道 Scaffold 不是必须组件，而是 Material 页面骨架
- 看懂 topBar 参数为什么接收 Composable Lambda
- 理解内容 Lambda 的 PaddingValues 来源
- 避免列表被 TopAppBar 或系统栏遮挡

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`
- `ui/detail/ComponentDetailScreen.kt`
- `components/ScaffoldDemo.kt`



## 3. 项目源码片段

```kotlin
Scaffold(
    topBar = {
        TopAppBar(title = { Text("Compose 组件 Demo") })
    },
) { padding ->
    LazyColumn( // LazyColumn 是列表组件，第 16 章详解
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) { /* 条目 */ }
}
```

## 4. 逐步解释

`Scaffold` 自己不等于整个页面内容，它负责计算顶部栏、底部栏、悬浮按钮、Snackbar（第 29 章详解）等槽位之间的空间关系。`topBar` 是一个 UI 构建参数，调用方把 `TopAppBar` 交给它。最后的大括号是 `content` 参数，其中的 `padding` 是 `Scaffold` 根据顶部栏、底部栏和系统栏（状态栏、底部导航条）占用的空间算出的 `PaddingValues`（一个包含四个方向边距的值）。

项目先让列表（`LazyColumn`，第 16 章详解）填满内容槽，再执行 `.padding(padding)`。因此列表真正可用的区域会避开标题栏。若忽略这个 padding，第一条内容通常会绘制在 TopAppBar 后面。这里的 `padding` 不是随便命名的普通数值，而是包含 start、top、end、bottom 四个方向的信息。

需要提前纠正一个常见误解：`Scaffold` 只负责把这份 `PaddingValues` 算出来交给你，它**不会自己应用到内容上**，是否 `padding(padding)` 完全由你决定（相应官方描述是 "Scaffold does not apply the insets to content; this responsibility is yours."）。`Scaffold` 的 `contentWindowInsets` 默认也不等于 `safeDrawing`，而是来自 `ScaffoldDefaults.contentWindowInsets`（系统栏类 insets）；某个方向有对应的 `topBar` / `bottomBar` 时，该方向的 insets 由栏自己处理。这些细节在第 78 章展开。

另外，`TopAppBar` 在本项目使用的 Material3 版本中仍标注为实验 API，使用时需要给所在函数加 `@OptIn(ExperimentalMaterial3Api::class)`（本项目的 `ComponentDetailScreen.kt`、`ScaffoldDemo.kt` 都写了）。

### 4.1 innerPadding 与嵌套 Scaffold 的系统栏边距（WindowInsets）

> **首次阅读可先跳过：**本节是把 Scaffold 放进固定高度示例时才需要的进阶边界。先掌握上一节的核心规则即可：Scaffold 把内容区所需的 `padding` 交给你，内容根要应用它；`WindowInsets`、嵌套栏与重复消费会在第 78 章系统讲解。读完第 78 章后再回来核对本节。

`components/ScaffoldDemo.kt` 演示了一个容易踩坑的场景：把「迷你 Scaffold」嵌进一个固定高度的容器（高度用尺寸修饰符写成 `440.px`，尺寸修饰符详见第 13 章）里，用于在一屏内展示完整骨架。此时必须给槽位组件清零「系统栏边距」。

这里的「系统栏边距」在 Compose 里叫 `WindowInsets`：状态栏、底部导航条会占住屏幕的一部分，系统把「被占用了多少」这个信息告诉应用，这个信息就是 `WindowInsets`（第 78 章详解）。示例把这些值清零：

```kotlin
Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
        TopAppBar(
            windowInsets = WindowInsets(0, 0, 0, 0), // 左、上、右、下全为 0
            title = { Text("topBar 标题栏") },
        )
    },
    bottomBar = {
        // 底部栏放什么由你决定；这里的 NavigationBar（底部导航栏，第 31 章详解）只是示例
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) { /* 三个 NavigationBarItem */ }
    },
) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding) // 仍必须避开 topBar / bottomBar
            .padding(16.px),
    ) { /* 内容 */ }
}
```

准确的说法分三层：

1. `TopAppBar` 和示例里的 `NavigationBar` 默认会把系统栏（状态栏、导航条）占用的高度算进自己的边距，这在「页面最外层」的 Scaffold 上是对的。
2. 当 Scaffold 被嵌进固定高度容器、而外层详情页已经处理过一次系统栏时，这两条系统栏边距会重复叠加：顶栏被额外撑高、底栏下方多出一段空白。因此示例显式传入 `windowInsets = WindowInsets(0, 0, 0, 0)` 把它们清零。这里的 `WindowInsets(0, 0, 0, 0)` 是构造一个四边都为 0 的 `WindowInsets` 对象，表示「这里不再需要额外避让」。
3. 清零的是「槽位组件自己的系统栏边距」，不影响 Scaffold 传给内容区的 `innerPadding`——内容区依然必须执行 `.padding(innerPadding)`，否则会被 topBar 和 bottomBar 压住。另外要注意：`Scaffold` 并**不「自动」把 insets 应用到内容上**，它只是把算好的 `PaddingValues` 交给你，由内容根自己决定如何应用（这也是「写了 `Scaffold { padding -> }` 却没用 padding」会成为常见错误的原因）。换句话说：`innerPadding` 负责「避开同页的栏」，槽位的 `windowInsets` 负责「避免重复计算系统栏」，两者不是同一件事，不能互相替代。

   如果内容里还有会再次读 insets 的子布局（例如嵌套 Scaffold、带 `imePadding` 的输入区），在内容根再加一句 `.consumeWindowInsets(innerPadding)` 声明「这份 insets 已经用掉了」即可，避免重复叠加。完整原理与写法见第 78 章。

因此，`Scaffold` 在页面最外层时通常保持默认即可；只有当它被嵌套进另一个已处理系统栏的容器里时，才需要给 `TopAppBar` / `NavigationBar` 清零 `windowInsets`。

## 5. 实际执行过程

1. Compose 调用 Scaffold
2. Scaffold 先组合 topBar
3. Scaffold 计算内容应避开的边距
4. 内容 Lambda 收到 PaddingValues
5. LazyColumn 应用 padding 后开始布局

## 6. Flutter / Dart 3 对照

Flutter 的 `Scaffold` 同样提供 appBar、body、bottomNavigationBar 等页面槽位。不过 Flutter 的 body 通常由 Scaffold 自动布局到 AppBar 下方；Compose 的 Scaffold 把 `PaddingValues` 明确交给内容，开发者必须决定如何应用。

```dart
Scaffold(
  appBar: AppBar(title: const Text('Compose 组件 Demo')),
  body: ListView.builder(
    itemCount: items.length,
    itemBuilder: (context, index) => Text(items[index]),
  ),
);
```

## 7. 常见错误

- 把 Scaffold 当成所有页面都必须使用的父类；它只是可选的布局组件
- 写了 `Scaffold { padding -> }`，却没有把 padding 用到内容上
- 同时在多个层级重复应用同一份 padding，造成空白翻倍
- 误认为参数名必须叫 padding；名字可以改，但值的类型和含义不变
- 忘记给 `TopAppBar` 加 `@OptIn(ExperimentalMaterial3Api::class)`，编译报实验性 API 错误
- 把 Scaffold 嵌进固定高度容器时，忘了给 `TopAppBar` / `NavigationBar` 传 `windowInsets = WindowInsets(0, 0, 0, 0)`，导致顶栏被系统栏边距再次撑高、底栏下方多出空白
- 反过来，把「清零槽位 windowInsets」当成可以省掉内容区 `.padding(innerPadding)` 的理由
- 以为 `Scaffold` 会自动把 insets 应用到内容上（它只把 `PaddingValues` 交给你）
- 用了 `.padding(innerPadding)` 却忘了 `.consumeWindowInsets(innerPadding)`，内层再读 insets 时重复叠加（详见第 78 章）

## 8. 本章检查题

1. 删除 `.padding(padding)` 后，首页第一条内容可能发生什么？
2. topBar 的 `{ Text(...) }` 为什么不是立即返回一个 Text 对象？
3. PaddingValues 和单个 Dp 有什么区别？
4. 把 Scaffold 嵌进固定高度容器时，为什么要给 `TopAppBar` 传 `windowInsets = WindowInsets(0, 0, 0, 0)`？清零后还需要用 `innerPadding` 吗？

答案：

1. 第一条内容会绘制到 TopAppBar 后面被遮挡，因为列表没有避开顶部栏占用的区域。
2. 它是 UI 构建参数（Composable 槽位），由 Scaffold 在合适的时机构建并放入槽位，而不是立即返回对象。
3. PaddingValues 包含 start、top、end、bottom 四个方向的信息，单个 Dp 只有一个值。
4. 因为 `TopAppBar` 默认会把状态栏高度算进自己的边距，而外层页面已经处理过一次系统栏，嵌套后会重复叠加、把顶栏额外撑高。清零的是槽位组件自己的系统栏边距，与内容区无关，所以内容区仍然必须执行 `.padding(innerPadding)`。

## 9. 本章总结

`Scaffold` 负责页面槽位间的空间协调，并把 insets 算成 `PaddingValues` 交给内容；它**不会自己应用这些 insets**，所以内容 Lambda 接收的 `PaddingValues` 必须由具体内容消费（必要时再配 `consumeWindowInsets`，见第 78 章）。
