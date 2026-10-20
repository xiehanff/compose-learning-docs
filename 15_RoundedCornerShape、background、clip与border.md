# 第 15 章：RoundedCornerShape、background、clip 与 border

## 1. 本章目标

先看两个真实问题。其一：给卡片写了圆角背景 `background(color, RoundedCornerShape(16.dp))`，里面放的图片却把四个方角顶出来，圆角"失效"了。其二：border 和 background 的圆角一个 16dp 一个 8dp，边缘对不齐还找不到原因。两个问题的根因相同：形状、画背景、裁内容、描边是四件独立的事，谁也不附带谁。

理解形状、背景、裁剪和边框是四个不同步骤，并通过 Modifier 顺序控制最终绘制范围。

学完后，你应当能够：

- 使用 RoundedCornerShape 创建圆角形状（dp、百分比、单角三种构造）
- 区分 background 的形状参数与 clip 的真实裁剪
- 理解 border 只绘制边界、不参与布局尺寸
- 通过顺序决定 padding、背景和裁剪范围
- 复现并解释"圆角背景 + 方角内容溢出"，知道圆角图片必须 clip

## 2. 项目中的位置

- `components/ContainerDemo.kt`
- `components/ImageDemo.kt`
- `ui/detail/ComponentDetailScreen.kt`



## 3. 项目源码片段

<!-- compile
prelude: val imageBitmap: ImageBitmap = ImageBitmap(4, 4)
-->
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
    bitmap = imageBitmap,
    contentDescription = "圆角图片",
    modifier = Modifier.clip(RoundedCornerShape(12.px)),
)
```

项目节选，原项目用 `.px` 设计稿单位（第 43、44 章）；`imageBitmap` 为编译校验用的占位空位图，真实圆角图片效果需自备资源（见 4.3）。

### 3.1 独立示例：不 clip vs 先 clip 对照

参与编译校验。按图片资源类示例的约定，用色块代替图片演示裁剪。初始界面：左右两个 96dp 的灰色"图框"。

<!-- compile -->
```kotlin
val shape = RoundedCornerShape(16.dp)

Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
    // 左：只有 background(shape)——背景是圆角，但内层方角内容把四角顶出去
    Box(Modifier.size(96.dp).background(Color(0xFFBDBDBD), shape)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF42A5F5)))
    }
    // 右：先 clip(shape)——内层所有绘制被限制在圆角内
    Box(Modifier.size(96.dp).clip(shape).background(Color(0xFFBDBDBD), shape)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF42A5F5)))
    }
}
```

预期观察：左边看起来是一个 96dp 的蓝色方块——方角，蓝色盖住灰底、四角溢出圆角边界；右边是圆角的蓝色块——四角被裁掉，透出页面底色。"圆角背景"不等于"圆角内容"。

## 4. 逐步解释

### 4.1 Shape 只是几何描述

`RoundedCornerShape` 只是一个形状描述，不会独立产生任何视觉效果。把它传给 `background`，背景会按圆角形状绘制，但子内容不一定被裁掉；把它传给 `clip`，后续绘制内容会被限制在形状内；把它传给 `border`，边界会沿形状描边。

三种常用构造（参与编译校验）。初始界面：纵向三个 96dp 色块。

<!-- compile -->
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    // 半径 16dp 的圆角
    Box(Modifier.size(96.dp).background(Color(0xFF90CAF9), RoundedCornerShape(16.dp)))
    // 百分比 50：圆角半径 = 短边的 50%
    Box(Modifier.size(96.dp).background(Color(0xFFA5D6A7), RoundedCornerShape(50)))
    // 只圆两个角：topStart 与 bottomEnd
    Box(
        Modifier.size(96.dp).background(
            Color(0xFFCE93D8),
            RoundedCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
        )
    )
}
```

预期观察：第一块普通圆角；第二块在方形块上近似圆形、在长方形块上呈胶囊形；第三块只有左上、右下有圆角。

