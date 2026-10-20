# 第 11 章：Text、TextStyle、FontWeight 与 lineHeight

## 1. 本章目标

先看两个真实问题。其一：设计稿标注字号 16，你写了 `fontSize = 16.sp`，行距却总跟设计稿对不上——因为行距是另一个参数 `lineHeight`，两个值互相独立。其二：想让长标题单行显示省略号，加了 `overflow = TextOverflow.Ellipsis`，省略号却死活不出现——因为还差 `maxLines`。本章把字号、行高、字重、截断拆成四件独立的事讲清楚，并回答"字号为什么用 sp 而不是 dp"。

系统掌握项目中的文本参数与主题样式覆盖；知道有富文本这回事，它的细节在第 61 章。

学完后，你应当能够：

- 区分 text、style 与顶层文字参数，说清顶层参数如何覆盖 style 里的同名字段
- 理解 fontSize 和 lineHeight 是两个独立值，知道多出的行高空间去了哪里
- 使用 FontWeight、FontStyle、maxLines、overflow
- 描述换行的基本规则与 softWrap 的作用
- 解释 fontSize 为什么用 sp，sp 与系统字体缩放（无障碍）的关系，以及为什么不建议把字号固定在设计稿像素上
- 知道富文本（`buildAnnotatedString` + `SpanStyle`）能做什么，细节在第 61 章

## 2. 项目中的位置

- `components/TextDemo.kt`
- `ui/detail/ComponentDetailScreen.kt`
- `ui/theme/Type.kt`



## 3. 项目源码片段

下面是项目节选，依赖 `ComponentInfo`（第 36 章）、`LocalDemoTheme`（第 3、42 章）与 `.pxSp()`（第 44 章）等项目专属符号，不可直接复制；可独立运行的对照示例见 3.1。

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

### 3.1 独立示例：四项能力一屏对照

参与编译校验，只依赖本章之前见过的组件。初始界面：一列文字，从上到下依次是加粗斜体的主题色标题、两组同字号不同行高的两行文字、一段单行省略的长文。

