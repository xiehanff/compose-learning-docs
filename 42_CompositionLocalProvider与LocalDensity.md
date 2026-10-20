# 第 42 章：CompositionLocalProvider 与 LocalDensity

## 1. 本章目标

先看一个真实问题：第 3 章里 `Theme.kt` 用一行 `CompositionLocalProvider(LocalDemoTheme provides demoTheme)` 把尺寸配置挂上树，`HomeScreen` 里一句 `LocalDemoTheme.current` 就拿到了它——中间隔着导航、页面好几层组件，谁都没有转发这个参数。这是怎么做到的？反过来，把所有跨层数据都这么传可以吗？

理解环境值如何沿组合树向下提供，以及覆盖范围为何只限子树；知道用 LocalDensity 做设计稿适配时存在全局影响。

学完后，你应当能够：

- 写出最小的 provider / consumer 闭环：`CompositionLocalProvider(LocalX provides value)` 提供值，`LocalX.current` 在子树任意深处读取
- 说清作用域（provider 到子树结束）与默认值（没有 Provider 时拿到声明时给的兜底值）
- 分清提供端的 `provides` 与读取端的 `.current`（例如项目的 `LocalDemoTheme.current`，第 3 章）
- 理解最近 Provider 原则
- 区分 `compositionLocalOf` 与 `staticCompositionLocalOf` 的机制差别
- 区分环境依赖与业务参数
- 把 CompositionLocal 与 Flutter 的 InheritedWidget 区分为"概念相似、机制不等价"

## 2. 项目中的位置

- `ui/adapter/DesignWidthScale.kt`
- `ui/theme/Theme.kt`

原工程未随本仓库分发，路径仅用于说明来源（见 README）。

## 3. 项目源码片段

项目节选（教学推演，不可直接复制：依赖 `designDensity`、`demoTheme`、`content()` 等原工程符号）：

```kotlin
// 提供（Theme.kt，第 3 章）：把 demoTheme 挂到子树上
CompositionLocalProvider(
    LocalDensity provides designDensity,
) {
    content()
}

CompositionLocalProvider(
    LocalDemoTheme provides demoTheme,
) {
    MaterialTheme(content = content)
}

// 读取（HomeScreen.kt）：拿到最近一层提供的值，只在本子树内有效
val demoTheme = LocalDemoTheme.current
fontSize = demoTheme.listItemTextPx.pxSp()
```

### 3.1 独立示例：最小 provider / consumer 闭环

参与编译校验，不依赖原工程。声明、提供、读取三步齐全，可复制运行。

<!-- compile -->
```kotlin
// 1) 声明：staticCompositionLocalOf 带一个默认值工厂（两种声明方式的差别见 4.3）
val LocalCardBg = staticCompositionLocalOf { Color(0xFFBBDEFB) }

Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    // 2) 提供：在 Provider 的子树里挂新值
    CompositionLocalProvider(LocalCardBg provides Color(0xFFEF9A9A)) {
        // 3) 消费：子树任意深处读 .current，拿到最近一层 provides 的值（红色）
        Box(Modifier.size(120.dp).background(LocalCardBg.current))
    }
    // Provider 作用域之外再读：拿到声明时的默认值（浅蓝）
    Box(Modifier.size(120.dp).background(LocalCardBg.current))
}
```

首次出现的 Kotlin 语法：`provides` 是中缀（infix）函数。`LocalCardBg provides color` 等价于 `LocalCardBg.provides(color)`，中缀写法让「谁提供什么」读起来更像一句话；它返回一个 `ProvidedValue`，`CompositionLocalProvider` 收的就是这种值，几个就传几个。

预期观察：上面一个红色方块（Provider 内读到新值），下面一个浅蓝方块（Provider 外回落到默认值）。

## 4. 逐步解释

### 4.1 提供与读取：一个最小闭环

CompositionLocal 是沿组合树向下传播的环境值。提供端用 `provides` 挂值，读取端用 `.current` 取最近一层的值，两边的名字必须对应同一个 CompositionLocal 对象——对象本身就是"频道"，声明一次、提供和读取共用。3.1 就是完整闭环：声明 `LocalCardBg`、在 Provider 里提供红色、子树里读 `.current`。

### 4.2 作用域、最近 Provider 与默认值

子节点读取 LocalDensity.current 时，会找到离自己最近的 Provider；父节点和兄弟子树不受内部覆盖影响。离开 Provider 范围后恢复外层值。两个要点补充：

- 默认值：从声明处带到整个树。任何位置都没 provide 过时，`.current` 返回默认值工厂的结果（3.1 第二个方块）。
- 嵌套时"最近者胜"：同一频道嵌套两层 Provider，内层读到内层的值，内层结束后恢复外层的值——第 43 章 DesignWidthScale 正是把自己的 Density 挂成离全部后代最近的那层。

项目同样用 LocalDemoTheme 提供 Demo 尺寸配置（第 3 章）：子组件写 `val demoTheme = LocalDemoTheme.current`，拿到的是最近一层 `LocalDemoTheme provides ...` 提供的 `DemoThemeConfig`。此机制适合主题、密度、语言等跨大量层级的环境信息，但会形成隐式依赖，不应取代所有明确业务参数。它也不是修改设备或线程的全局 density。特别是覆盖 `LocalDensity` 会影响整个子树的普通 Dp、Material 默认尺寸和第三方组件，因此设计稿适配必须限定范围并明确无障碍取舍。

