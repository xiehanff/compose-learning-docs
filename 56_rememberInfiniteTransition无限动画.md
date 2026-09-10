# 第 56 章：rememberInfiniteTransition 与无限动画

## 1. 本章目标

学完后，你应当能够：

- 用 `rememberInfiniteTransition` 编写加载转圈、呼吸灯这类永不停止的动画；
- 区分 `infiniteRepeatable`（无限）与 `repeatable`（有限次数）两种规格；
- 理解 `graphicsLayer` 为什么是动画的首选载体（不触发布局，成本低）；
- 说清楚动画与组合生命周期的关系：组合销毁时动画自动取消，无需手动清理。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

- `androidx.compose.animation`：`rememberInfiniteTransition`、`InfiniteTransition`
- `androidx.compose.animation.core`：`infiniteRepeatable`、`tween`、`RepeatMode`、`LinearEasing`、`FastOutSlowInEasing`
- `androidx.compose.ui`：`Modifier.graphicsLayer`
- `androidx.compose.material3`：`Icon`、`MaterialTheme`

## 3. 项目源码片段

加载图标无限旋转 + 呼吸灯透明度，两个效果共用同一个 `InfiniteTransition`，全部通过 `graphicsLayer` 施加，不触发任何重组外的布局开销：

<!-- compile decls -->
```kotlin
@Composable
fun LoadingIndicator() {
    // 入口：一个驱动多条无限动画的"引擎"
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    // 0f -> 360f 无限旋转，每次从头开始
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // 0.2f -> 1f 呼吸透明度，到达目标后反向
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = "加载中",
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                rotationZ = rotation   // 只改绘制层，不触发布局
                this.alpha = alpha
            },
        tint = MaterialTheme.colorScheme.primary
    )
}
```

## 4. 逐步解释

1. **入口 `rememberInfiniteTransition`**。它返回一个 `InfiniteTransition` 对象，负责在组合存活期间持续驱动动画帧。注意它本身是 `remember` 的——重组时不会重建，否则动画会从零开始抖动。记住：一个无限动画组共用一个 `InfiniteTransition`，不要为每个属性单独建一个。

2. **`animateFloat` 返回一个 `State<Float>`**。签名是 `initialValue`、`targetValue`、`animationSpec`、`label`。返回值是 `State<Float>`，可以用 `by` 委托解包成 `Float` 直接读取。`label` 用于调试（Layout Inspector 里能看到动画名），写上有百利无一害。

3. **`infiniteRepeatable` 是关键规格**。普通 `repeatable` 需要 `iterations` 参数，转完指定次数就停；`infiniteRepeatable` 没有次数概念，永远循环。配合 `RepeatMode.Restart`（到头后跳回起点）或 `RepeatMode.Reverse`（到头后反向播放），就能覆盖"转圈"和"呼吸"两类常见效果。记住：无限动画必须用 `infiniteRepeatable`。

4. **`graphicsLayer` 让动画零布局成本**。旋转、透明度、缩放都属于绘制属性，写在 `graphicsLayer { }` 里只更新 RenderNode 的绘制参数，Compose 可以跳过 measure 和 layout，只走 draw 阶段。如果在动画里直接改 `Modifier.size`、`padding` 之类的布局属性，每一帧都会触发整棵子树的重新测量，掉帧是必然的。记住：动画能走 `graphicsLayer` 就别走布局属性。

5. **不要用 `LaunchedEffect` + `while (true)` 手写循环**。手动循环需要自己 `delay`、自己算进度、自己把值写进 State，每帧还触发一次重组，代码又丑又容易漏掉取消。`InfiniteTransition` 内部有专门的帧回调机制，值的更新不会引发无关重组。记住：无限动画的标准答案是 `rememberInfiniteTransition`，不是 while 循环。

6. **自动清理**。`InfiniteTransition` 的生命周期绑定组合：当包含它的 composable 从组合中移除（比如列表项被删除、页面被导航走），动画帧回调自动停止，无需 `DisposableEffect` 手动收尾。这是声明式框架帮你兜底的典型场景。

## 5. 实际执行过程

