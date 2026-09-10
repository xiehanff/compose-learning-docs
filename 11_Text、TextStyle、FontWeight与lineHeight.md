# 第 11 章：Text、TextStyle、FontWeight 与 lineHeight

## 1. 本章目标

系统掌握项目中的文本参数与主题样式覆盖；知道有富文本这回事，它的细节在第 61 章。

学完后，你应当能够：

- 区分 text、style 与顶层文字参数
- 理解 fontSize 和 lineHeight 是两个独立值
- 使用 FontWeight、maxLines、overflow
- 知道富文本（`buildAnnotatedString` + `SpanStyle`）能做什么，细节在第 61 章

## 2. 项目中的位置

- `components/TextDemo.kt`
- `ui/detail/ComponentDetailScreen.kt`
- `ui/theme/Type.kt`



## 3. 项目源码片段

```kotlin
Text(
    text = info.description,
    style = MaterialTheme.typography.bodyMedium,
    fontSize = demoTheme.detailDescriptionTextPx.pxSp(),
    lineHeight = demoTheme.detailDescriptionLineHeightPx.pxSp(),
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

Text(
    "这是一段很长的文字",
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
```

## 4. 逐步解释

`Text` 可以先接收一个完整的 `TextStyle`，再由顶层参数覆盖其中的同名字段。项目先采用 `bodyMedium`，再用全局 DemoThemeConfig 覆盖 fontSize 和 lineHeight，因此既保留主题的字体族等属性，又统一调整演示区字号。

`fontSize` 决定字形大小；`lineHeight` 决定每行占用的垂直高度，两者不能混为一谈。单行省略必须同时设置 `maxLines = 1` 和 `overflow = Ellipsis`。最后提一句富文本：让同一段文字里的不同片段带不同样式（颜色、字重、下划线）属于另一项能力，由 `buildAnnotatedString` + `SpanStyle` 提供，本章不展开，第 61 章详解。

## 5. 实际执行过程

1. Text 读取最终 TextStyle
2. 若是富文本，先按样式区间拆分文字（这里的机制第 61 章再展开）
3. 文本测量器根据宽度和 maxLines 断行
4. lineHeight 决定行框高度
5. overflow 决定超出部分表现

## 6. Flutter / Dart 3 对照

Flutter 的 Text + TextStyle 与 Compose 非常接近；富文本（第 61 章）对应 Text.rich / TextSpan。Compose 的主题通过 MaterialTheme.typography 读取，顶层参数同样可以覆盖 style。

```dart
Text(
  description,
  maxLines: 2,
  overflow: TextOverflow.ellipsis,
  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
    fontSize: 16,
    height: 1.4,
    fontWeight: FontWeight.w600,
  ),
);
```

## 7. 常见错误

- 只设置 overflow，不设置 maxLines，省略号不按预期出现
- 把 lineHeight 当成额外的上下 padding
- 在所有 Text 上硬编码颜色，破坏暗色主题
- 同时在 style 和顶层参数设置同一字段，却没意识到顶层参数会覆盖

## 8. 本章检查题

1. 项目中的 description 最终字号来自哪里？
2. fontSize 16sp、lineHeight 24sp 各控制什么？
3. 如果想把同一段文字里的几个字单独改成红色加粗，用本章的 `TextStyle` 能做到吗？需要哪一类能力？

答案：

1. 来自主题 bodyMedium 基础样式，再被全局 DemoThemeConfig 的 fontSize 和 lineHeight 覆盖，因此保留字体族等主题属性又统一调整了演示区字号。
2. fontSize 控制字形大小；lineHeight 控制每行占用的垂直高度。
3. 本章的 `TextStyle` 作用于整段文字，做不到只改几个字。想让局部片段变红加粗，要靠 `buildAnnotatedString` + `SpanStyle` 这类按片段设置样式的能力，具体写法见第 61 章。

## 9. 本章总结

Text 的最终外观由主题 style 与顶层覆盖参数共同决定。字号、行高、字重和溢出是本章的四项独立能力，应分别配置；富文本是另一项能力，第 61 章单独讲。
