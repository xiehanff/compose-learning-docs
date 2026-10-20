# 第 77 章：主题进阶：动态取色、Shape 与 Typography

## 1. 本章目标

学完后，你应当能够：

- 掌握 MaterialTheme 三件套：colorScheme、typography、shapes
- 用 lightColorScheme / darkColorScheme 局部覆盖，而不是手写整个配色
- 实现 Android 12+ 动态取色（跟随系统动态色板）与低版本降级方案
- 自定义 Shapes 圆角体系与 Typography 字体族，让组件风格全局统一

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.material3`：MaterialTheme、lightColorScheme / darkColorScheme、dynamicLightColorScheme / dynamicDarkColorScheme、Shapes、Typography
- `android.os.Build`：动态取色要先判 `VERSION.SDK_INT >= VERSION_CODES.S`
- `androidx.compose.ui.graphics.Color`、`androidx.compose.foundation.shape.RoundedCornerShape`
- `androidx.compose.ui.text.TextStyle`、`FontFamily`、`Font`
- `androidx.compose.foundation.isSystemInDarkTheme`

## 3. 项目源码片段

一个完整的可换肤主题：支持动态取色、深浅色、自定义圆角与字体。

```kotlin
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // Android 12+（API 31+）：从壁纸提取色板。dynamicColor 是应用自己的开关，
        // material3 没有「系统是否开启动态取色」这类查询 API，所以判断条件只有这两个
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(primary = Color(0xFF90CAF9))
        else -> lightColorScheme(primary = Color(0xFF1E88E5))
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

