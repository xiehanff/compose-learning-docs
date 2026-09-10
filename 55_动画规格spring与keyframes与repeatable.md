# 第 55 章：动画规格：tween、spring、keyframes、repeatable 与 easing

## 1. 本章目标

学完后，你应当能够：

- 理解 `AnimationSpec` 家族：`tween`、`spring`、`keyframes`、`repeatable`、`infiniteRepeatable` 各自的适用场景；
- 区分常用 easing 曲线，并会用 `CubicBezierEasing` 自定义曲线；
- 用 `keyframes` 实现分段时间控制的分段动画；
- 说清 `repeatable` 与 `infiniteRepeatable` 的区别，以及 `togetherWith` 的作用。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的 androidx 包与官方 API：

- `androidx.compose.animation.core`：`AnimationSpec`、`tween`、`spring`、`keyframes`、`repeatable`、`infiniteRepeatable`、`RepeatMode`、`FastOutSlowInEasing`、`LinearEasing`、`CubicBezierEasing`、`Spring`、`rememberInfiniteTransition`（无限动画入口，第 56 章详解）
- `androidx.compose.animation`：`togetherWith`、`animateFloatAsState`

## 3. 项目源码片段

场景一：点赞图标回弹——短距离、需要"过冲再回弹"的物理手感，用 `spring`。

```kotlin
val scale by animateFloatAsState(
    targetValue = if (liked) 1.15f else 1f,
    animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    ),
    label = "likeBounce"
)
```

场景二：加载进度条——前段快、中段慢、后段加速的"变速"效果，用 `keyframes` 分段控制。

```kotlin
val progress by animateFloatAsState(
    targetValue = if (loading) 1f else 0f,
    animationSpec = keyframes {
        durationMillis = 2000               // 总时长必须显式设置
        0f at 0 with LinearEasing
        0.4f at 500 with FastOutSlowInEasing
        0.7f at 1200 with LinearEasing
        1f at 2000
    },
    label = "loadingProgress"
)
```

场景三：呼吸灯——透明度在 0.2 与 1 之间无限往返，用 `infiniteRepeatable` + `RepeatMode.Reverse`。这里的"无限动画"指不会自己结束、一直循环播放的动画；普通 `animate*AsState` 只会播到目标值就停，想让它一直转需要换个容器——`rememberInfiniteTransition` 就是专门持续驱动无限动画的入口（第 56 章详解），本章只需用它把 `infiniteRepeatable` 跑起来。

```kotlin
val alpha by rememberInfiniteTransition(label = "breath").animateFloat(
    initialValue = 0.2f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 1000, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse
    ),
    label = "alpha"
)
```

## 4. 逐步解释

### 4.1 `tween`：最基础的定时动画

```kotlin
tween(
    durationMillis = 300,               // 总时长
    delayMillis = 0,                    // 延迟开始
    easing = FastOutSlowInEasing        // 缓动曲线
)
```

`tween` 的含义是"在固定时长内，从起点插值到终点"。`delayMillis` 让动画延迟启动，适合"等一拍再动"的节奏设计。

记住：`tween` 是时间驱动的——时长固定，无论起点终点距离多大，播放时间都一样。

### 4.2 easing：控制"速度分布"而非"时长"

easing 描述的是**单位时间内的速度变化**：同样 300ms，起点快终点慢和匀速，观感完全不同。常用曲线：

| 曲线 | 效果 |
|---|---|
| `FastOutSlowInEasing` | 快入慢出，Material 默认手感 |
| `LinearEasing` | 匀速，适合进度条、无限循环 |
| `FastOutLinearInEasing` | 快入线性出 |
| `LinearOutSlowInEasing` | 线性入慢出 |
| `CubicBezierEasing(x1, y1, x2, y2)` | 用两个控制点的四个坐标自定义曲线（贝塞尔曲线，第 59 章详解） |

easing 只影响速度分布，**不改变总时长**——这是新人最容易混淆的点。表中的"贝塞尔曲线"指用控制点描述的光滑曲线（第 59 章详解），本章只需知道 `CubicBezierEasing` 的四个数字是**两个控制点的坐标**（起点 (0,0) 与终点 (1,1) 固定不动），曲线形状由这两个控制点共同决定。

记住：easing 管"怎么动"，duration 管"动多久"，两者正交。

### 4.3 `spring`：物理弹性动画