<!-- compile -->
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    // 1) 基础四参数：fontSize 字号、fontWeight 字重、fontStyle 斜体、color 颜色
    Text(
        text = "标题文字",
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.primary,
    )
    // 2) 行高对照：字号相同、lineHeight 不同，行与行的疏密不同
    Text("行高 16sp：第一行\n第二行", fontSize = 16.sp, lineHeight = 16.sp)
    Text("行高 28sp：第一行\n第二行", fontSize = 16.sp, lineHeight = 28.sp)
    // 3) 截断两件套：maxLines 与 overflow 必须同时设置（见 4.3）
    Text(
        "这是一段很长的文字，超出单行宽度后将被截断",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
```

操作：把预览宽度逐步压窄。预期观察：第 3 段始终保持一行，末尾出现省略号；前两段可能被挤成更多行，但每一行的高度仍由 lineHeight 决定，行距不变。

## 4. 逐步解释

### 4.1 style 与顶层参数：先整体采用，再逐项覆盖

`Text` 可以先接收一个完整的 `TextStyle`，再由顶层参数覆盖其中的同名字段。项目先采用 `bodyMedium`，再用全局 DemoThemeConfig 覆盖 fontSize 和 lineHeight，因此既保留主题的字体族等属性，又统一调整演示区字号。

`TextStyle` 是把 fontSize、fontWeight、fontFamily、lineHeight、color 等二十多个字段打包的数据类。`Text(style = ...)` 先整体采用这份样式；写在 Text 上的顶层参数（fontSize、color、fontWeight…）再逐个覆盖同名字段，没写的字段继续沿用 style。

首次出现的 Kotlin 语法：`FontWeight.Bold` 里的 `FontWeight` 是一个类，`Bold`、`Normal`、`Medium`、`Light` 和 `W100`…`W900` 是它伴生对象上的常量。Kotlin 用伴生对象（companion object）把"类级别的常量"挂在类名下，访问时不需要创建实例，读起来像静态字段。

四个基础参数各司其职：`fontSize` 字形大小（sp）；`fontWeight` 笔画粗细；`fontStyle` 斜体（`FontStyle.Italic`，可与 Bold 叠加成"加粗且斜体"）；`color` 文字颜色。颜色建议读 `MaterialTheme.colorScheme` 的角色而不是硬编码色值，原因见第 3 章和本章常见错误。

### 4.2 lineHeight 与 sp：行高、字号、无障碍缩放

`fontSize` 决定字形大小；`lineHeight` 决定每行占用的垂直高度，两者不能混为一谈。两个字段都是文字单位 `TextUnit` 的值：fontSize 16.sp + lineHeight 24.sp 表示字形 16sp、每行的行框高 24sp。多出的 8sp 不是插在两行之间，而是分布在每个行框内部文字的上下（类似行内留白）；首行、末行的精确分配还受 `lineHeightStyle` 与平台文字样式影响，先按"上下均分"理解即可（教学简化，本仓库不展开 lineHeightStyle）。

为什么文字单位是 sp 不是 dp：sp 会结合 fontScale——用户在系统设置里选择的字体大小偏好——经当前 Density 的字体转换器换算（第 41 章）。只有在线性模型下才能简写为在 dp 基础上乘 fontScale；本仓库基线支持非线性转换，不限定 Android 14，也会影响常规字号。用户调大系统字体时，sp 文字按字体曲线缩放；dp 只随屏幕密度缩放，不随字体偏好缩放。用 sp 写字号，等于默认接上系统的无障碍字体缩放通道。官方对 sp 的定义（"随用户字体偏好缩放的像素"）见资源类型文档的 Dimension 一节。

Compose 在类型层面也强制了这一点：`fontSize` 参数的类型是 `TextUnit`，`.sp` 返回 `TextUnit`，而 `.dp` 返回 `Dp`——`fontSize = 16.dp` 是类型不匹配的编译错误（见第 7 节反例）；`TextUnit` 只支持 sp 和 em 两种文字单位，没有 dp 的位置。

不把字号固定死在设计稿像素：第 1—45 章的 compose_demo 用 `.pxSp()` 把设计稿 px 换算成 sp，换算时除以了 fontScale，试图抵消字体放大。只有线性模型下才能据此推导尺寸恒定；非线性转换下还可能反向缩小，无法保证遵循用户的字体偏好。这是教学工程为对齐设计稿做的取舍，风险与边界在第 41、44、45 章专门讨论；生产工程的常规做法是直接用 sp、让文字跟随系统设置，不推荐为"像素级还原设计稿"抵消 fontScale。

### 4.3 maxLines + overflow：截断两件套

单行省略必须同时设置 `maxLines = 1` 和 `overflow = TextOverflow.Ellipsis`。

原因在处理顺序：文字先按可用宽度断行；只有当行数达到 `maxLines` 上限后，放不下的剩余内容才交给 `overflow` 处理。`overflow` 默认值是 `TextOverflow.Clip`——直接切掉、不留省略号，所以"只写 maxLines = 1"得到的是硬切口；反过来只写 overflow 不写 maxLines 时，文字可以一直换行，永远轮不到 overflow 生效，这就是"省略号不出现"的原因。

三种 overflow：`Clip` 切掉；`Ellipsis` 在末行末尾补省略号；`Visible` 强制画出边界（文字会画出 Text 的布局框，很少使用）。

对照示例。参与编译校验。初始界面：同一段长文按四种写法纵向排列。

<!-- compile -->
```kotlin
val longText = "这是一段足够长的示例文字，用来演示换行与截断行为，宽度不够时文字会先尝试换行"
Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    // 1) 默认：不设 maxLines，按宽度自动换行，全部内容可见
    Text(longText)
    // 2) 只设 maxLines：overflow 默认 Clip，第一行末尾被硬切，没有省略号
    Text(longText, maxLines = 1)
    // 3) maxLines + Ellipsis：第一行末尾出现省略号
    Text(longText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    // 4) softWrap = false：禁止软换行，整段按一行排布后再截断
    Text(longText, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
```

预期观察（常见手机宽度下）：第 1 种占两到三行、完整显示；第 2 种一行、末尾像被刀切；第 3 种一行、末尾有省略号；第 4 种与第 3 种外观相同。操作：把第 3、4 个 Text 的 maxLines 都改成 2 再看——第 3 种变为两行截断，第 4 种仍是一行（`softWrap = false` 关闭了换行，根本没有第二行）。

### 4.4 换行的基本规则

宽度不够时 Text 自动"软换行"：英文按单词边界断行，不会把一个单词劈成两半；中文几乎可以在任意相邻字符之间断行；具体断点由文本引擎按 Unicode 断行规则决定（教学简化）。`softWrap = false` 关闭软换行：整段文字按一行排布，宽度不足的部分交给 overflow 处理，常与 maxLines + Ellipsis 连用。不换行又不写 overflow 时，超宽文字直接被切出可见区域。

### 4.5 富文本只是预告

让同一段文字里的不同片段带不同样式（颜色、字重、下划线）属于另一项能力，由 `buildAnnotatedString` + `SpanStyle` 提供；本章的 `TextStyle` 只能作用于整段文字。本章不展开，第 61 章详解。

## 5. 实际执行过程

1. Text 把 style 与顶层参数合成最终 TextStyle：顶层参数覆盖同名字段，其余字段沿用 style
2. 若是富文本，先按样式区间拆分文字（这里的机制第 61 章再展开）
3. 文本测量器根据宽度和 softWrap 断行；softWrap = false 时按一行排
4. 行数达到 maxLines 上限后，剩余内容按 overflow 处理（Clip / Ellipsis / Visible）
5. lineHeight 决定每个行框的高度；sp 值经当前 Density 换算成像素（第 41 章）
6. 最终 TextStyle 的其余字段（字体族等）继续生效

## 6. Flutter / Dart 3 对照

Flutter 的 Text + TextStyle 与 Compose 非常接近；富文本（第 61 章）对应 Text.rich / TextSpan。Compose 的主题通过 MaterialTheme.typography 读取，顶层参数同样可以覆盖 style。

相似点：参数几乎一一对应——fontSize / fontWeight / fontStyle / color 同名同义；`maxLines` + `TextOverflow.ellipsis` 与 Compose 的 `maxLines` + `TextOverflow.Ellipsis` 名字和语义几乎一致；两边文字都默认跟随系统字体缩放（Flutter 的 TextScaler、Compose 的 fontScale）；两边都支持"从主题复制样式再局部覆盖"（copyWith 与 TextStyle.copy）。

不等价处（概念对照，不是机制等价）：Flutter 的 `TextStyle.height` 是倍率，实际行高 = fontSize × height（16 × 1.4 = 22.4）；Compose 的 `lineHeight` 是绝对 sp 值（对应写 `lineHeight = 22.4.sp`）。同一份设计稿换算时不能把 1.4 直接抄给 Compose。另外两边文本基线与行高的排版细节实现不同，跨端对齐行高时以真机预览为准。

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

- 只设置 overflow，不设置 maxLines，省略号不按预期出现（4.3 的处理顺序）
- 把 lineHeight 当成额外的上下 padding：多出的空间分布在每个行框内部，不是组件的外边距（4.2，教学简化）
- 在所有 Text 上硬编码颜色，破坏暗色主题
- 同时在 style 和顶层参数设置同一字段，却没意识到顶层参数会覆盖（4.1）
- 用 dp 表达字号，或为像素级还原设计稿手动除以 fontScale，破坏用户期望的字体缩放（4.2；教学工程的取舍见第 44、45 章）
- 以为设置了 lineHeight 就能避免截断：行高不参与"放不下"的判断，截断只看 maxLines

错误示例（故意错误，不参与编译）：

```kotlin
// 编译错误：fontSize 的类型是 TextUnit（sp/em），`.dp` 返回 Dp，类型不匹配。
// 就算手动换算成像素数值塞进来，dp 也不随系统字体缩放，无障碍上同样不成立（见 4.2）。
Text(
    text = "标题",
    fontSize = 16.dp,
)
```

## 8. 本章检查题

1. 项目中的 description 最终字号来自哪里？
2. fontSize 16sp、lineHeight 24sp 各控制什么？多出的 8sp 去了哪里？
3. 如果想把同一段文字里的几个字单独改成红色加粗，用本章的 `TextStyle` 能做到吗？需要哪一类能力？
4. 预测：一段需要三行才能完整显示的文字，写 `maxLines = 2`、`overflow = TextOverflow.Ellipsis`，最终显示几行？最后一行末尾是什么？
5. 预测：两处 `fontSize = 14.sp` 的文字，用户把系统字体大小调到最大后一处变大了、另一处没变——没变的那处可能是什么写法？
6. 动手：把 4.3 对照示例里第 3、4 个 Text 的 maxLines 都改成 2，运行后两者各显示几行？为什么不同？

答案：

1. 来自主题 bodyMedium 基础样式，再被全局 DemoThemeConfig 的 fontSize 和 lineHeight 覆盖，因此保留字体族等主题属性又统一调整了演示区字号。
2. fontSize 控制字形大小；lineHeight 控制每行占用的垂直高度；多出的 8sp 分布在每个行框内部文字的上下两侧（教学简化：首末行的精确分配还受 lineHeightStyle 等影响）。
3. 本章的 `TextStyle` 作用于整段文字，做不到只改几个字。想让局部片段变红加粗，要靠 `buildAnnotatedString` + `SpanStyle` 这类按片段设置样式的能力，具体写法见第 61 章。
4. 显示两行，第二行末尾是省略号；第三行的剩余内容被丢弃。
5. 先检查两处实际传入的字号与 LocalDensity：可能某处覆盖了 fontScale，或像项目的 `.pxSp()` 那样在换算时手动除以它（第 44 章）。后者只在线性模型下完全抵消；非线性转换下结果还可能缩小。使用相同系统 Density、保持数值不变的 14.sp 应按字体曲线响应系统设置。
6. 第 3 个变为两行截断（软换行仍开启，第二行末尾省略号）；第 4 个仍是一行——`softWrap = false` 关闭了换行，maxLines 再大也没有第二行可显示。

## 9. 本章总结

Text 的最终外观由主题 style 与顶层覆盖参数共同决定。字号、行高、字重和溢出是本章的四项独立能力，应分别配置；maxLines 与 overflow 是截断两件套，只写其中一个得不到省略号；fontSize 用 sp 是让文字跟随系统字体缩放的常规做法，dp 表达不了文字单位，也不应把字号固定死在设计稿像素（教学工程 `.pxSp()` 的取舍见第 41、44、45 章）。富文本是另一项能力，第 61 章单独讲。

官方参考：

- [Text in Compose（文本官方指南）](https://developer.android.com/develop/ui/compose/text)
- [TextOverflow API 参考](https://developer.android.com/reference/kotlin/androidx/compose/ui/text/style/TextOverflow)
- [资源类型 Dimension（sp 的定义）](https://developer.android.com/guide/topics/resources/more-resources)
