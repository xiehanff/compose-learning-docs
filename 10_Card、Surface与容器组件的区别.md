# 第 10 章：Card、Surface 与容器组件的区别

## 1. 本章目标

分清 Card、Surface、Box 以及“Flutter Container 等价组合”各自负责的问题。

学完后，你应当能够：

- 理解 Surface 是 Material 语义容器
- 理解 Card 是带卡片默认样式和交互语义的 Surface 风格组件
- 知道 Compose 没有一个包办所有装饰的 Container
- 根据场景选择 Box、Surface 或 Card

## 2. 项目中的位置

- `ui/home/HomeScreen.kt`
- `components/SurfaceDemo.kt`
- `components/ContainerDemo.kt`



## 3. 项目源码片段

```kotlin
Card(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth(),
) {
    Row(Modifier.fillMaxWidth().padding(16.px)) {
        Text(info.title)
    }
}

Surface(
    shape = RoundedCornerShape(12.px),
    color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Text("主题化容器", Modifier.padding(16.px))
}
```

## 4. 逐步解释

`Surface` 代表 Material 表面，集中处理 color、contentColor、shape、tonalElevation、shadowElevation 等主题化属性。`Card` 是更具体的卡片组件，拥有 CardDefaults，并可直接使用 `onClick` 版本获得正确的交互和语义。

Compose 没有 Flutter 那种同时提供尺寸、边距、背景、圆角、边框、对齐的单一 `Container`。项目的 ContainerDemo 使用 Box 加 Modifier 链组合这些能力。简单层叠或对齐选 Box；需要 Material 背景和内容色传播选 Surface；表达一张信息卡片并需要点击选 Card。

## 5. 实际执行过程

1. 选择容器语义
2. 容器读取 MaterialTheme 默认值
3. 测量并布局 content
4. 绘制背景、形状和 elevation
5. 可点击版本再处理交互与无障碍语义

## 6. Flutter / Dart 3 对照

Flutter 的 Card 是 Material 卡片；Container 是通用装饰盒。Compose 的 Surface 更接近“有 Material 语义的基础表面”，而 Box + Modifier 更接近 Container 的组合式实现。

```dart
Card(
  child: InkWell(
    onTap: onTap,
    child: const Padding(
      padding: EdgeInsets.all(16),
      child: Text('卡片'),
    ),
  ),
);
```

## 7. 常见错误

- 为了点击效果在 Card 外再套 clickable，造成重复点击语义
- 用 Surface 表达纯布局，却不需要任何 Material 属性
- 认为 Card 的外边距由 Card 自带；外边距仍需 Modifier.padding
- 把 Box、Surface、Card 当成仅名称不同的同一个组件

## 8. 本章检查题

1. 首页 EntryCard 为什么使用 Card(onClick) 而不是 Box.clickable？
2. Surface 的 contentColor 有什么价值？
3. Flutter Container 在 Compose 中通常由哪些能力组合？

答案：

1. Card(onClick) 自带 Material 卡片语义、正确的交互和涟漪反馈，以及 CardDefaults 默认样式；Box.clickable 只有点击行为，没有卡片语义和 Material 表面样式。
2. contentColor 会向子树传播与表面背景形成对比的内容颜色，保证表面上的文字、图标可读性。
3. Box + Modifier 链（size、padding、background、border、clip、align 等）。

## 9. 本章总结

Box 解决基础布局，Surface 解决 Material 表面，Card 表达卡片语义。选择组件时先看语义和默认行为，而不是只看视觉是否都是“一个矩形”。