```kotlin
spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,   // 阻尼比
    stiffness = Spring.StiffnessMedium                // 刚度
)
```

两个核心参数：

- **`dampingRatio`（阻尼比）**：0 无阻尼（永不停止地振荡），0 到 1 之间产生回弹（过冲后衰减），1 临界阻尼（最快到达且不过冲），大于 1 过阻尼（缓慢逼近）；
- **`stiffness`（刚度）**：弹簧硬度，越大回弹越快。

`spring` 的时长不是固定的：由初始距离、刚度、阻尼共同决定。它适合**跟手**（拖拽松手回弹）和**短距离回弹**（点赞、开关），天然有"物理感"。

记住：`spring` 是物理驱动的——时长由物理参数推导，适合短距离跟手与回弹。

### 4.4 `keyframes`：分段控制

`keyframes` 把动画拆成若干段，每段指定"在哪个时间点到达什么值、用什么曲线"：

```kotlin
keyframes {
    durationMillis = 2000    // 总时长（必填）
    0f at 0 with LinearEasing
    0.4f at 500 with FastOutSlowInEasing
    0.7f at 1200 with LinearEasing
    1f at 2000
}
```

注意 `at` 后面是**绝对毫秒位置**（相对总时长），不是百分比；段与段之间自动插值。适合"先快后慢再冲刺"这种非均匀节奏，或"前 70% 是 A 动作、后 30% 是 B 动作"的编排。

记住：`keyframes` 是"时间点 → 值 → 曲线"的表格，`durationMillis` 必须设置。

### 4.5 `repeatable` 与 `infiniteRepeatable`

- `repeatable(iterations, animation, repeatMode)`：循环**有限次数**，结束后停在最终值；
- `infiniteRepeatable(animation, repeatMode)`：**无限循环**，只能在无限动画上下文（如 `rememberInfiniteTransition`，第 56 章详解）中使用。

两者都有 `repeatMode`：

- `RepeatMode.Restart`：每次从头播；
- `RepeatMode.Reverse`：正播-倒播往返。

无限动画本身是一个独立话题，第 56 章会完整展开，这里先记住入口与区别。

记住：`repeatable` 会结束，`infiniteRepeatable` 永不结束。

### 4.6 `togetherWith`：进入/退出过渡的组合

`togetherWith` 属于过渡（Transition）领域：把进入过渡与退出过渡绑成一对，用于 `AnimatedContent` 的 `transitionSpec`（第 53 章已见）。它本身不是 `AnimationSpec`，但常与 `tween` 等配合使用：

```kotlin
transitionSpec = {
    (slideInHorizontally { it / 2 } + fadeIn())
        .togetherWith(slideOutHorizontally { -it / 2 } + fadeOut())
}
```

记住：规格是"一个值怎么动"，`togetherWith` 是"新旧两个内容怎么配合动"。

## 5. 实际执行过程

以场景二进度条为例：

1. `loading = true` → `targetValue = 1f`，启动 `keyframes` 动画；
2. 0~500ms：值从 0 到 0.4，线性匀速，进度条快速前进；
3. 500~1200ms：值从 0.4 到 0.7，`FastOutSlowInEasing`，先快后慢，进度条减速；
4. 1200~2000ms：值从 0.7 到 1，线性匀速，进度条再次匀速冲刺；
5. 到达 1f 后动画结束；`loading` 变 false 时反向播放同样的分段。

场景三呼吸灯则不同：`rememberInfiniteTransition` 驱动 `infiniteRepeatable`，透明度在 0.2 ↔ 1 之间往复，动画永不结束，直到该 Composable 离开组合。

## 6. Flutter / Dart 3 对照

Flutter 中的对应关系：

```dart
// easing → CurvedAnimation
CurvedAnimation(parent: controller, curve: Curves.easeInOut);

// keyframes → TweenSequence（按权重分段）
TweenSequence<double>([
  TweenSequenceItem(tween: Tween(begin: 0, end: 0.4), weight: 25),
  TweenSequenceItem(tween: Tween(begin: 0.4, end: 0.7), weight: 35),
  TweenSequenceItem(tween: Tween(begin: 0.7, end: 1.0), weight: 40),
]);

// spring → SpringSimulation
SpringSimulation(
  spring: const SpringDescription(mass: 1, stiffness: 100, damping: 10),
  initialVelocity: 0,
);
```

