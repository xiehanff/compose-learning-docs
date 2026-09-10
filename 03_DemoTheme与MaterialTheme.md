# 第 3 章：MaterialTheme 与 DemoTheme

## 1. 本章目标

只回答一个问题：MaterialTheme 是什么，它把哪些值向下提供，子组件怎么读取，包裹范围有多大。

学完后，你应当能够：

- 说出 MaterialTheme 提供的三套值：`colorScheme`、`typography`、`shapes`
- 用 `MaterialTheme.colorScheme.xxx`、`MaterialTheme.typography.xxx` 读取主题值
- 解释 `darkTheme` 参数如何决定亮色 / 暗色方案
- 解释为什么 `DemoApp()` 放进主题后，里面所有页面都能读到统一样式
- 解释为什么内部还能再嵌一层 `MaterialTheme` 做局部覆盖
- 用 `LocalDemoTheme.current` 读取项目自己的 Demo 尺寸配置
- 把 `DemoTheme` 与 Material 3 官方 API 区分开

## 2. 项目中的位置

- `MainActivity.kt`：`setContent` 里的主题入口
- `ui/theme/Theme.kt`：`DemoTheme` 定义
- `ui/detail/ComponentDetailScreen.kt`：详情页的 `DemoContent` 局部覆盖字体

## 3. 项目源码片段

入口：

```kotlin
setContent {
    DesignWidthScale {
        DemoTheme {
            DemoApp()
        }
    }
}
```

`Theme.kt` 核心：

```kotlin
@Composable
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    demoTheme: DemoThemeConfig = DemoThemeConfig(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // 动态取色分支（见第 77 章）：从壁纸取色，亮/暗各一套。
            // 这里不能省略：when 的每个分支都必须给出 ColorScheme，否则返回 Unit，
            // 下面的 MaterialTheme(colorScheme = ...) 无法编译。
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalDemoTheme provides demoTheme) { // 该机制见第 42 章
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DemoTypography(),
            content = content,
        )
    }
}
```

子组件读取：

```kotlin
Text(
    text = "选择下面的组件入口",
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

```kotlin
MaterialTheme.typography.headlineSmall
MaterialTheme.colorScheme.primary
```

## 4. 逐步解释

### 4.1 MaterialTheme 是官方主题容器

`MaterialTheme` 来自 `androidx.compose.material3`，是 Material 3 提供的官方主题容器。它接收 `colorScheme`、`typography`、`shapes` 等参数，把这些值提供给自己的 `content` 子树。

`DemoTheme` 不是官方 API，它是本项目封装的包装函数：先算好 `colorScheme`，再调用 `MaterialTheme`，另外还做了两件项目自己的事——提供 Demo 尺寸配置（第 42 章）和同步系统栏图标（第 25 章）。本章只讨论它跟 `MaterialTheme` 相关的那部分。

### 4.2 三套值

`MaterialTheme` 向下提供三套值：

| 参数 | 内容 | 读取示例 |
|---|---|---|
| `colorScheme` | 语义化颜色角色（primary、onPrimary、surface、onSurfaceVariant…） | `MaterialTheme.colorScheme.primary` |
| `typography` | 文字样式角色（displayLarge…labelSmall） | `MaterialTheme.typography.headlineSmall` |
| `shapes` | 圆角角色（extraSmall…extraLarge） | `MaterialTheme.shapes.medium` |

业务代码只写“用哪个角色”，不写死具体色值或字号，所以改主题一处就能全应用联动。

`DemoTheme` 显式传了 `colorScheme` 和 `typography`，没有传 `shapes`，`shapes` 用的是 `MaterialTheme` 的默认值。

### 4.3 读取遵循“最近一层”

`MaterialTheme.colorScheme.primary` 不是全局单例，它读取的是当前组件向上最近的 `MaterialTheme` 提供的值。同理，`HomeScreen` 里的 `Text` 能读到颜色，是因为它位于入口 `MaterialTheme` 的 `content` 子树内部。

### 4.4 darkTheme 决定亮色还是暗色

```kotlin
darkTheme: Boolean = isSystemInDarkTheme()
```

- 类型：`Boolean`，默认跟随系统当前是否使用深色主题
- 不传时用默认值：`DemoTheme { DemoApp() }`
- 也可强制：`DemoTheme(darkTheme = true) { DemoApp() }`

`when` 表达式从上到下匹配，命中第一项就取对应结果：`darkTheme` 为真用 `DarkColorScheme`，否则用 `LightColorScheme`。`DemoTheme` 前面还有一个 `dynamicColor` 分支走 Android 12+ 壁纸取色，属于主题进阶内容，见第 77 章。

### 4.5 包裹范围

```text
DemoTheme
└── MaterialTheme（提供三套值）
    └── DemoApp
        ├── HomeScreen → Text / Card / TopAppBar
        └── ComponentDetailScreen