首次出现的 Kotlin 语法：Kotlin 按参数类型选择重载——传 `Dp`（16.dp）按"圆角半径"解释，传 `Int`（50）按"短边百分比"解释，写错类型语义完全不同：`RoundedCornerShape(16)` 是 16% 圆角而不是 16dp 圆角，这是高频笔误（见第 7 节）。

### 4.2 四个角色的分工与顺序

- `background(color, shape)`：只画一层背景。shape 决定这块颜色的形状；它不裁剪任何子内容，也不影响布局尺寸。
- `clip(shape)`：从它所在的位置起，把"内侧"的所有绘制（后续背景、子组件内容、再往后的 border）裁剪到形状内。不改变布局尺寸。
- `border(width, color, shape)`：沿形状边缘描一圈边，只负责绘制，不把内容往外挤、不参与布局尺寸计算——盒子该多大还多大，粗描边会向内压住内容边缘的视觉（与 Flutter 的差异见第 6 节）。
- 顺序规则沿用第 7 章：Modifier 链从外到内，clip 只影响写在它之后的绘制。`.clip(shape).background(color)` 与 `.background(color, shape)` 在"背景形状"上效果相同，但前者还会裁剪子内容，后者不会——圆角容器需要裁内容时必须显式 clip。

因此圆角背景和圆角图片需求不同。纯色容器可直接使用 background(shape=...)；图片必须 clip 才能裁掉四角。若同时使用 background、border、padding，应根据需要确定哪一层覆盖外部范围。

### 4.3 圆角图片：真图版

需自备资源，不参与编译校验（在 `res/drawable` 放一张 `photo.jpg`）：

```kotlin
Image(
    painter = painterResource(R.drawable.photo),
    contentDescription = "圆角封面图",
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .clip(RoundedCornerShape(12.dp)), // 去掉 clip：图片方角会顶出圆角（见 3.1 左侧）
)
```

3.1 的色块对照等价于这里"有 clip / 无 clip"的差异：Image 的内容是绘制层，只有 clip 能把它裁进圆角；background 与组件自身的 shape 参数（如 Card 的 shape）都代替不了这一步。

### 4.4 背景与边框：形状一致才对齐

border 沿自己的 shape 描边，background 沿自己的 shape 填色：两个形状一致时边缘重合；不一致（半径不同、一个圆角一个直角）时肉眼可见地错位。边框宽度也不改变布局：`border(4.dp)` 不会让盒子变大 8dp——描边画在组件边界附近，粗边框会侵占内容边缘的视觉空间（描边落点细节以当前版本实现为准，教学简化：按"贴着形状边缘画"理解）。

错误示例（故意错误，不参与编译）——两个形状半径不同，边框和背景边缘对不齐：

```kotlin
Box(
    Modifier
        .size(96.dp)
        .background(Color(0xFFBDBDBD), RoundedCornerShape(16.dp))
        .border(1.dp, Color.Red, RoundedCornerShape(8.dp)), // 半径与背景不同：边缘不吻合
)
```

### 4.5 clip 的边界：只管绘制，不管点击热区

clip 裁剪的是绘制；布局尺寸与点击热区不因 clip 变小——把 clickable 写在 clip 之内，可点击区域仍按布局边界（矩形）计算（命中测试细节在第 64、65 章展开，此处先记住现象）。所以"视觉圆角 + 圆角热区"一致的需求不能只靠 clip，进阶做法在自定义 Modifier 与手势章节讨论。

## 5. 实际执行过程

1. Modifier 链确定各绘制节点顺序：先写的在外层
2. background 在指定范围按 shape 绘制颜色（只画"一层底"，不裁内容）
3. clip 建立后续绘制裁剪区：其后所有绘制超出形状的部分不显示
4. border 沿形状边缘绘制描边
5. padding 改变内部内容位置（第 13 章）
6. 点击热区与无障碍语义按布局边界工作，不受 clip 影响

## 6. Flutter / Dart 3 对照

Flutter 常通过 BoxDecoration 同时描述 color、border、borderRadius，但对子图片的实际裁剪通常仍要 ClipRRect。Compose 将这些拆为独立 Modifier，组合更灵活，也更要求理解顺序。