相似点：

- 都有"时长 + 曲线"的模型（`tween` + easing ≈ `Duration` + `Curves`）；
- 都支持分段动画（`keyframes` ≈ `TweenSequence`）；
- 都提供物理弹簧模型（`spring` ≈ `SpringSimulation`）。

本质区别：

- **分段方式**：Compose `keyframes` 用绝对毫秒时间点；Flutter `TweenSequence` 用**权重**（百分比）分段，总时长由 controller 决定；
- **物理模型**：Compose `spring` 的参数是阻尼比+刚度（无量纲、直观）；Flutter `SpringSimulation` 是质量+刚度+阻尼（SI 单位）；
- **声明层级**：Compose 的规格是独立对象，可直接传给 `animate*AsState`、`Animatable`、`Transition` 等任意动画入口；Flutter 的曲线/序列必须挂到 `AnimationController` 上才有意义。

| Compose | Flutter |
|---|---|
| `tween(durationMillis, easing)` | `Duration` + `CurvedAnimation` |
| `keyframes { ... at ... with ... }` | `TweenSequence` + 权重 |
| `spring(dampingRatio, stiffness)` | `SpringSimulation` |
| `repeatable` / `infiniteRepeatable` | `controller.repeat()` / `Repeat` |
| `FastOutSlowInEasing` | `Curves.easeOut` 等 |

## 7. 常见错误

### 错误 1：所有动画都用默认 `tween`

默认 `FastOutSlowInEasing` 是"端庄"的 Material 手感，但不适合跟手、回弹场景。拖拽回弹、点赞这类需要物理感的动画应使用 `spring`，否则显得"死板"。

### 错误 2：`spring` 用于长距离位移导致振荡突兀

```kotlin
// 1000px 长位移 + 低阻尼 → 大幅过冲振荡，观感突兀
spring(dampingRatio = 0.2f, stiffness = Spring.StiffnessLow)
```

`spring` 适合短距离。长距离位移用 `tween` + 缓动，或把 `dampingRatio` 提高到临界阻尼附近。

### 错误 3：`keyframes` 忘记设置 `durationMillis`

`keyframes` 块中 `durationMillis` 是必填的（它决定 `at` 时间点的绝对基准），漏写会编译报错或无法定义时长。

### 错误 4：混淆 `repeatable` 与 `infiniteRepeatable`

```kotlin
// 错误：infiniteRepeatable 用于普通 animate*AsState 会报错/无法工作
animateFloatAsState(1f, infiniteRepeatable(tween(500)))
```

`infiniteRepeatable` 只能用于无限动画上下文（`rememberInfiniteTransition` 等，第 56 章详解）；有限次循环用 `repeatable(iterations, ...)`。

### 错误 5：把 easing 当"时长"

"我想让动画慢一点所以换 easing"——easing 不改变总时长，只改变速度分布。想变慢应增大 `durationMillis`。

## 8. 本章检查题

1. `spring` 的两个核心参数各控制什么？
2. `keyframes` 如何定义分段？`at` 后面的数字是什么单位？
3. `infiniteRepeatable` 与 `repeatable` 的区别是什么？

答案：

1. `dampingRatio`（阻尼比）控制回弹程度：0 到 1 有振荡、1 临界、大于 1 过阻尼；`stiffness`（刚度）控制弹簧硬度，越大回弹越快。
2. 在 `keyframes { durationMillis = ... }` 中写 `值 at 时间点 with 曲线`；`at` 后是相对总时长的绝对毫秒位置，不是百分比。
3. `repeatable` 循环有限次数后结束并停在最终值；`infiniteRepeatable` 永不结束，只能用于无限动画上下文（如 `rememberInfiniteTransition`）；两者都可通过 `RepeatMode.Restart`/`Reverse` 控制往返方式。

## 9. 本章总结

`AnimationSpec` 家族是 Compose 动画的"物理/时间引擎选择器"：`tween` 管固定时长的缓动、`spring` 管物理回弹、`keyframes` 管分段节奏、`repeatable`/`infiniteRepeatable` 管循环。选规格的核心是匹配场景手感——跟手回弹用 spring、节奏编排用 keyframes、匀速无限循环用 tween + LinearEasing。规格与动画入口（AsState、Animatable、Transition）正交组合，构成了 Compose 完整的动画能力。
