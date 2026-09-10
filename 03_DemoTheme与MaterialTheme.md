# 第 3 章：DemoTheme、MaterialTheme 与 CompositionLocalProvider

## 1. 本章对应的项目代码

入口处：

```kotlin
setContent {
    DesignWidthScale {
        DemoTheme {
            DemoApp()
        }
    }
}
```

主题定义位于：

```text
app/src/main/java/com/example/demo/ui/theme/Theme.kt
```

核心函数：

```kotlin
@Composable
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    demoTheme: DemoThemeConfig = DemoThemeConfig(),
    content: @Composable () -> Unit,
) {
    // 选择颜色
    // 配置系统栏图标
    // 向子树提供自定义配置
    // 使用 MaterialTheme 包裹 content
}
```

本章回答：

> 为什么 `DemoApp()` 放进 `DemoTheme {}` 后，里面所有页面都能使用统一颜色和字体？

---

## 2. Theme 解决什么问题

假设没有主题，每个页面都自己写颜色：

```kotlin
Text(
    text = "标题",
    color = Color(0xFF6750A4),
)
```

另一个页面又写：

```kotlin
Button(
    colors = ButtonDefaults.buttonColors(
        containerColor = Color(0xFF6750A4),
    ),
) { ... }
```

问题：

- 颜色重复
- 修改品牌色需要搜索很多文件
- 深色模式难以统一处理
- 不同组件容易出现不一致

主题的目标是：

```text
在上层集中提供颜色、字体、形状等配置
                 ↓
内部所有组件按需读取
```

---

## 3. `MaterialTheme` 是什么

项目内部最终调用：

```kotlin
MaterialTheme(
    colorScheme = colorScheme,
    typography = DemoTypography(),
    content = content,
)
```

`MaterialTheme` 是 Material 3 提供的主题容器。

它主要向内部 UI 子树提供：

```kotlin
MaterialTheme.colorScheme
MaterialTheme.typography
MaterialTheme.shapes
```

项目首页读取：

```kotlin
MaterialTheme.typography.headlineSmall
```

```kotlin
MaterialTheme.colorScheme.onSurfaceVariant
```

```kotlin
MaterialTheme.colorScheme.primary
```

它们之所以可以读取到值，是因为 `HomeScreen` 位于 `MaterialTheme` 的 `content` 子树内部。

---

## 4. 主题包裹范围

入口结构：

```text
DemoTheme
└── DemoApp
    ├── HomeScreen
    │   ├── TopAppBar
    │   ├── Text
    │   └── Card
    └── ComponentDetailScreen
        ├── Text
        ├── Surface
        └── Button
```

因此，`DemoApp` 下方的所有组件都能读取同一个主题。

如果某个组件放在 `DemoTheme` 外部，它就不能读取项目这层自定义主题配置，只能读取更外层存在的默认或其他主题环境。

---

## 5. `DemoTheme` 的参数

```kotlin
fun DemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    demoTheme: DemoThemeConfig = DemoThemeConfig(),
    content: @Composable () -> Unit,
)
```

逐个看。

### 5.1 `darkTheme`

```kotlin
darkTheme: Boolean = isSystemInDarkTheme()
```

- 类型：`Boolean`
- 默认值：跟随系统当前是否使用深色主题

不传时：

```kotlin
DemoTheme {
    DemoApp()
}
```

会使用默认值。

强制深色：

```kotlin
DemoTheme(darkTheme = true) {
    DemoApp()
}
```

### 5.2 `dynamicColor`

```kotlin
dynamicColor: Boolean = true
```

控制 Android 12 及以上是否使用系统动态颜色（动态颜色指从用户壁纸中提取一套配色，第 77 章详解）。

项目逻辑：

```kotlin
if (dynamicColor && Android版本 >= Android 12) {
    使用系统动态颜色
}
```

否则回退到项目定义的亮色或暗色配色。

### 5.3 `demoTheme`

```kotlin
demoTheme: DemoThemeConfig = DemoThemeConfig()
```

这是项目自己增加的配置，不属于 Material 3 原生 `MaterialTheme`。

