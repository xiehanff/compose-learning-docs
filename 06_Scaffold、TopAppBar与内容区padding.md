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



## 3. 项目源码片段

```kotlin
Scaffold(
    topBar = {
        TopAppBar(title = { Text("Compose 组件 Demo") })
    },
) { padding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
    ) { /* 条目 */ }
}
```

## 4. 逐步解释

`Scaffold` 自己不等于整个页面内容，它负责计算顶部栏、底部栏、悬浮按钮、Snackbar 等槽位之间的空间关系。`topBar` 是一个 UI 构建参数，调用方把 `TopAppBar` 交给它。最后的大括号是 `content` 参数，其中的 `padding` 是 `Scaffold` 根据顶部栏和系统 Insets 算出的 `PaddingValues`。

项目先让 `LazyColumn` 填满内容槽，再执行 `.padding(padding)`。因此列表真正可用的区域会避开标题栏。若忽略这个 padding，第一条内容通常会绘制在 TopAppBar 后面。这里的 `padding` 不是随便命名的普通数值，而是包含 start、top、end、bottom 四个方向的信息。

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

## 8. 本章检查题

1. 删除 `.padding(padding)` 后，首页第一条内容可能发生什么？
2. topBar 的 `{ Text(...) }` 为什么不是立即返回一个 Text 对象？
3. PaddingValues 和单个 Dp 有什么区别？

答案：

1. 第一条内容会绘制到 TopAppBar 后面被遮挡，因为列表没有避开顶部栏占用的区域。
2. 它是 UI 构建参数（Composable 槽位），由 Scaffold 在合适的时机构建并放入槽位，而不是立即返回对象。
3. PaddingValues 包含 start、top、end、bottom 四个方向的信息，单个 Dp 只有一个值。

## 9. 本章总结

`Scaffold` 负责页面槽位和安全空间的协调；`TopAppBar` 是顶部栏内容；内容 Lambda 接收的 `PaddingValues` 必须由具体内容消费。
