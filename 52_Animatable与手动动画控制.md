# 第 52 章：Animatable 与手动动画控制

## 1. 本章目标

学完后，你应当能够：

- 使用 `Animatable` 手动控制动画：`snapTo` 立即跳转、`animateTo` 在协程中播放动画；
- 理解 `AnimationVector` 的作用，知道常见类型（Float、Offset、Color）如何映射为向量；
- 用 `LaunchedEffect` + `Animatable` 实现「点击 → 播放一段动画 → 执行回调」；
- 说清 `Animatable` 与 `animate*AsState` 的分工：隐式声明 vs 显式控制。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的 androidx 包与官方 API：

- `androidx.compose.animation.core`：`Animatable`、`spring`、`tween`、`AnimationVector`、`Spring`
- `androidx.compose.runtime`：`remember`、`rememberCoroutineScope`、`LaunchedEffect`
- `androidx.compose.foundation.gestures`：`detectDragGestures`
- `androidx.compose.ui.input.pointer`：`pointerInput`
- `androidx.compose.ui.unit`：`IntOffset`、`dp`
- `androidx.compose.foundation`：`background`、`shape`

## 3. 项目源码片段

场景一：点击按钮后，先播放一段 1 秒的位移动画，动画结束后才执行回调（例如弹出提示）。

```kotlin
@Composable
fun AnimateThenCallback(onFinished: () -> Unit) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Button(
        onClick = {
            scope.launch {
                offsetX.snapTo(0f)                    // 立即归位，无动画
                offsetX.animateTo(                    // 协程中播放动画
                    targetValue = 300f,
                    animationSpec = tween(durationMillis = 1000)
                )
                onFinished()                          // 动画结束后的回调
            }
        }
    ) {
        Text("播放并回调")
    }

    Text(
        "★",
        fontSize = 48.sp,
        modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
    )
}
```

场景二：拖拽小球松手后回弹到原点。这里 `onDragEnd` 不是挂起环境，用 `rememberCoroutineScope` 启动协程。

```kotlin
@Composable
fun DraggableBall() {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount.x) }
                    },
                    onDragEnd = {
                        scope.launch {
                            offsetX.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )
                            )
                        }
                    }
                )
            }
    ) {
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .size(64.dp)
                .background(Color(0xFFFF5252), CircleShape)
        )
    }
}
```

## 4. 逐步解释

### 4.1 `Animatable` 是什么

`Animatable` 是一个**可手动驱动的动画值对象**：它自己持有当前值，并提供 `snapTo`、`animateTo` 等方法改变这个值。常见类型的顶层工厂函数自带类型转换器，所以写法很简洁：

```kotlin
val f = remember { Animatable(0f) }                    // Animatable<Float, AnimationVector1D>
val c = remember { Animatable(Color.White) }           // Animatable<Color, AnimationVector4D>
val o = remember { Animatable(Offset.Zero) }           // Animatable<Offset, AnimationVector2D>
```

记住：`Animatable` 是"对象"，要 `remember` 住；`animate*AsState` 是"函数"，自带 remember。

### 4.2 `AnimationVector` 与类型适配

动画系统需要对数值做插值运算，而 `Color`、`Offset` 不是数字。`AnimationVector` 就是把任意类型映射成"数字向量"的桥：

| 值类型 | 向量类型 | 含义 |
|---|---|---|
| `Float` | `AnimationVector1D` | 一维：一个数 |
| `Offset` | `AnimationVector2D` | 二维：x、y |
| `Color` | `AnimationVector4D` | 四维：r、g、b、a |

`Animatable<T, V>` 的两个泛型参数就是"业务值类型"和"向量类型"。绝大多数情况下我们不需要手动写 `TwoWayConverter`，用顶层工厂函数即可。

记住：`AnimationVector` 负责"把颜色/偏移拆成数字来插值"，平时不用直接碰它。

### 4.3 `snapTo` 与 `animateTo`

- `snapTo(targetValue)`：**立即**跳到目标值，不播放过渡，用于归位、初始化；
- `animateTo(targetValue, animationSpec)`：**播放**一段动画到达目标值，动画结束后返回。

两个都是**挂起函数**，只能在协程中调用——因为 `animateTo` 要挂起当前协程直到动画完成，这正是"动画结束后执行回调"这一模式的来源：

```kotlin
scope.launch {
    offsetX.animateTo(300f, tween(1000))
    onFinished()   // 动画完成才执行
}
```

记住：`snapTo` 是瞬移，`animateTo` 是播放，且都在协程里。

### 4.4 与协程环境配合

在 `LaunchedEffect` 中可以直接调用（它自带协程作用域）；在普通点击回调、手势回调等非挂起环境，用 `rememberCoroutineScope()` 取得作用域再 `launch`。注意：`scope.launch` 的协程在 Composable 离开组合时会被自动取消，这正是我们想要的生命周期绑定。

### 4.5 读取值的两种姿势

读取 `offsetX.value` 有两种方式：

- 直接在组合中读（如 `Text(offsetX.value)`）→ 值变化触发重组；
- 用 `Modifier.offset { IntOffset(...) }` 这种 **lambda 版 modifier** → 只更新布局偏移，不触发重组，性能更好。

拖拽场景请用第二种。记住：动画值怎么读，决定了它影响重组还是只影响绘制/布局。

### 4.6 与 `animate*AsState` 的分工