它集中保存 Demo 页面使用的一些尺寸，例如：

- 列表文字字号
- 列表文字行高
- 详情描述字号
- 示例区域内边距

### 5.4 `content`

```kotlin
content: @Composable () -> Unit
```

表示被主题包裹的实际 UI。

调用：

```kotlin
DemoTheme {
    DemoApp()
}
```

大括号中的：

```kotlin
DemoApp()
```

就是 `content`。

---

## 6. 颜色方案如何选择

项目代码：

```kotlin
val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    }

    darkTheme -> DarkColorScheme
    else -> LightColorScheme
}
```

这是 Kotlin `when` 表达式。

选择顺序：

```text
是否开启动态颜色，并且系统 >= Android 12？
├── 是：使用系统动态颜色
└── 否：是否深色模式？
    ├── 是：DarkColorScheme
    └── 否：LightColorScheme
```

注意：

> `when` 从上到下匹配，命中第一项后就使用对应结果。

---

## 7. `LocalContext.current` 是什么

```kotlin
val context = LocalContext.current
```

它让 Composable 在当前 UI 环境中取得 Android `Context`。

动态配色函数需要 `Context`：

```kotlin
dynamicDarkColorScheme(context)
```

```kotlin
dynamicLightColorScheme(context)
```

不能把 `LocalContext.current` 简单理解成全局单例。它表示当前 Composition 环境提供的 Context。

Flutter 概念上接近：

```dart
final theme = Theme.of(context);
```

两者都依赖当前组件所在的上层环境，但类型和实现机制不同。

---

## 8. `MaterialTheme.colorScheme` 如何读取值

首页：

```kotlin
Text(
    text = "选择下面的组件入口",
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

卡片箭头：

```kotlin
Text(
    text = "→",
    color = MaterialTheme.colorScheme.primary,
)
```

这里不是重新创建一套主题，而是读取上层最近的 `MaterialTheme` 提供值。

结构：

```text
MaterialTheme(colorScheme = colorScheme)
└── HomeScreen
    └── Text 读取 MaterialTheme.colorScheme.primary
```

如果内部再次嵌套一个 `MaterialTheme`：

```kotlin
MaterialTheme(colorScheme = anotherScheme) {
    Text(...)
}
```

该 `Text` 会读取距离自己最近的主题值。

---

## 9. `CompositionLocalProvider` 是什么

`CompositionLocalProvider` 的作用是向下传递局部值：它把某个值提供给自己的内容子树，子树中的组件读取对应环境对象时就能拿到这个值（背后的 `CompositionLocal` 机制详见第 42 章）。

项目代码：

```kotlin
CompositionLocalProvider(
    LocalDemoTheme provides demoTheme,
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = DemoTypography(),
        content = content,
    )
}
```

项目希望提供的内容不仅有 Material 3 自带的颜色和字体，还有：

```kotlin
DemoThemeConfig
```

所以它定义了一个项目自己的环境值：

```kotlin
LocalDemoTheme
```

然后使用：

```kotlin
LocalDemoTheme provides demoTheme
```

含义：

> 在当前内容子树中，读取 `LocalDemoTheme.current` 时，得到这里提供的 `demoTheme`。

首页：

```kotlin
val demoTheme = LocalDemoTheme.current
```

随后使用：

```kotlin
fontSize = demoTheme.listItemTextPx.pxSp()
```

完整路径：

```text
DemoTheme(demoTheme = DemoThemeConfig(...))
    ↓ provides
LocalDemoTheme
    ↓ current
