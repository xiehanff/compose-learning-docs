# 第 61 章：富文本：AnnotatedString、Span 与文本绘制

## 1. 本章目标

上一章之前我们写的 Text 都是一整段同一样式的文字。现实中经常遇到「一段文字里，关键字加粗变色、某段过期文案加删除线、协议链接可点击」的需求。本章解决这个问题。

学完后，你应当能够：

- 用 `buildAnnotatedString` 构造一个文本、多种样式的富文本
- 区分 `SpanStyle`（字符级）与 `ParagraphStyle`（段落级）的职责
- 用 `ClickableText` 配合 `LinkAnnotation` 实现局部文字点击
- 知道 `AnnotatedString` 的不可变性与合并方式

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.text.AnnotatedString`
- `androidx.compose.ui.text.buildAnnotatedString`
- `androidx.compose.ui.text.SpanStyle` / `ParagraphStyle`
- `androidx.compose.ui.text.LinkAnnotation`
- `androidx.compose.foundation.text.ClickableText`

## 3. 项目源码片段

场景：登录页的《用户协议》文案——「重要提示」红色加粗，旧版条款加删除线，「完整协议」可点击跳转。

```kotlin
@Composable
fun RichTextDemo() {
    val agreement = buildAnnotatedString {
        withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.Bold)) {
            append("重要提示")
        }
        append("：本协议自 ")
        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            append("2023 版")
        }
        append(" 起生效。查看")
        withStyle(
            style = SpanStyle(color = Color(0xFF3F6FF6)),
            link = LinkAnnotation.Url(
                url = "https://example.com/agreement",
                styles = TextLinkStyles(
                    style = SpanStyle(textDecoration = TextDecoration.Underline),
                ),
            ),
        ) {
            append("完整协议")
        }
    }

    ClickableText(
        text = agreement,
        onClick = { offset ->
            agreement.getLinkAnnotations(offset, offset).forEach { range ->
                val url = (range.item as LinkAnnotation.Url).url
                // 这里用 UriHandler / Intent 打开链接
            }
        },
    )
}
```

## 4. 逐步解释

`AnnotatedString` 由三部分组成：`text`（纯文本字符串）、`spanStyles`（字符级样式区间列表）、`paragraphStyles`（段落级样式区间列表）。后两者都是 `Range<T>` 列表，每个 Range 记录 `start`、`end` 偏移和 `item` 样式。所以 AnnotatedString 本质是「一个字符串 + 一堆带起止偏移的样式标注」，布局时按偏移切分文本块，再逐块应用样式。

`buildAnnotatedString` 是它的构建器 DSL：`append` 追加文字，`withStyle` 打开一个样式作用域。要点是 withStyle 是「lambda 作用域」——块内 append 的文字才带该样式，块一退出样式立即结束，不会串到后面的文字。这比手写 Range 的 start/end 安全得多。

`SpanStyle` 控制字符级样式：字体、字号、字重、颜色、背景、删除线/下划线等，作用于文字本身。`ParagraphStyle` 控制段落级样式：对齐、行高、缩进、间距，作用于整段文本的排布。Text 的 `style` 参数（TextStyle）和这里不同，别混淆。

`AnnotatedString` 是不可变的。需要组合两段富文本时用 `+` 运算符合并（内部会做偏移拼接），要改样式只能重新 build 或合并，不能原地修改。

可点击文字：`ClickableText` 的 `onClick` 回调会返回被点击位置的字符偏移 `offset`，配合 `text.getLinkAnnotations(offset, offset)` 查找该偏移处命中的 `LinkAnnotation`，取到 Url 后执行跳转。跳转本身（UriHandler、Intent）由业务层决定，Compose 只负责把「点击位置」翻译成「文本偏移」。

顺带一提：TextField 的 `VisualTransformation` 与 AnnotatedString 关系密切——`PasswordVisualTransformation` 就是把输入文本重写成一个 `AnnotatedString` 再交给绘制层，可见富文本机制是文本渲染的基础设施。

记住：**富文本 = 文本 + 样式区间 + 链接区间，用 buildAnnotatedString 声明，用 ClickableText 交互。**

## 5. 实际执行过程

1. 组合阶段执行 `buildAnnotatedString`，生成 text 与带偏移的样式/链接区间
2. Text 布局阶段按 Range 把文本拆成多个文本块，分别测量
3. 绘制阶段逐块应用字体、颜色、装饰，输出整段文字
4. 用户点击文字，ClickableText 把点击坐标映射为字符偏移 offset
5. `getLinkAnnotations(offset, offset)` 查命中链接，回调中执行跳转

## 6. Flutter / Dart 3 对照

先讲相似点：两边都把富文本建模成一棵「文本 + 样式」树，局部样式都是声明式的。

```dart
Text.rich(
  TextSpan(
    children: [
      const TextSpan(
        text: '重要提示',
        style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold),
      ),
      const TextSpan(text: '：本协议自 '),
      const TextSpan(
        text: '2023 版',
        style: TextStyle(decoration: TextDecoration.lineThrough),
      ),
      TextSpan(
        text: ' 起生效。查看完整协议',
        style: const TextStyle(color: Colors.blue),
        recognizer: TapGestureRecognizer()..onTap = () => _openUrl(),
      ),
    ],
  ),
)
```

本质区别：Flutter 用嵌套的 `TextSpan` widget 树表达样式，点击靠 `recognizer` 直接注册回调；Compose 用**不可变数据 + 偏移区间**表达，文本与样式是分离的两层数据，点击通过「偏移查找」而非回调对象。前者的样式天然嵌套，后者的样式可被程序化地合并、检索（比如 getLinkAnnotations 查区间），更接近数据驱动。

记住：**Compose 富文本是数据，Flutter 富文本是 widget 树。**

## 7. 常见错误

1. 把需要样式的文字写在 `withStyle` 块**外面**：withStyle 只作用于块内 append 的文字，块外 append 完全不受影响——结果「样式丢失」。反过来也不要以为样式会延续到块外，它不会。
2. `SpanStyle` 与 `TextStyle` 混淆：`Text(style = ...)` 参数要 `TextStyle`，`withStyle(...)` 参数要 `SpanStyle`，两者字段相似但类型不同。需要转换时用 `TextStyle.toSpanStyle()`。
3. 用普通 String + 多个 `Text` 拼接实现富文本：多个 Text 无法按文字流自动换行与基线对齐，点击区域也被割裂成多个组件。应合并进**一个** `AnnotatedString` + 一个 Text。
4. 忘了 `AnnotatedString` 不可变：想直接改原对象里的样式——不行，要么重新 build，要么用 `+` 合并生成新对象。

## 8. 本章检查题

1. `AnnotatedString` 由哪三部分组成？
2. `withStyle` 的样式作用范围是什么？离开块后样式会延续吗？
3. 如何实现一段文本中的局部可点击？
4. `SpanStyle` 与 `ParagraphStyle` 分别控制什么？

答案：

1. `text`（纯文本）、`spanStyles`（字符级样式区间）、`paragraphStyles`（段落级样式区间）。
2. 只作用于块内 append 的文字；`withStyle` 返回即自动结束，不会延续到后面的 append。
3. 在 `buildAnnotatedString` 中为目标片段包上 `LinkAnnotation.Url`，再用 `ClickableText` 的 `onClick` 拿到字符偏移，`getLinkAnnotations(offset, offset)` 查命中链接后执行跳转。
4. `SpanStyle` 控制字符级样式（字体、字号、颜色、背景、装饰线）；`ParagraphStyle` 控制段落级样式（对齐、行高、缩进）。

## 9. 本章总结

富文本的载体是 `AnnotatedString`：一个文本字符串加上带起止偏移的样式区间与链接区间。用 `buildAnnotatedString` 的 `withStyle` 声明样式最安全，局部点击则交给 `ClickableText` 的偏移回调 + `LinkAnnotation` 完成。掌握这三块，协议文案、聊天消息、搜索结果高亮等富文本场景都能覆盖。