```

凡是位于 `MaterialTheme` 的 `content` 子树内的组件，都能读到这三套值；放到 `DemoTheme` 外部的组件读不到，只能读到更外层（或默认）的主题环境。

### 4.6 内部可以再嵌一层做局部覆盖

详情页的 `DemoContent` 又套了一层 `MaterialTheme`：

```kotlin
val typography = MaterialTheme.typography
val demoTypography = typography.copy(
    bodyMedium = typography.bodyMedium.copy(/* 改字号、行高 */),
)

MaterialTheme(
    colorScheme = MaterialTheme.colorScheme, // 沿用外层
    shapes = MaterialTheme.shapes,           // 沿用外层
    typography = demoTypography,             // 只替换字体
    content = content,
)
```

颜色和形状显式沿用外层，只替换示例区域的部分字体。它只影响自己包裹的子树，不影响页面标题和整个应用——规则仍然是“读最近一层”。

### 4.7 项目自定义的尺寸配置

`DemoThemeConfig` / `LocalDemoTheme` 是项目自己定义的尺寸配置（列表项、详情示例的字号与行高，单位是设计稿 px），不是 Material 3 的 API。它是通过 `CompositionLocalProvider` 提供的，机制见第 42 章；这些设计稿 px 如何换算成实际像素，见第 43、44 章。

提供方是 `DemoTheme`（上面 `Theme.kt` 里的 `CompositionLocalProvider(LocalDemoTheme provides demoTheme)`），读取方是子组件，用 `LocalDemoTheme.current` 拿到离自己最近的那份配置：

```kotlin
// HomeScreen.kt
val demoTheme = LocalDemoTheme.current // 读取上方 DemoTheme 提供的尺寸配置

