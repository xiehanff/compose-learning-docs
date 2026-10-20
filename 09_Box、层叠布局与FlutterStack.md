# 第 9 章：Box、层叠布局与 Flutter Stack

先看两个真实问题：

1. 想让图标右上角挂一个数字角标，Column 和 Row 都只能把角标「排」到图标旁边，怎么都「叠」不上去。
2. 一个可点击的浮层盖在可点击的内容上，点重叠区域时永远触发的是下面那层的逻辑。

这两个问题都指向同一件事：Column / Row 沿一个方向依次排开子项，而有些界面需要多个子项**共享同一块区域**。Box 就是干这个的：子项在同一块父区域内分别测量和放置，可以相互覆盖。

## 1. 本章目标

理解 Box 的两个用途：作为单子项容器提供对齐，也作为多子项层叠容器实现覆盖布局。

学完后，你应当能够：

- 知道 Box 子项默认可以重叠
- 使用 contentAlignment 设置默认位置
- 使用 Modifier.align 单独定位子项
- 理解后写子项通常绘制在先写子项上方
- 预测重叠区域上的点击会命中哪一层

## 2. 项目中的位置

- `components/BoxDemo.kt`
- `components/IndexedStackDemo.kt`



## 3. 项目源码片段

<!-- compile -->
```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(180.px),
) {
    Box(Modifier.matchParentSize().background(Color.LightGray))
    Text("左上", Modifier.align(Alignment.TopStart))
    Text("居中", Modifier.align(Alignment.Center))
    Text("右下", Modifier.align(Alignment.BottomEnd))
}
```

（原 Demo 使用项目自定义 `.px` 单位；标准工程写 `.dp`。）第一个子项的 `matchParentSize` 是 Box 内容作用域里特有的修饰符：它让这个子项铺满整个 Box，但**不参与 Box 自身尺寸的计算**——Box 的大小由其他子项（这里是三行文字）决定，灰底只是跟着铺满。这与 `fillMaxSize` 不同：后者填满的是父约束允许的最大空间，并参与常规测量。

## 4. 逐步解释

Box 不会像 Column 或 Row 那样沿某个方向依次排开子项。多个子项会在同一块父区域内分别测量和放置，因此可以相互覆盖。`contentAlignment` 为所有没有单独指定位置的子项提供默认对齐；`Modifier.align` 则只改变当前子项。

绘制顺序通常与声明顺序一致：后声明的子项画在更上层。需要显式改变层级时可使用 `zIndex`。项目的 IndexedStack 示例正是把三个面板都放进同一个 Box，再通过 zIndex 控制可见层。

### 4.1 contentAlignment 管整体默认，子项 Modifier.align 管单个例外

`contentAlignment` 是写在 Box 参数里的「全组默认」；`Modifier.align` 是写在某个子项上的「单人覆盖」，两者同时存在时子项自己的 `align` 赢。

<!-- compile -->
```kotlin
Box(
    modifier = Modifier.fillMaxWidth().height(160.dp),
    contentAlignment = Alignment.Center, // 默认：没有单独 align 的子项都居中
) {
    // 灰块与下面这行文字都跟随默认 → 都在 Box 正中（文字叠在灰块上）
    Box(Modifier.size(120.dp).background(Color(0xFFBDBDBD)))
    Text("跟随默认：居中", color = Color.Black)
    // 单独覆盖的两个例外
    Text("单独指定：左上角", Modifier.align(Alignment.TopStart))
    Text("单独指定：右下角", Modifier.align(Alignment.BottomEnd))
}
```

初始界面：一个 160dp 高的区域，中央一个 120dp 灰块，灰块上叠着「跟随默认：居中」；左上角与右下角各一行小字。预期观察：改 `contentAlignment` 只影响前两个子项的位置，后两行文字纹丝不动——它们只认自己的 `align`。注意 `Modifier.align` 是 Box 内容作用域（BoxScope）提供的修饰符，和第 7、8 章的 `weight` 一样只在 Box 的内容 lambda 里可用。

### 4.2 叠放次序与命中：后写的在上层，上层先接收点击

绘制按声明顺序进行，后声明的子项画在先声明的上面；点击事件也顺着这个层级分发：**重叠区域上，绘制在上层的子项优先接收点击**。用一个可交互的例子验证（计数写法 `by remember` 在第 7 章 4.3 节讲过）：

<!-- compile -->
```kotlin
var hitResult by remember { mutableStateOf("点击两块色块试试") }

Box(
    modifier = Modifier.fillMaxWidth().height(160.dp),
    contentAlignment = Alignment.Center,
) {
    // 先声明：底层蓝色块，靠左（宽 140dp，会伸进中间）
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .size(width = 140.dp, height = 80.dp)
            .background(Color(0xFF64B5F6))
            .clickable { hitResult = "命中：底层蓝色块" },
    )
    // 后声明：上层红色块，居中——与蓝块在中间重叠，绘制在上、优先接收点击
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(width = 140.dp, height = 80.dp)
            .background(Color(0xFFE57373))
            .clickable { hitResult = "命中：上层红色块" },
    )
}

Text(text = hitResult, modifier = Modifier.padding(top = 8.dp))
```

初始界面：中间一行 160dp 高的区域，蓝色块靠左、红色块居中，两者在中间有一段重叠；区域下方显示提示文字。操作步骤与预期观察：

1. 点击红色块：显示「命中：上层红色块」。
2. 点击蓝色块未与红色重叠的左侧部分：显示「命中：底层蓝色块」。
3. 点击两块重叠的中间部分：显示「命中：上层红色块」——红色后声明、画在上面，点击优先分给它。