### 4.3 compositionLocalOf 与 staticCompositionLocalOf 的差别

两种声明方式的机制差别在「值变化时谁重组」（作为机制差异简述，细节以官方 CompositionLocal 文档为准）：

| 声明方式 | 值变化时 | 适用 |
| --- | --- | --- |
| `compositionLocalOf { ... }` | 追踪每个读取位置，只让读取处的作用域重组 | 值可能频繁变化 |
| `staticCompositionLocalOf { ... }` | 不追踪读取位置，整个 content 子树全部重组 | 值几乎不变时反而更省 |

LocalDensity 这类"跟随配置、很少变"的环境值，两种声明都能工作；项目自定义的 LocalDemoTheme 配置也是基本不变的类型。选型依据是变化频率与重组代价，不是功能差异——读取端写法完全一样。

### 4.4 LocalDensity：官方内置的环境值

LocalDensity 是这套机制的官方内置例子：`LocalDensity.current` 返回当前子树的 `Density`（第 41 章），官方内置的还有 LocalContext、LocalContentColor 等。它们不是特权 API——项目自定义 LocalDemoTheme 用的正是同一套 provider / consumer 机制。第 43 章的 DesignWidthScale 覆盖 LocalDensity，就是"用官方频道挂自定义值"的实例。

## 5. 实际执行过程

1. 外层存在默认值
2. Provider 在当前子树绑定新值
3. 后代读取 nearest value
4. 值变化使读取者重组（static 声明则整子树重组，见 4.3）
5. 范围外仍使用原值

## 6. Flutter / Dart 3 对照

Flutter 的 Theme、MediaQuery、Directionality 等 InheritedWidget 也沿树向下查找最近值——概念相似，机制不等价。InheritedWidget 靠 Element 树上的依赖登记工作：`dependOnInheritedWidgetOfExactType` 把当前 Element 登记进依赖表，值变化时框架回调它的 `didChangeDependencies`；CompositionLocal 则是 Compose 运行时的作用域读取：`.current` 读取被记为快照依赖，值变化时由重组系统使读取处的重组作用域失效（第 22 章）。一个长在不可变 Widget 树加依赖登记上，一个长在 Compose 重组运行时上，跨框架只做概念对照，不能当等价机制讲。

```dart
MediaQuery(
  data: MediaQuery.of(context).copyWith(
    textScaler: const TextScaler.linear(1.0),
  ),
  child: const DemoBody(),
);
```

## 7. 常见错误

- 以为 Provider 永久修改整个 App
- 在 Provider 外读取却期待内部值
- 把所有业务数据塞 CompositionLocal
- 多层适配 Provider 导致规则混乱
- 提供端和读取端用了两个名字相近但不同的 CompositionLocal 对象

错误示例（故意错误，不参与编译）：

```kotlin
// 编译错误：CompositionLocalProvider 需要 provides 表达式（ProvidedValue 类型），
// 直接传 .current 读出来的值，类型对不上任何重载
CompositionLocalProvider(LocalDensity.current) {
}
```

## 8. 本章检查题

1. LocalDensity.current 如何选择值？
2. Provider 会影响父节点吗？
3. 哪些信息适合 CompositionLocal？
4. `LocalDemoTheme.current` 能读到什么？它和 `provides` 是什么关系？
5. 预测：外层 `CompositionLocalProvider(LocalX provides A)` 内嵌一层 `LocalX provides B`，内层子树读到什么？内层结束之后再读呢？
6. compositionLocalOf 与 staticCompositionLocalOf 在"值变化"时的行为差别是什么？
7. 动手：把 3.1 里 `provides` 那一行删掉，运行后两个方块各是什么颜色？

答案：

1. 读取时沿组合树向上找最近的 Provider，找到即用它提供的值。
2. 不会。Provider 只影响它内部的子树，父节点和兄弟子树不受影响，离开范围后恢复外层值。
3. 主题、密度、语言等需要跨大量层级传递的环境信息；不适合替代所有明确的业务参数。
4. 读到最近一层 `LocalDemoTheme provides ...` 提供的 `DemoThemeConfig`（第 3 章里 `DemoTheme` 提供、`HomeScreen` 读取）；`.current` 是读取端，`provides` 是提供端，两者靠同一个 `CompositionLocal` 对象对应，缺了 Provider 就只能拿到默认值。
5. 内层读到 B（最近者胜）；内层结束后恢复读 A。
6. compositionLocalOf 只让读取位置所在的作用域重组；staticCompositionLocalOf 不追踪读取位置，值一变整个 content 子树重组。功能上读取端写法相同，差别是重组范围与适用场景。
7. 都是默认值浅蓝。没有 Provider 时，Provider 内外的读取都回落到声明时默认值工厂给的 `Color(0xFFBBDEFB)`。

## 9. 本章总结

CompositionLocalProvider 只覆盖组合子树，读取遵循最近 Provider 原则；没有 Provider 就拿默认值。compositionLocalOf 与 staticCompositionLocalOf 的差别是重组范围，不是功能。LocalDensity 是官方内置的环境值，覆盖它的代价在第 43、45 章展开；与 Flutter 的 InheritedWidget 概念相似、机制不等价。

官方参考：

- [CompositionLocal 官方指南（provides / current / 两种声明的取舍）](https://developer.android.com/develop/ui/compose/compositionlocal)
- [androidx.compose.ui.platform 包参考（LocalDensity 所在包）](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/package-summary)