Text(
    text = component.title,
    fontSize = demoTheme.listItemTextPx.pxSp(),     // 默认 32f（设计稿 px）
    lineHeight = demoTheme.listItemLineHeightPx.pxSp(), // 默认 40f
)
```

`DemoThemeConfig` 的字段都是「设计稿 px 数值 + 默认值」，例如 `listItemTextPx = 32f`、`demoTextPx = 28f`、`demoComponentPaddingPx = 24f`。字号、行高用 `.pxSp()` 转成 `TextUnit`，内边距、间距用 `.px` 转成 `Dp`（第 44 章）。改默认值或在 `DemoTheme(demoTheme = DemoThemeConfig(...))` 里传新值，页面上所有读了这些字段的地方会一起变——这仍然是「读最近一层」的规则，只不过这一层是项目自己的 `LocalDemoTheme`。

## 5. 实际执行过程

1. `setContent` 进入根组合，`DemoTheme` 先计算 `colorScheme`（自动跟随系统，或由 `darkTheme` / 动态取色决定）
2. `MaterialTheme` 把 `colorScheme`、`typography`、`shapes` 提供给 `content` 子树
3. `DemoApp` → `HomeScreen` 等后代读取最近的 `MaterialTheme` 值并渲染
4. 详情页 `DemoContent` 再嵌一层 `MaterialTheme`，只覆盖 `typography`，它的后代读到局部字体
5. 主题值变化时，读取了这些值的组件会重新执行；同步到组合树外部的动作（系统栏图标）不在本章范围，见第 25 章

## 6. Flutter / Dart 3 对照

Flutter 把主题放在 `MaterialApp` 上：

```dart
MaterialApp(
  theme: ThemeData(colorScheme: colorScheme),
  home: const DemoApp(),
)
```

子组件读取：

```dart
final colors = Theme.of(context).colorScheme;
```

对应关系：

| Compose | Flutter |
|---|---|
| `MaterialTheme`（官方） | `MaterialApp` 的 `theme` / `darkTheme`（`ThemeData`） |
| `MaterialTheme.colorScheme` | `Theme.of(context).colorScheme` |
| `MaterialTheme.typography` | `Theme.of(context).textTheme` |
| `MaterialTheme.shapes` | 没有全局 Shape 体系，圆角分散在各组件主题里 |
| 再嵌一层 `MaterialTheme` 做局部覆盖 | 用 `Theme` widget 包裹子树 |
| `DemoTheme` | 项目自己封装的 `AppTheme` 包装 |

区别是 Compose 读取时不显式传 `BuildContext`。

## 7. 常见错误

- 把固定色值写满所有页面：深色模式和主题统一都会失效，应读 `colorScheme` 的角色。
- 把 `DemoTheme` 当成 Material 3 官方 API，或以为 `MaterialTheme` 有 `demoTheme` 参数。
- 以为内部不能再嵌 `MaterialTheme`：可以，它只覆盖被包裹的子树。
- 以为 `MaterialTheme` 会顺带改掉系统状态栏：它只管 Compose UI，外部对象要单独同步（第 25 章）。

## 8. 本章检查题

1. `DemoTheme` 与 `MaterialTheme` 是什么关系？
2. `MaterialTheme` 向下提供哪三套值？各举一个读取写法。
3. `MaterialTheme.colorScheme.primary` 的值来自哪里？
4. `darkTheme` 不传时，颜色方案怎么选？
5. 为什么 `DemoApp` 下面的所有页面都能读到同一套主题？
6. 内部再嵌一层 `MaterialTheme` 会影响整个应用吗？`DemoContent` 为什么这么做？
7. 与 Flutter 的 `ThemeData` / `Theme.of(context)` 怎么对应？
8. `DemoThemeConfig` / `LocalDemoTheme` 是官方 API 吗？子组件怎么读到它？

答案：

1. `MaterialTheme` 是 Material 3 官方主题容器；`DemoTheme` 是项目对它的封装，先算好颜色方案再调用 `MaterialTheme`，不是官方 API。
2. `colorScheme`、`typography`、`shapes`。例如 `MaterialTheme.colorScheme.onSurfaceVariant`、`MaterialTheme.typography.headlineSmall`、`MaterialTheme.shapes.medium`。
3. 来自距离当前组件最近的那层 `MaterialTheme` 所提供的 `colorScheme`。
4. 默认值跟随系统是否处于深色模式，再按 `when` 从上到下命中：暗色用 `DarkColorScheme`，否则 `LightColorScheme`。
5. 因为它们在入口 `MaterialTheme` 的 `content` 子树内部，读取时向上找到的就是那一层。
6. 不会，只影响它包裹的子树。`DemoContent` 想让示例区域用更小一档的字体，同时显式沿用外层的颜色和形状，所以只替换了 `typography`。
7. `MaterialTheme` 对应 `MaterialApp` 的 `theme` / `darkTheme`（`ThemeData`），`MaterialTheme.colorScheme` 对应 `Theme.of(context).colorScheme`，读取时 Compose 不需要显式传 `context`。
8. 不是，是项目自己定义的尺寸配置（`CompositionLocal`，机制见第 42 章）。`DemoTheme` 用 `CompositionLocalProvider(LocalDemoTheme provides demoTheme)` 把它挂到子树上，子组件用 `val demoTheme = LocalDemoTheme.current` 读取最近一层提供的 `DemoThemeConfig`，字段值再用 `.px` / `.pxSp()` 换算成 `Dp` / `TextUnit`（第 44 章）。

## 9. 本章总结

主题的本质是“组件读角色、主题给值”：

```text
DemoTheme（项目封装）
├── 选择 colorScheme（light / dark，动态取色见第 77 章）
└── MaterialTheme（官方容器）
    ├── colorScheme：颜色角色
    ├── typography：文字样式角色
    └── shapes：圆角角色
        └── content：DemoApp 及其所有页面
```

一句话：`MaterialTheme` 把颜色、字体、形状三套值提供给自己的子树，子组件读最近一层；`DemoTheme` 是本项目在这之上的包装，不是官方 API。子树内部可以再嵌一层 `MaterialTheme` 覆盖局部，不影响外部。