这条规则的实际影响：遮罩层必须声明在内容**之后**（或用更高的 `zIndex`），否则盖不住；反过来，如果盖在上面的层不希望拦截点击，就不能随手给它加 `clickable`，需要借助 `pointerInput` 与 `PointerEventPass` 控制事件的分发与消费（第 65 章手势章节再展开）。

### 4.3 实战：图标 + 右上角数字角标

把「对齐 + 叠放」组合起来，就是最常见的角标样式：图标居中，角标固定在右上角，后声明所以画在图标之上。

<!-- compile -->
```kotlin
Box(modifier = Modifier.size(56.dp)) {
    // 底层：通知图标，单独指定居中
    Icon(
        imageVector = Icons.Filled.Notifications,
        contentDescription = "通知", // 无障碍朗读用，第 80 章详解
        modifier = Modifier.align(Alignment.Center).size(32.dp),
    )
    // 上层：右上角数字角标。后声明 → 画在图标之上
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .size(20.dp)
            .background(Color(0xFFE53935), CircleShape), // 红色圆形底
        contentAlignment = Alignment.Center,             // 数字在圆心里居中
    ) {
        Text(text = "3", color = Color.White, fontSize = 12.sp)
    }
}
```

初始界面：一个 56dp 见方的区域，中央一个通知图标，右上角一个 20dp 红色圆，圆心里是白色数字 3。预期观察：角标固定贴住 Box 的右上角，不会因为图标大小变化而移动——因为它对齐的是整个 Box，不是图标。改数字为两位数（如 12）时 20dp 圆会显得挤，实践中常改用 `defaultMinSize(minWidth = 20.dp)` 一类的写法让圆随文字变宽，属于第 13 章之后的练习。

## 5. 实际执行过程

1. Box 接收父约束
2. 分别测量所有子项
3. 根据 contentAlignment 或子项 align 计算位置
4. 按声明顺序绘制
5. zIndex 较高的节点显示在上层

（教学简化：Box 自身的大小默认取「最大的那个子项」，`matchParentSize` 的子项除外——它铺满 Box 但不参与大小决定；这正是第 3 节灰底示例的机制。）

## 6. Flutter / Dart 3 对照

Flutter 中最接近的是 `Stack`。`Alignment` 对应 Stack 的 alignment，`Positioned` 更接近 Compose 的 offset 或自定义布局；Compose 的 `Modifier.align` 只能在 BoxScope 中使用。

```dart
Stack(
  alignment: Alignment.center,
  children: const [
    Positioned(top: 0, left: 0, child: Text('左上')),
    Text('居中'),
    Positioned(right: 0, bottom: 0, child: Text('右下')),
  ],
);
```

相似点：`alignment` 与 `contentAlignment` 一样为未定位子项提供默认对齐；两者都按 children 声明顺序绘制，后写的在上层。不等价处至少有两点：其一，Flutter 的 `Positioned` 用 `top/left/right/bottom` 给出的是**任意坐标**定位，Compose 的 `Modifier.align` 只能从九个对齐点（TopStart、Center、BottomEnd 等）里选，任意坐标要用 `offset` 或自定义布局；其二，尺寸计算规则不同——`Stack` 决定自身大小时忽略 `Positioned` 子项，只看非定位子项，而 Box 的大小由所有子项（`matchParentSize` 除外）共同决定，没有「定位子项不参与测量」的区分。概念可以对照，机制并不等价。

## 7. 常见错误

- 把 Box 当成 Flutter Container；Box 主要解决布局，不自带完整 decoration 参数
- 忘记固定或填充 Box 尺寸，导致可用于对齐的空间很小
- 在 BoxScope 外调用 Modifier.align
- 只用半透明遮盖未选中面板，却没有处理其点击事件

错误示例（故意错误，不参与编译）：

```kotlin
// align 是 BoxScope 的成员，出了 Box 的内容 lambda 就拿不到
Column {
    Text("hi", Modifier.align(Alignment.TopStart)) // 编译报错：Unresolved reference: align
}
```

## 8. 本章检查题

1. Box 和 Column 在放置多个子项时最大的区别是什么？
2. 为什么 Modifier.align 只在 Box 的 content 中可用？
3. 如何确保遮罩层显示在内容之上？
4. 预测：4.2 节演示里把红色块的声明挪到蓝色块**之前**（其他不变），点击重叠区域会显示什么？为什么？
5. 动手：把 4.3 节角标例子中的角标从右上角改到左下角，并把圆形换成 4dp 圆角的方形（提示：`Alignment.BottomStart` 与 `RoundedCornerShape(4.dp)`），先预测位置再运行验证。

答案：

1. Box 不沿某个方向依次排开子项，多个子项在同一块父区域内分别测量和放置，可以相互覆盖；Column 依次垂直排开。
2. align 是 BoxScope 提供的作用域 Modifier，只在 Box 的 content 中可用，用于给单个子项指定对齐。
3. 把遮罩子项声明在内容之后（绘制顺序通常与声明顺序一致），或显式使用 zIndex 提高层级。
4. 显示「命中：底层蓝色块」。红色先声明时绘制在蓝色之下，重叠区域上层变成蓝色块，点击优先分给绘制在上层的蓝色块。
5. 角标出现在整个 Box 的左下角，圆角方形红底内数字居中；其余不变。

## 9. 本章总结

Box 是 Compose 的基础层叠容器，对应 Flutter Stack 的核心能力。它既能统一对齐子项，也能让多个子项共享同一空间并控制层级。三条规则记牢：`contentAlignment` 是默认，子项 `Modifier.align` 是覆盖；后声明的绘制在上层；重叠区域的点击上层优先。

官方参考：[Compose 布局基础](https://developer.android.com/develop/ui/compose/layouts/basics)、[Alignment API 参考](https://developer.android.com/reference/kotlin/androidx/compose/ui/Alignment)。
