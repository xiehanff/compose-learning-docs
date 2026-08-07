# 第 77 章：主题进阶：动态取色、Shape 与 Typography

## 1. 本章目标

学完后，你应当能够：

- 掌握 MaterialTheme 三件套：colorScheme、typography、shapes
- 用 lightColorScheme / darkColorScheme 局部覆盖，而不是手写整个配色
- 实现 Android 12+ 动态取色（壁纸取色）与低版本降级方案
- 自定义 Shapes 圆角体系与 Typography 字体族，让组件风格全局统一

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.material3`：MaterialTheme、lightColorScheme / darkColorScheme、dynamicLightColorScheme / dynamicDarkColorScheme、isDynamicColorEnabled、Shapes、Typography
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
        // Android 12+ 且系统开启动态取色：从壁纸提取色板
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            isDynamicColorEnabled() ->
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

// 注册 res/font 下的自定义字体并映射到文字样式
val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily(Font(R.font.title_font)),
        fontWeight = FontWeight.Bold
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily(Font(R.font.body_font))
    )
)
```

## 4. 逐步解释

1. 三件套如何贯穿组件：MaterialTheme 接收 colorScheme、typography、shapes 三个参数，它们通过 CompositionLocal 注入整棵组合树。Button 的背景取 colorScheme.primary，Card 的圆角取 shapes.medium，Text 默认样式取 typography.bodyLarge——组件开发者只声明"用什么角色"，具体值由主题决定。这就是换肤能力的来源：改主题一处，全应用联动。

2. 全量定制 vs 局部覆盖：lightColorScheme() 与 darkColorScheme() 已提供 Material 规范的默认值，你只需覆盖想改的槽位（如 primary、secondaryContainer）。切忌从零手写全部 20 多个颜色槽——容易漏掉 tertiary、surfaceVariant 等导致组件配色不协调。记住：先默认，再覆盖。

3. 动态取色与降级：dynamicLightColorScheme(context) / dynamicDarkColorScheme(context) 从系统壁纸提取 Material You 色板。这是 Android 12（API 31）才有的能力，必须先判 SDK 版本再调用；isDynamicColorEnabled() 进一步检查系统设置是否开启，两者都满足才走动态分支，否则降级到普通 light/dark 色板。

4. Shapes 体系：Shapes 定义 extraSmall / small / medium / large / extraLarge 五个圆角槽位。Material 3 规范中，FloatingActionButton 用 extraLarge，Card、Dialog 用 medium，Chip 等小控件用 small……组件按角色取对应圆角，整体视觉自然统一。自定义时只给要改的槽位传值，未指定的沿用默认。

5. Typography：默认提供 displayLarge 到 labelSmall 共 15 个文字样式角色。自定义字体时，先用 FontFamily(Font(R.font.xxx)) 把 res/font 下的字体文件包装成 FontFamily，再映射到需要的角色上。注意字体文件必须放在 res/font 目录，并遵守 Android 资源命名规则。

6. 主题与语义：主题不只是好看。primary 与 onPrimary 必须满足对比度要求（正文至少 4.5:1），状态层（state layer）通过 colorScheme 的透明度叠加实现按压、悬停反馈。动态取色下尤其要检查对比度，因为壁纸颜色不可控。

记住：组件永远读主题的角色值，业务代码里不要写死颜色。

## 5. 实际执行过程

1. App 启动调用 AppTheme，计算 darkTheme 与 dynamicColor 两个开关。
2. Android 12+ 且开启动态取色时，读取壁纸色板生成动态 colorScheme；低版本或关闭时走 lightColorScheme / darkColorScheme 分支。
3. MaterialTheme 把 colorScheme、typography、shapes 注入组合树。
4. 组合树中的 Button 读取 colorScheme.primary 作为背景、shapes.medium 作为圆角；Text 读取 typography 对应角色。
5. 用户切换深色模式或更换壁纸时，系统配置变化触发重组，主题重新计算，全界面自动换肤。

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

相似点：都有"种子色 + 派生色板"的思路（ColorScheme.fromSeed 对应 lightColorScheme 的默认派生）；TextTheme 对应 Typography；MaterialApp 的 theme / darkTheme 对应深浅色切换；Material 3 动态取色两个平台都有——Flutter 用 dynamicSchemeVariant 控制取色变体，但需要自己提供 seedColor（壁纸主色要插件获取），而 Compose 直接读系统壁纸。

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
3. Shapes 的 small / medium / large 通常对应哪些组件？
4. 为什么强调"先默认再覆盖"ColorScheme？

答案：

1. colorScheme、typography、shapes。Button 的背景与文字色来自 colorScheme（primary / onPrimary），圆角来自 shapes（small / medium 等），文字样式来自 typography。
2. 先判断 Build.VERSION.SDK_INT >= S 且 isDynamicColorEnabled() 为真，不满足就走 lightColorScheme / darkColorScheme 分支，绝不在低版本调用 dynamic*ColorScheme。
3. 按 Material 3 规范，Chip 等小控件用 small，Card、Dialog 用 medium，BottomSheet、Drawer 等大容器用 large，FloatingActionButton 用 extraLarge。
4. 因为手写 20 多个颜色槽极易遗漏或配错，导致组件间风格不一致；基于默认值覆盖差异，既符合规范，又只需关心业务需要改的槽位。

## 9. 本章总结

主题系统的本质是"组件读角色、主题给值"：MaterialTheme 用 colorScheme、typography、shapes 三件套统一全应用外观，动态取色让应用跟随系统壁纸，Shape 与 Typography 让风格可定制。坚持业务代码零写死颜色、主题先默认后覆盖、动态取色必做降级，就能得到可换肤且符合 Material 规范的应用。