EntryCard 读取配置
```

---

## 10. 与 Flutter 的对比

Flutter 通常通过 `Theme`、`InheritedWidget` 或 Provider 向下传值。

```dart
MaterialApp(
  theme: ThemeData(
    colorScheme: colorScheme,
  ),
  home: const DemoApp(),
)
```

子组件读取：

```dart
final colors = Theme.of(context).colorScheme;
```

Compose：

```kotlin
MaterialTheme(
    colorScheme = colorScheme,
) {
    DemoApp()
}
```

子组件读取：

```kotlin
val colors = MaterialTheme.colorScheme
```

项目自定义配置：

| Compose | Flutter 概念对照 |
|---|---|
| `CompositionLocal`（向下传递局部值的方式，第 42 章） | `InheritedWidget` / Provider 提供的上层环境值 |
| `CompositionLocalProvider` | 在子树上方注入值 |
| `LocalDemoTheme.current` | `Something.of(context)` 或 Provider 读取 |

区别是 Compose 读取时通常不显式传 `BuildContext`。

---

## 11. `SideEffect` 在主题中的作用

`SideEffect` 用来执行「副作用」：副作用指组合函数体之外、会对外部世界产生影响的操作（例如修改系统窗口）。它在每次成功重组后执行一次，完整限制详见第 25 章。

项目中：

```kotlin
SideEffect {
    val window = (view.context as Activity).window
    WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !darkTheme
        isAppearanceLightNavigationBars = !darkTheme
    }
}
```

这里修改的不是 Compose 组件，而是 Android 系统窗口的图标明暗样式。

当主题状态成功应用后，`SideEffect` 将 Compose 当前结果同步到外部 Android Window。

本章先记住边界：

```text
MaterialTheme 配置 Compose UI
SideEffect 同步外部系统对象
```

`SideEffect` 的完整生命周期和使用限制会在第 25 章详讲。

---

## 12. `DemoContent` 为什么内部又套一次 `MaterialTheme`

详情页：

```kotlin
@Composable
private fun DemoContent(content: @Composable () -> Unit) {
    val typography = MaterialTheme.typography

    val demoTypography = typography.copy(
        bodyMedium = typography.bodyMedium.copy(
            fontSize = ...,
            lineHeight = ...,
        ),
    )

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = demoTypography,
        content = content,
    )
}
```

它保留外层：

- 颜色
- 形状

只替换示例区域中的部分字体尺寸。

作用范围：

```text
外层 DemoTheme
└── 详情页
    ├── 页面标题：使用外层字体
    └── DemoContent
        └── 示例组件：使用局部修改后的字体
```

这说明主题可以局部覆盖，而不必全局修改。

---

## 13. 常见错误

### 错误 1：把固定颜色写满所有页面

这样会破坏深色模式和主题统一性。

优先考虑：

```kotlin
MaterialTheme.colorScheme.primary
MaterialTheme.colorScheme.surface
MaterialTheme.colorScheme.onSurface
```

### 错误 2：在 `DemoTheme` 外读取 `LocalDemoTheme.current`

如果没有合理默认值，可能得到非预期配置或直接报错，取决于 Local 的定义方式。

### 错误 3：误以为修改主题会直接修改所有 Android 系统窗口属性

Compose 主题主要管理 Compose UI。状态栏图标等外部对象需要额外同步。

### 错误 4：认为内部不能再次使用 `MaterialTheme`

可以局部嵌套，用距离组件最近的主题覆盖部分配置。

---

## 14. 本章检查题

1. `DemoTheme` 本身是 Material 3 原生函数吗？
2. `MaterialTheme.colorScheme.primary` 的值来自哪里？
3. `content: @Composable () -> Unit` 表示什么？
4. `LocalDemoTheme.current` 为什么能读取到 `DemoThemeConfig`？
5. 内部再次嵌套 `MaterialTheme` 会影响整个应用吗？

答案：

1. 不是，它是项目封装的主题函数。
2. 来自距离当前组件最近的 `MaterialTheme`。
3. 一个无参数、返回 Unit、能够构建 UI 的内容函数。
4. 上层 `CompositionLocalProvider` 提供了该值。
5. 不会，只影响它包裹的子树。

---

## 15. 本章总结

项目主题链路：

```text
DemoTheme
├── 选择 Light / Dark / Dynamic colorScheme
├── 用 SideEffect 同步系统栏图标样式
├── CompositionLocalProvider 提供 DemoThemeConfig
└── MaterialTheme 提供颜色、字体、形状
    └── content：DemoApp 及其所有页面
```

一句话：

> `DemoTheme` 是项目主题入口，`MaterialTheme` 提供 Material 颜色和排版，`CompositionLocalProvider` 提供项目自定义的子树级配置。