// 圆角体系：small/medium/large 对应不同组件
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// 注册 res/font 下的自定义字体并映射到文字样式：
// 推荐基于默认 Typography 的对应角色 .copy()，只改字体族/字重，
// 保留该角色默认的字号、行高等参数；前提是 res/font 下存在对应字体文件
val AppTypography = Typography(
    titleLarge = Typography().titleLarge.copy(
        fontFamily = FontFamily(Font(R.font.title_font)),
        fontWeight = FontWeight.Bold
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = FontFamily(Font(R.font.body_font))
    )
)
```

## 4. 逐步解释

1. 三件套如何贯穿组件：MaterialTheme 接收 colorScheme、typography、shapes 三个参数，它们通过 CompositionLocal 注入整棵组合树。Button 的背景取 colorScheme.primary，Card 的圆角取 shapes.medium，Text 默认样式取 typography.bodyLarge——组件开发者只声明"用什么角色"，具体值由主题决定。这就是换肤能力的来源：改主题一处，全应用联动。

2. 全量定制 vs 局部覆盖：lightColorScheme() 与 darkColorScheme() 已提供 Material 规范的默认值，你只需覆盖想改的槽位（如 primary、secondaryContainer）。切忌从零手写全部 20 多个颜色槽——容易漏掉 tertiary、surfaceVariant 等导致组件配色不协调。记住：先默认，再覆盖。

3. 动态取色与降级：dynamicLightColorScheme(context) / dynamicDarkColorScheme(context) 读取的是**系统生成并维护的动态色板**（Material You，Android 12 起由系统根据壁纸等条件推导），应用拿到的是成品色板，不是自己去解析壁纸图片。这是 Android 12（API 31）才有的能力，必须先判 SDK 版本再调用；条件满足就走动态分支，否则降级到普通 light/dark 色板。material3 没有提供「系统是否开启动态取色」的查询 API（`isDynamicColorEnabled()` 并不存在），所以条件就是「应用自己的 dynamicColor 开关 + `SDK_INT >= S`」，第 3 章的 `DemoTheme` 也是这样写的。

4. Shapes 体系：Shapes 定义 extraSmall / small / medium / large / extraLarge 五个圆角槽位（material3 1.4 还有 largeIncreased / extraLargeIncreased 等内部槽位）。每个组件通过自己的 Defaults 对象决定默认形状，且并非所有组件都走 shapes 槽位。以本仓库基线（material3 1.4.0）源码为例：Card 默认取 shapes.medium，AlertDialog 默认取 shapes.extraLarge，FloatingActionButton 默认取 shapes.large；而 Button 不走槽位——`ButtonDefaults.shape` 的默认值由 CornerFull token 解析为 CircleShape（全圆角），不是 shapes.medium/small。因此修改 shapes.medium 不会改变默认 Button 的圆角；想要特定 Shape 必须在调用处显式传 `shape` 参数。不同组件、不同版本的具体默认以对应版本的 Defaults.shape（如 CardDefaults.shape）与官方 API 文档为准，不要背一张“规范映射表”。自定义 Shapes 时只给要改的槽位传值，未指定的沿用默认。

5. Typography：默认提供 displayLarge 到 labelSmall 共 15 个文字样式角色，每个角色自带 Material 规范的字号、字重、行高。自定义字体时，推荐用 `Typography().titleLarge.copy(...)` 这种写法：从默认角色出发只改字体族/字重，保留其余默认参数；直接手写整个 TextStyle 容易丢掉规范字号与行高。前提是字体文件必须放在 res/font 目录并遵守 Android 资源命名规则，否则 R.font.xxx 无法解析。

6. 主题与语义：主题不只是好看。配色角色应满足无障碍对比度要求——按 WCAG AA 级，正文文本前景/背景对比度至少 4.5:1（大号文本约 3:1 即可）。这是设计规范层面的要求，平台运行时不会强制校验，需要设计与检查工具把关。状态层（state layer）通过 colorScheme 的透明度叠加实现按压、悬停反馈。动态取色下尤其要检查对比度，因为壁纸颜色不可控。

记住：组件永远读主题的角色值，业务代码里不要写死颜色。

## 5. 实际执行过程

1. App 启动调用 AppTheme，计算 darkTheme 与 dynamicColor 两个开关。
2. Android 12+ 且开启动态取色时，读取壁纸色板生成动态 colorScheme；低版本或关闭时走 lightColorScheme / darkColorScheme 分支。
3. MaterialTheme 把 colorScheme、typography、shapes 注入组合树。
4. 组合树中的 Button 读取 colorScheme.primary 作为背景、ButtonDefaults.shape 作为圆角（material3 1.4.0 默认是全圆角，不取 shapes 槽位，改 shapes.medium 不影响默认 Button）；Card 读取 shapes.medium 作为圆角；Text 读取 typography 对应角色。
5. 用户切换深色模式时，uiMode 配置变化触发 Activity 重建（未手动处理时），isSystemInDarkTheme 重新求值，主题重新计算；更换壁纸时动态色板是否热更新、是否触发重组依赖系统版本与具体行为（如需配置变更或重启），不做无条件承诺。

## 6. Flutter / Dart 3 对照

Flutter 用 ThemeData 表达同样的三件套：

```dart
final colorScheme = ColorScheme.fromSeed(
  seedColor: const Color(0xFF1E88E5),
  brightness: isDark ? Brightness.dark : Brightness.light,
);
final theme = ThemeData(
  colorScheme: colorScheme,
  textTheme: const TextTheme(
    titleLarge: TextStyle(fontFamily: 'MyFont', fontWeight: FontWeight.bold),
  ),
  appBarTheme: const AppBarTheme(centerTitle: true),
  cardTheme: CardThemeData(
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
  ),
);
MaterialApp(theme: theme, darkTheme: theme.copyWith(brightness: Brightness.dark));
```

相似点：TextTheme 对应 Typography；MaterialApp 的 theme / darkTheme 对应深浅色切换；两个平台都有 Material 3 动态取色入口。要注意一个关键差异：Flutter 的 `ColorScheme.fromSeed(seedColor: ...)` 是**从种子色算法派生整套 tonal 色板**，改 seedColor 会重算全部颜色槽；Compose 的 `lightColorScheme(primary = ...)` 只是把 Material 规范的**固定默认值**拿来覆盖指定槽位，改 primary 不会重新生成其余颜色——两者不是对应关系。动态取色上，Flutter 的 dynamicSchemeVariant 控制取色变体但需要自备 seedColor（壁纸色要插件获取）；Compose 的 dynamic*ColorScheme 直接读系统动态色板。

本质区别：Compose 的 MaterialTheme 通过 CompositionLocal 注入，换肤即时且粒度细，可以按局部子树覆盖主题；Flutter 的 ThemeData 是 MaterialApp 级别的配置对象，局部覆盖需用 Theme widget 包裹子树，组件取色走 Theme.of(context) 向上查找。另外 Compose 的 shapes 是独立三件套之一，Flutter 没有全局 Shape 体系，圆角散落在各组件主题（CardTheme、ButtonTheme）里，全局统一性更难保证。

## 7. 常见错误

1. 业务代码直接写 Color(0xFF...) 而不用 colorScheme 角色：换肤时这些组件纹丝不动，主题形同虚设。
2. 动态取色不做版本判断：在 Android 11 上调用 dynamicLightColorScheme 直接崩溃。必须先判 Build.VERSION.SDK_INT >= S 再调用。
3. 手写整个 ColorScheme：漏掉 tertiary、surfaceContainer 等槽位导致部分组件颜色怪异。应基于 lightColorScheme() 只覆盖差异。
4. 自定义 Shape 忽略对比度与可点性：圆角过大的按钮视觉上"点不到"；同时要检查前景色与背景色的对比度是否达标。
5. 字体只改一个 TextStyle 而不管 Typography 层级：字号层级混乱。应整体映射 display / headline / title / body / label 各角色。

## 8. 本章检查题

1. MaterialTheme 三件套是什么？它们如何影响一个 Button 的显示？
2. 动态取色在低版本设备上如何降级？
3. Shapes 的槽位与组件如何对应？Button 默认用哪个形状？
4. 为什么强调“先默认再覆盖”ColorScheme？

答案：

1. colorScheme、typography、shapes。Button 的背景与文字色来自 colorScheme（primary / onPrimary），文字样式来自 typography；圆角取决于组件各自的 Defaults：Button 默认是全圆角（material3 1.4.0 中由 CornerFull 解析为 CircleShape，不取 shapes.small/medium，改 shapes.medium 不会影响默认 Button），Card 这类走槽位的组件才取 shapes 对应档位。
2. 先判断应用自己的 dynamicColor 开关与 `Build.VERSION.SDK_INT >= S`，不满足就走 lightColorScheme / darkColorScheme 分支，绝不在低版本调用 dynamic*ColorScheme（material3 没有「系统是否开启动态取色」的查询 API）。
3. 槽位与组件不足一一对应：Card 默认取 shapes.medium，AlertDialog 默认取 shapes.extraLarge，FloatingActionButton 默认取 shapes.large（以 material3 1.4.0 源码为例）；Button 则不走槽位，默认是全圆角（CornerFull → CircleShape），想要特定 Shape 需显式传 shape 参数。各组件具体默认以对应版本的 Defaults.shape 与官方 API 文档为准，不要背一张固定映射表。
4. 因为手写 20 多个颜色槽极易遗漏或配错，导致组件间风格不一致；基于默认值覆盖差异，既符合规范，又只需关心业务需要改的槽位。

## 9. 本章总结

主题系统的本质是"组件读角色、主题给值"：MaterialTheme 用 colorScheme、typography、shapes 三件套统一全应用外观，动态取色让应用跟随系统动态色板，Shape 与 Typography 让风格可定制。坚持业务代码零写死颜色、主题先默认后覆盖（lightColorScheme 只覆盖指定槽位、不重新派生色板）、动态取色必做降级，就能得到可换肤且符合 Material 规范的应用。

官方参考：

- [Material Design 3 in Compose（主题/形状/字体体系）](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [dynamicLightColorScheme API](https://developer.android.com/reference/kotlin/androidx/compose/material3/package-summary#dynamicLightColorScheme(android.content.Context))
- [Fonts in Compose（自定义字体与 Typography）](https://developer.android.com/develop/ui/compose/text/fonts)