相似点：BoxDecoration（color + border + borderRadius）的组合约等于 background + border 用同一个 shape；ClipRRect 对应 Modifier.clip(shape)；"圆角容器里放图片仍需显式裁剪"两边一致——Flutter 的 Container 圆角同样裁不掉 Image，要 ClipRRect。

不等价处（概念对照，不是机制等价）：一，Flutter 的 Container 边框参与尺寸计算（Container 自身尺寸 = child + border + padding），Compose 的 border 只是绘制层，不改布局尺寸；二，BoxDecoration 是"一张表单同时描述"，Compose 拆成三处传 shape，形状不匹配时两边都表现为视觉错位，但 Compose 还叠加了链上顺序语义；三，clip 的作用范围表达不同——Flutter 靠包装层级（ClipRRect 包住谁就裁谁），Compose 靠链上位置（clip 影响其后所有绘制）。

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

- 给 background 设置圆角后，以为内部图片也必然被裁剪（3.1 左侧：必须显式 clip）
- border 与 background 使用不同 shape，导致边缘不吻合（4.4）
- 忽略 Modifier 顺序，造成点击区或阴影范围错误（第 7 章、4.5）
- 把 RoundedCornerShape 当成 dp 数值直接使用：`RoundedCornerShape(16)` 是 16% 圆角（百分比），要 dp 必须写 `RoundedCornerShape(16.dp)`（4.1）
- 以为 border 会让组件变大：border 不参与布局尺寸（4.4）
- 把 clip 当成裁剪点击热区：它只裁绘制，热区仍按布局边界（4.5）

## 8. 本章检查题

1. background(shape) 与 clip(shape) 的区别是什么？
2. 圆角图片最关键的 Modifier 是哪一个？
3. 为什么 border 与 background 通常应使用相同 shape？
4. 预测：把 3.1 右侧 Box 改成 `.background(Color.Gray, shape).clip(shape)`（clip 挪到 background 之后），内层蓝色还会被裁成圆角吗？灰底呢？
5. 预测：`RoundedCornerShape(50)` 用在 200dp × 80dp 的色块上，四角大概什么形状？
6. 动手：给 4.1 第三个色块加 `border(2.dp, Color.Black)`（不传 shape），观察描边形状；再给 border 传一个与 background 不同的半径，观察错位。

答案：

1. background(shape) 按圆角形状绘制背景，但子内容不一定被裁剪；clip(shape) 把后续绘制内容限制在形状内。
2. clip(RoundedCornerShape(...))。图片必须被裁剪才能真正去掉四角。
3. 描边与背景沿同一形状绘制才能对齐，否则圆角边缘不吻合。
4. 内层蓝色仍被裁（蓝色绘制发生在 clip 之后）；灰底不经过这个 clip，但 background 自带同一 shape，本来就是圆角——视觉效果几乎不变。若 background 不带 shape（直角灰底），灰底的四个角会留在圆角裁剪之外，形成"圆角蓝 + 方角灰"的分层，这正是"clip 只影响其后"的顺序规则。
5. 胶囊形：圆角半径 = 短边 80dp 的 50% = 40dp，左右两端近似半圆。
6. border 不传 shape 时用默认的直角形状，叠在圆角色块上四个角会突出圆角外；传不同半径则描边与背景边缘明显错位。

## 9. 本章总结

Shape 只描述几何形状；background、clip、border 分别负责填充、裁剪和描边：background(shape) 只画自己的底色，clip 影响其后所有绘制的可见范围，border 沿形状描边且不参与布局尺寸。圆角背景不裁内容——圆角图片必须 clip；背景与边框形状一致才对齐。Compose 的组合式设计要求明确每一步做什么以及按什么顺序执行。

官方参考：

- [RoundedCornerShape API 参考](https://developer.android.com/reference/kotlin/androidx/compose/foundation/shape/RoundedCornerShape)
- [Compose layout basics（布局与 Modifier 顺序）](https://developer.android.com/develop/ui/compose/layouts/basics)
