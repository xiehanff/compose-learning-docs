# 第 15 章：RoundedCornerShape、background、clip 与 border

## 1. 本章目标

理解形状、背景、裁剪和边框是四个不同步骤，并通过 Modifier 顺序控制最终绘制范围。

学完后，你应当能够：

- 使用 RoundedCornerShape 创建圆角形状
- 区分 background 的形状参数与 clip 的真实裁剪
- 理解 border 只绘制边界
- 通过顺序决定 padding、背景和裁剪范围

## 2. 项目中的位置

- `components/ContainerDemo.kt`
- `components/ImageDemo.kt`
- `ui/detail/ComponentDetailScreen.kt`



## 3. 项目源码片段

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .background(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.px),
        )
        .border(
            width = 1.px,
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(12.px),
        )
        .padding(16.px),
)

Image(
    modifier = Modifier.clip(RoundedCornerShape(12.px)),
    /* ... */
)
```

## 4. 逐步解释

`RoundedCornerShape` 只是一个形状描述，不会独立产生任何视觉效果。把它传给 `background`，背景会按圆角形状绘制，但子内容不一定被裁掉；把它传给 `clip`，后续绘制内容会被限制在形状内；把它传给 `border`，边界会沿形状描边。

因此圆角背景和圆角图片需求不同。纯色容器可直接使用 background(shape=...)；图片必须 clip 才能裁掉四角。若同时使用 background、border、padding，应根据需要确定哪一层覆盖外部范围。

## 5. 实际执行过程

1. Modifier 链确定各绘制节点顺序
2. background 在指定范围绘制颜色
3. clip 建立后续绘制裁剪区
4. border 沿形状边缘绘制描边
5. padding 改变内部内容位置

## 6. Flutter / Dart 3 对照

Flutter 常通过 BoxDecoration 同时描述 color、border、borderRadius，但对子图片的实际裁剪通常仍要 ClipRRect。Compose 将这些拆为独立 Modifier，组合更灵活，也更要求理解顺序。

```dart
ClipRRect(
  borderRadius: BorderRadius.circular(12),
  child: Container(
    decoration: BoxDecoration(
      color: Colors.blue.shade50,
      border: Border.all(color: Colors.blue),
      borderRadius: BorderRadius.circular(12),
    ),
    padding: const EdgeInsets.all(16),
    child: const Text('内容'),
  ),
);
```

## 7. 常见错误

- 给 background 设置圆角后，以为内部图片也必然被裁剪
- border 与 background 使用不同 shape，导致边缘不吻合
- 忽略 Modifier 顺序，造成点击区或阴影范围错误
- 把 RoundedCornerShape 当成 dp 数值直接使用

## 8. 本章检查题

1. background(shape) 与 clip(shape) 的区别是什么？
2. 圆角图片最关键的 Modifier 是哪一个？
3. 为什么 border 与 background 通常应使用相同 shape？

答案：

1. background(shape) 按圆角形状绘制背景，但子内容不一定被裁剪；clip(shape) 把后续绘制内容限制在形状内。
2. clip(RoundedCornerShape(...))。图片必须被裁剪才能真正去掉四角。
3. 描边与背景沿同一形状绘制才能对齐，否则圆角边缘不吻合。

## 9. 本章总结

Shape 只描述几何形状；background、clip、border 分别负责填充、裁剪和描边。Compose 的组合式设计要求明确每一步做什么以及按什么顺序执行。
