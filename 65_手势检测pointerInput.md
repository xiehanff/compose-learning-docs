# 第 65 章：手势检测：pointerInput 与手势探测器

## 1. 本章目标

前几章我们只处理了 onClick 这种单点手势。本章解决「点击、拖动、缩放、旋转等手势如何识别」的完整问题。

学完后，你应当能够：

- 理解 `pointerInput` + `awaitPointerEventScope` 的底层事件流
- 用 `detectTapGestures` / `detectDragGestures` 识别点击与拖拽
- 用 `detectTransformGestures` 实现双指缩放、旋转
- 理解 `consume` 与手势竞争，知道拖拽为什么用相对位移

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.ui.input.pointer.pointerInput`
- `androidx.compose.ui.input.pointer.PointerEvent` / `PointerInputChange`
- `androidx.compose.foundation.gestures.detectTapGestures` 等 detect 系列
- `androidx.compose.foundation.gestures.detectTransformGestures`
- `androidx.compose.ui.input.pointer.consume`

## 3. 项目源码片段

场景一：可拖拽 + 双指缩放的图片浏览器。

```kotlin
@Composable
fun GestureImageDemo() {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 4f)
                    offset += pan
                }
            }
            .graphicsLayer {
                translationX = offset.x
                translationY = offset.y
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.demo),
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
    }
}
```

场景二：底层事件流与单指拖拽——看清楚 detect 系列背后的机制。

<!-- compile
prelude: var offset by remember { mutableStateOf(Offset.Zero) }
-->
```kotlin
Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
                if (change.pressed) change.consume()
            }
        }
    }
}

Modifier.pointerInput(Unit) {
    detectDragGestures(
        onDragStart = { offset -> /* 记录起点 */ },
        onDrag = { change, dragAmount ->
            change.consume()        // 消费本次移动，防止手势穿透
            offset += dragAmount    // 相对位移，与按在哪无关
        },
        onDragEnd = { /* 收尾 */ },
    )
}
```

## 4. 逐步解释

底层机制：`Modifier.pointerInput(keys) { ... }` 启动一个手势协程，块内 `awaitPointerEventScope` 里循环调用 `awaitPointerEvent()` 接收 `PointerEvent`——它带 `changes` 列表，每个 `PointerInputChange` 对应一根手指，含 `position`、`pressed` 等状态。**所有 detect 系列函数都是在这套事件流之上封装的高层识别器**：它们内部循环收事件、按模式匹配，再回调给你。

`detectTapGestures`：识别 onTap、onDoubleTap、onLongPress、onPress（按下即回调，可配合 tryAwaitRelease 做按压反馈）。适合按钮、列表项。

`detectDragGestures`：onDragStart / onDrag / onDragEnd。onDrag 的第二个参数 `dragAmount` 就是 `change.positionChange()`——**两次事件之间的相对位移**。必须用相对位移而不是绝对坐标：绝对坐标和手指按在哪有关，相对位移只描述「动了多少」，与起点无关，才能正确累加。

`detectTransformGestures`：同时给 pan（平移）、zoom（缩放）、rotation（旋转）三个增量，适合双指操作。它内部处理了多指与焦点（centroid）计算。

轴向锁定：`detectVerticalDragGestures` / `detectHorizontalDragGestures` 只认单一方向的拖动，常用来区分「列表里竖滑 vs 横滑」。

事件消费与手势竞争：指针事件从最内层节点向外传播，每一层都可以 `change.consume()` 标记「我已处理」。被消费的事件不会再触发外层的手势识别。这就是为什么子级图片拖拽时父级滚动不响应——**谁消费谁赢**。忘了 consume 的典型后果是手势穿透、父子同时响应。

记住：**detect 系列 = 事件流上的模式识别器；consume 决定手势竞争胜负；位移用 positionChange。**

## 5. 实际执行过程

1. 组合阶段 pointerInput 启动协程，await 第一个指针事件
2. 手指按下 → down 事件进入协程，detectDragGestures 触发 onDragStart
3. 手指移动 → 每帧 move 事件，识别器计算 positionChange 得到 dragAmount
4. onDrag 回调里 consume 并更新 offset 状态 → 触发重组
5. 重组后 graphicsLayer 用新 offset 位移，画面跟随手指
6. 双指场景下 detectTransformGestures 同时产出 pan/zoom，scale 相乘后夹取范围
7. 手指抬起/取消 → onDragEnd 收尾；未被 consume 的事件继续向外传播给父级

## 6. Flutter / Dart 3 对照

先讲相似点：Flutter 的 GestureDetector 也覆盖 tap、pan、scale 等手势，且 onPanUpdate 的 delta、onScaleUpdate 的 scale 与 Compose 的 dragAmount、zoom 一一对应。

```dart
GestureDetector(
  onTap: () => _handleTap(),
  onPanUpdate: (details) => setState(() => offset += details.delta),
  onScaleUpdate: (details) => setState(() => scale = details.scale),
  child: child,
)
```

本质区别：Flutter 由 `GestureDetector` 的 arena 机制**自动仲裁**手势——多个手势同时竞争时框架帮你决定谁赢；Compose 则要求你显式进入某种识别（调用 detect*），配合 consume 手动裁决竞争。Compose 更底层、更可控，Flutter 更省心但定制手势时要写自定义 recognizer。另外 Compose 的拖拽增量来自 `positionChange()`，Flutter 是 `details.delta`，语义相同。

记住：**Flutter 自动仲裁，Compose 显式识别 + 手动 consume。**

## 7. 常见错误

1. 忘记 `change.consume()`：手势穿透——子级拖动时父级滚动/点击同时触发，行为错乱。
2. pointerInput 里直接用旧状态：keys 不变时闭包捕获组合那一刻的值，之后更新的状态读不到。用 `rememberUpdatedState` 包装回调，或把依赖放进 keys。
3. 拖动用绝对坐标：`change.position` 与按下位置耦合，累加会跳变；应该用 `positionChange()` 的相对位移。
4. 手势与滚动容器冲突不知道原因：本质是消费问题——子级不 consume 父级会抢，父级先消费子级收不到；需要嵌套滚动协调时用 nestedScroll。
5. 把整个状态对象放进 pointerInput 的 keys：状态每次变化都重启协程，识别器状态丢失、还浪费；正确做法是 rememberUpdatedState。

## 8. 本章检查题

1. detect 系列函数基于什么底层机制？
2. `change.consume()` 的作用是什么？
3. 拖拽为什么用 positionChange 而不是绝对坐标？
4. `pointerInput(keys)` 的 keys 变化时会发生什么？

答案：

1. 基于 `awaitPointerEventScope` 循环接收 PointerEvent 的事件流，detect* 是在其上封装的高层手势识别器。
2. 把本次指针事件标记为「已处理」，阻止它继续向外层传播，从而决定手势竞争的胜负、防止手势穿透。
3. 因为绝对坐标与手指按下位置耦合，无法正确描述「移动了多少」；positionChange 是两次事件间的相对位移，累加结果才稳定。
4. 协程会取消并重启，闭包重新捕获最新的外部值；keys 不变则一直使用旧闭包，可能读到过期状态。

## 9. 本章总结

手势识别的底座是 pointerInput 事件流，detectTapGestures / detectDragGestures / detectTransformGestures 是它上面的模式识别器。写手势代码记住三件事：位移用 positionChange、冲突用 consume 裁决、状态更新用 rememberUpdatedState——拖拽、缩放、旋转这类交互就都能稳稳实现。