1. `LoadingIndicator()` 首次进入组合，`rememberInfiniteTransition` 创建 `InfiniteTransition`，注册帧回调。
2. 两条 `animateFloat` 按各自规格启动：旋转从 0f 匀速走向 360f，透明度从 0.2f 走向 1f。
3. 每帧回调把当前进度写入对应的 `State<Float>`；`graphicsLayer` 的 `rotationZ`、`alpha` 读取新值，仅触发该节点的 draw。
4. 旋转到达 360f 后，`RepeatMode.Restart` 让它跳回 0f 重新开始；透明度到达 1f 后，`RepeatMode.Reverse` 让它反向回到 0.2f，如此往复。
5. 当页面退出、`LoadingIndicator` 从组合移除时，帧回调自动停止，动画结束，无残留。

## 6. Flutter / Dart 3 对照

相似点：Flutter 的 `AnimationController.repeat()` 与 Compose 的 `infiniteRepeatable` 完全等价，都是"无限循环的动画驱动器"；`RepeatMode.Restart/Reverse` 对应 Flutter 的 `repeat()` 的 `reverse` 参数。`graphicsLayer` 的旋转则对应 `RotationTransition`。

```dart
class _LoadingIndicatorState extends State<LoadingIndicator>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    )..repeat(); // 等价于 infiniteRepeatable + Restart
  }

  @override
  void dispose() {
    _controller.dispose(); // 必须手动释放，否则泄漏
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RotationTransition(
      turns: _controller, // 0.0 ~ 1.0，等价于 0f ~ 360f
      child: const Icon(Icons.refresh),
    );
  }
}
```

本质区别：Flutter 要求你在 `dispose()` 里手动调用 `_controller.dispose()`，忘记写就是内存泄漏；Compose 把清理绑定进组合生命周期，`InfiniteTransition` 随组合销毁自动取消。另外 Flutter 通常把旋转/透明度包成 `RotationTransition`、`FadeTransition` 这样的独立 Widget，Compose 则倾向于一个 `graphicsLayer` 同时搞定多个绘制属性，写法更集中。

## 7. 常见错误

1. **用 `LaunchedEffect` + `while (true)` 手写动画循环**。每帧 `delay(16)` 自己算进度、自己写 State，触发整棵树重组，且取消逻辑要自己写。应改用 `rememberInfiniteTransition`。
2. **在动画回调里直接改 `State`**。比如 `while` 循环里 `counter++` 写进 `mutableStateOf`，每帧重组整个 composable，子组件全部重新执行。`graphicsLayer` 的绘制属性更新不触发重组，是正确做法。
3. **把 `infiniteRepeatable` 写成 `repeatable`**。`repeatable(iterations = 3)` 转三圈就停了，加载图标转三圈停住，用户会以为应用卡死。
4. **动画属性放在布局 modifier 上**。把旋转值写进 `Modifier.rotate(rotation)` 虽然也能转，但会走布局阶段；高频场景应统一走 `graphicsLayer`。
5. **给每条动画单独 `rememberInfiniteTransition`**。多个无限动画各自驱动一个帧循环，浪费性能；应共用一个 transition，用 `label` 区分。

## 8. 本章检查题

1. `infiniteTransition.animateFloat` 的进度范围是什么？如何控制成别的区间？
2. 为什么无限旋转用 `graphicsLayer` 而不是改布局属性？
3. 包含 `rememberInfiniteTransition` 的组合被销毁后，动画会发生什么？
4. `infiniteRepeatable` 与 `repeatable` 的区别是什么？

答案：

1. 范围就是 `initialValue` 到 `targetValue`。写 `0f..360f` 得到 0 到 360 的进度；写 `0f..1f` 就得到 0 到 1 的归一化进度，配合 `graphicsLayer` 的 `rotationZ = it * 360f` 使用。
2. 旋转、透明度、缩放属于绘制属性，`graphicsLayer` 只更新绘制层，跳过 measure/layout，成本低且不引发重组；改布局属性则每帧触发整棵子树重新测量。
3. 帧回调自动停止，动画自动取消，无需手动清理。这是 `InfiniteTransition` 绑定组合生命周期的结果。
4. `repeatable` 需要 `iterations` 次数参数，播完指定次数即停止；`infiniteRepeatable` 无次数限制，永远循环，适合加载、呼吸类效果。

## 9. 本章总结

无限动画的标准入口是 `rememberInfiniteTransition`：它通过 `infiniteRepeatable` 规格让 `animateFloat` 永不停止，配合 `RepeatMode.Restart/Reverse` 实现转圈与呼吸两种经典效果。动画值尽量通过 `graphicsLayer` 施加到绘制层，既避开布局开销也避免无谓重组；组合销毁时动画自动取消，你不需要写任何清理代码。