- `animate*AsState`：**隐式**——声明目标值，动画由状态变化自动触发，适合"状态切换"场景；
- `Animatable`：**显式**——自己决定何时、以何种规格、播放到哪个值，适合"需要多次复用、需要精确控制时机、需要在动画后做事"的场景。

两者底层是同一套动画引擎，`animate*AsState` 内部就持有 `Animatable`。

记住：要"跟随状态自动动"用 AsState，要"自己掌控节奏"用 Animatable。

## 5. 实际执行过程

以场景一为例：

1. 首次组合：`remember { Animatable(0f) }` 创建动画值对象，值为 0f；
2. 用户点击按钮 → `onClick` 中 `scope.launch { ... }` 启动协程；
3. `snapTo(0f)`：立即归位（本例已是 0f，无视觉效果）；
4. `animateTo(300f, tween(1000))`：协程挂起，动画系统逐帧更新值 0f → 300f，`Modifier.offset` 每帧重算偏移，星标向右移动；
5. 1 秒后值到达 300f，`animateTo` 返回，协程继续；
6. `onFinished()` 执行，弹出提示；协程结束。

整个过程是**顺序执行**的：动画未完成，回调不会提前触发。

## 6. Flutter / Dart 3 对照

Flutter 的 `AnimationController` + `Tween` 与 `Animatable` 对应：

```dart
class _DemoState extends State<Demo> with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  late final Animation<double> _offset;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1000),
    );
    _offset = Tween<double>(begin: 0, end: 300).animate(_controller);
  }

  void _play() {
    _controller.forward().then((_) {
      // 动画完成后的回调
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }
}
```

相似点：

- 都支持"播放到某个目标值，结束后做后续动作"（`animateTo` 挂起返回 ≈ `forward().then(...)`）；
- 都支持立即跳转（`snapTo` ≈ `controller.value = x` 或 `jumpTo`）；
- 都支持 spring 物理动画（Compose `spring` ≈ Flutter `SpringSimulation`）。

本质区别：

- **对象数量**：Flutter 需要 `AnimationController`（进度）与 `Tween`（值映射）两个对象配合；Compose `Animatable` 一个对象兼任两者，类型转换靠 `AnimationVector` 自动完成；
- **生命周期**：Flutter 的 controller 要手动 `dispose` 并声明 `SingleTickerProviderStateMixin`；Compose 的 `Animatable` 由 `remember` 托管，随组合销毁自动回收；
- **方向控制**：Flutter 常用 `forward()`/`reverse()` 区分方向；Compose 的 `animateTo` 一个函数自动判断方向（从当前值到目标值）。

| Compose | Flutter |
|---|---|
| `Animatable(0f)` | `AnimationController` + `Tween(begin: 0, ...)` |
| `snapTo(x)` | `controller.value = x` / `jumpTo` |
| `animateTo(x, tween(...))` | `controller.forward()` / `animateTo` |
| 挂起返回后执行回调 | `forward().then(...)` |
| `spring(dampingRatio, stiffness)` | `SpringSimulation` |

## 7. 常见错误

### 错误 1：忘记 `remember` 包住 `Animatable`

```kotlin
// 错误：每次重组都新建 Animatable，值归零、动画重启
val offsetX = Animatable(0f)
```

必须 `remember { Animatable(0f) }`，让对象跨重组存活。

### 错误 2：在非协程环境调用 `animateTo` / `snapTo`

```kotlin
// 编译错误：挂起函数只能在协程中调用
button.onClick { offsetX.animateTo(300f) }
```

`animateTo`、`snapTo` 都是挂起函数。普通回调里用 `rememberCoroutineScope().launch { ... }` 包起来。

### 错误 3：`snapTo` 与 `animateTo` 混用导致跳变

拖拽中若某处误用 `animateTo` 追赶手指位置，会产生"永远追不上、来回震荡"的视觉；正确做法是拖拽中 `snapTo` 跟手、松手后 `animateTo` 回弹。两者职责别混。

### 错误 4：在动画中途重复触发 `animateTo`

再次调用 `animateTo` 会**取消**前一个动画并从当前值开始新动画。如果用户连点按钮，动画会不断重启、永远到不了终点。需要"点一次播一次"时，加个状态锁或在动画结束后再允许下次触发。

## 8. 本章检查题

1. `snapTo` 与 `animateTo` 的区别是什么？
2. 为什么 `animateTo` 必须在协程中调用？
3. `Animatable` 与 `animate*AsState` 的分工如何理解？

答案：

1. `snapTo` 立即跳转不播放过渡；`animateTo` 播放完整动画，挂起直到到达目标值才返回。
2. 因为它是挂起函数：`animateTo` 需要挂起当前协程等待动画完成，只有协程能提供这种"暂停-恢复"能力，这也让"动画结束后执行回调"可以按顺序书写。
3. `animate*AsState` 是隐式的：目标值随状态自动变化、自动播放；`Animatable` 是显式的：由代码决定何时、以何种规格播放到哪个值，适合复用与精确控制，且必须由 `remember` 管理生命周期。

## 9. 本章总结

`Animatable` 把动画从"状态变化自动触发"升级为"代码显式驱动"：`snapTo` 瞬移、`animateTo` 播放且挂起等待完成，配合协程可以精确编排"先播放、后回调"的时序。理解它与 `animate*AsState` 的分工（隐式 vs 显式）后，就能按场景选择正确的动画控制方式。
