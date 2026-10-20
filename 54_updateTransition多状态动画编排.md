# 第 54 章：updateTransition 多状态动画编排

## 1. 本章目标

学完后，你应当能够：

- 用 `updateTransition` 创建一个 `Transition<T>`，用枚举/密封类作为状态；
- 用 `transition.animateFloat`、`animateColor`、`animateDp` 等子动画声明"每个状态下的目标值"；
- 理解状态切换时所有子动画同步播放的机制；
- 说清 `Transition` 与多个独立 `animate*AsState` 的本质区别：同步编排、状态驱动。

## 2. 项目中的位置

进阶篇不再局限于 compose_demo 项目，本章使用通用 Compose API。

涉及的 androidx 包与官方 API：

- `androidx.compose.animation.core`：`updateTransition`、`Transition`，以及 `animateFloat`、`animateDp` 等子动画扩展（`animateColor` 扩展在 `androidx.compose.animation` 包）、`tween`、`spring`、`AnimationSpec`（动画规格，两者都是第 55 章的主角，本章只用到它们的默认值）
- `androidx.compose.runtime`：`State`、`getValue`/`setValue`、`remember`
- `androidx.compose.ui.graphics`：`graphicsLayer`、`Color`
- `androidx.compose.material.icons`：`Icons`、`Icons.Filled.Pause`（来自扩展图标库 material-icons-extended，`Icons.Filled.PlayArrow` 在核心图标库 material-icons-core；App 工程需引入对应依赖）

## 3. 项目源码片段

场景：一个播放/暂停按钮。状态在两个枚举值之间切换时，图标大小、颜色、缩放三个属性必须**同时、协调地**变化——播放态大而红，暂停态小而蓝。

<!-- compile decls -->
```kotlin
enum class PlayState { Playing, Paused }

@Composable
fun PlayPauseButton(state: PlayState, onToggle: () -> Unit) {
    // 单一状态源：所有子动画都跟随这个状态
    val transition = updateTransition(targetState = state, label = "playState")

    val iconScale by transition.animateFloat(label = "iconScale") { s ->
        if (s == PlayState.Playing) 1.2f else 1f
    }
    val iconColor by transition.animateColor(label = "iconColor") { s ->
        if (s == PlayState.Playing) Color(0xFFFF5252) else Color(0xFF2196F3)
    }
    val iconSize by transition.animateDp(
        transitionSpec = { tween(durationMillis = 400) },  // 动画规格：这里指定 400ms 定时过渡（第 55 章详解）
        label = "iconSize"
    ) { s ->
        if (s == PlayState.Playing) 56.dp else 40.dp
    }

    Icon(
        imageVector = if (state == PlayState.Playing) {
            Icons.Filled.Pause
        } else {
            Icons.Filled.PlayArrow
        },
        contentDescription = if (state == PlayState.Playing) "暂停" else "播放",
        tint = iconColor,
        modifier = Modifier
            .size(iconSize)
            .graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            }
            .clickable(onClick = onToggle)
    )
}
```

调用方只负责维护 `state`：

```kotlin
var playState by remember { mutableStateOf(PlayState.Paused) }
PlayPauseButton(state = playState) { playState = if (playState == PlayState.Playing) PlayState.Paused else PlayState.Playing }
```

## 4. 逐步解释

### 4.1 `Transition<T>` 是什么

`Transition<S>` 是一个**以状态 S 为驱动的动画编排容器**。`updateTransition(targetState = state)` 创建它，并把当前状态交给它。此后，容器内声明的每个子动画都遵循同一套规则：**状态 S 下，各属性取什么值**。

S 通常是枚举或密封类（状态机），也可以是任意类型。关键约束：状态值应该是"稳定的、可比较的"——它描述"界面处于哪种模式"，而不是"当前进度"。

记住：`Transition` 先有"状态"，再有"动画"，动画是状态的呈现。

### 4.2 子动画：`transition.animateXxx`

容器内可以声明任意多个子动画，常见的有：

- `animateFloat`：缩放、透明度、进度；
- `animateColor`：颜色；
- `animateDp`：尺寸、间距；
- `animateSize` / `animateOffset`：复合值。

每个子动画有两个关键部分：

1. **目标值函数**：`{ s -> ... }` 接收当前状态，返回该状态下属性的目标值；
2. **可选动画规格**：`transitionSpec = { tween(durationMillis = 400) }`，不传则默认 `spring()`。这里的"动画规格"（`AnimationSpec`）指的是描述动画"怎么动"的参数集合：时长、缓动曲线、是否回弹；`tween` 是固定时长的规格，`spring` 是物理回弹的规格。规格的完整家族（`spring`、`keyframes`、`repeatable` 等）第 55 章详解，本章只需会给子动画指定一个规格。

```kotlin
val alpha by transition.animateFloat(
    transitionSpec = { tween(durationMillis = 600) },
    label = "alpha"
) { s -> if (s == PlayState.Playing) 1f else 0.3f }
```

注意：子动画的 lambda 是一个**纯函数**——输入状态、输出目标值，不应在里面做副作用（见常见错误 2）。

记住：子动画 = "状态 → 目标值"的映射 + 播放规格。

### 4.3 状态切换时如何同步

当 `updateTransition` 的 `targetState` 变化时，`Transition` 会：

1. 记住**上一状态**（动画起点）与**新状态**（动画终点）；
2. 让所有子动画**同时启动**，从上一状态的目标值过渡到新状态的目标值；
3. 每个子动画使用自己的规格播放，但起点、终点、触发时机完全对齐。

这就是"同步编排"：三个属性不会出现"颜色先变完、尺寸还在动"的割裂感，即使它们的时长不同，也是从同一时刻、朝着同一状态的目标值播放。

还有一点值得单独说：**动画播放途中 `targetState` 再次变化是平滑打断（retarget）**。例如快速连点播放/暂停，第二次切换到来时第一次的动画还没播完，`Transition` 不会等它播完再排队，而是让所有子动画**从当前插值位置**转向新目标。交互上表现为动画永远平滑接续；但任何"动画一定会完整播完一遍"的假设（比如"动画结束后才解锁某个状态"）都不成立。

记住：同步不是"时长相同"，而是"起点终点对齐、同时触发"；中途切换不排队，而是从当前值转向新目标。

### 4.4 与多个独立 `animate*AsState` 的区别

对比写法：

```kotlin
// 各自为政：三个状态各自切换，无法保证同步
val a by animateFloatAsState(if (playing) 1.2f else 1f)
val b by animateColorAsState(if (playing) red else blue)
val c by animateDpAsState(if (playing) 56.dp else 40.dp)
```

差异在三点：

- **状态源**：独立 AsState 各自从自己的布尔条件取目标值，条件分散、易写错；`Transition` 只有一个 `targetState`，所有子动画共享它；
- **同步性**：独立 AsState 各自独立判断"值是否变化、何时启动"，理论上可能不同步；`Transition` 保证同一状态切换驱动所有子动画；
- **可扩展性**：状态机超过两个状态时（如 Loading/Success/Error），`animate*AsState` 的条件表达式迅速膨胀且易错，`Transition` 的"状态 → 各属性值"表格式写法更清晰。

记住：属性多、状态多、要求同步 → `updateTransition`；单个属性、简单布尔切换 → `animate*AsState` 就够。

### 4.5 `label` 与调试

`updateTransition` 和每个子动画都接受 `label`，在 Layout Inspector 中能直观看到"这个 Transition 由谁驱动、子动画当前值"，多状态编排时强烈建议命名。

## 5. 实际执行过程

以播放/暂停按钮为例：

1. 首次组合：`playState = Paused`，`updateTransition` 创建 `Transition`，各子动画初始值即为 Paused 对应的目标值（1f、蓝色、40dp），无动画；
2. 用户点击按钮 → `onToggle()` → `playState = Playing`；
3. 重组中 `updateTransition` 发现 `targetState` 从 Paused 变为 Playing，记录上一状态 Paused；
4. 三个子动画同时启动：`iconScale` 从 1f → 1.2f，`iconColor` 从蓝 → 红，`iconSize` 从 40dp → 56dp（400ms tween），图标同步放大、变色、变大；
5. 各子动画按各自规格播放，视觉上协调推进；到达目标值后动画结束；
6. 再次点击 → `playState` 回到 Paused → 三个子动画从 Playing 的目标值反向过渡回 Paused 的目标值。

整个过程，业务代码只改了一个枚举值。

## 6. Flutter / Dart 3 对照

Flutter 中"多属性随状态同步变化"常见两种写法，一是 `AnimatedContainer`：

```dart
AnimatedContainer(
  duration: const Duration(milliseconds: 400),
  width: state == PlayState.playing ? 56 : 40,
  height: state == PlayState.playing ? 56 : 40,
  color: state == PlayState.playing ? Colors.red : Colors.blue,
  child: Icon(
    state == PlayState.playing ? Icons.pause : Icons.play_arrow,
    size: state == PlayState.playing ? 48 : 32,
  ),
)
```

二是用一个 `AnimationController` 驱动多个 `Tween`：

```dart
final scale = Tween<double>(begin: 1, end: 1.2).animate(controller);
final color = ColorTween(begin: Colors.blue, end: Colors.red).animate(controller);
// 切换时 controller.forward() 或 controller.reverse()
```

相似点：

- 都是"声明属性目标值，框架负责过渡"；
- 都能让多个属性随同一个状态/进度变化；
- 都有时长与曲线控制（`duration` + `Curves` ≈ `tween` 的 `durationMillis` + `easing` 缓动曲线；缓动曲线控制动画速度随时间变化，第 55 章详解）。

本质区别：

- **驱动方式**：Compose 是**状态驱动（声明式）**——把"状态 → 各属性目标值"写清楚，状态切换自动播放；Flutter 常见做法是**手动 forward/reverse**，需要代码显式触发方向；
- **多属性组织**：Flutter 用单个 `AnimationController` 才能保证多属性同步（多个独立 `AnimatedXxx` 各自计时）；Compose 一个 `Transition` 天然保证同步，不需要额外对象；
- **状态机扩展**：Compose 的状态可以是任意枚举，天然支持三态以上；Flutter 的 controller 是连续进度，多状态需要自己映射区间。

| Compose | Flutter |
|---|---|
| `updateTransition(targetState)` | `AnimationController` + 状态判断 |
| `transition.animateColor { s -> ... }` | `ColorTween(...).animate(controller)` |
| 状态切换自动播放 | `controller.forward()` / `reverse()` |
| 枚举状态机天然支持 | 需自行把状态映射为进度 |

## 7. 常见错误

### 错误 1：每个子动画用独立状态，导致不同步

```kotlin
// 错误：三个布尔各管各的，切换时机不一致
var big by remember { mutableStateOf(false) }
var red by remember { mutableStateOf(false) }
var wide by remember { mutableStateOf(false) }
```

属性越多越难保证同步。应把多个布尔合并成一个状态机枚举，交给 `updateTransition` 统一驱动。

### 错误 2：在 `animateXxx` 的 lambda 里做副作用

```kotlin
// 错误：lambda 是纯函数，可能在动画期间被多次求值
val a by transition.animateFloat(label = "x") { s ->
    Log.d("tag", "求值")   // 副作用
    if (s == PlayState.Playing) 1f else 0f
}
```

目标值函数应保持纯函数：只根据状态返回数值。副作用放到 `SideEffect`、`LaunchedEffect` 中。

### 错误 3：依赖"动画会完整播完"的假设

```kotlin
// 错误假设：动画播放途中再次切换状态，会等旧动画播完再播新的
// 实际行为：所有子动画立即从当前值转向新目标（平滑打断），不排队、不闪跳
PlayPauseButton(state = playState, onToggle = { /* 快速连续切换 */ })
```

"中途切换"本身不是错误，这是 `Transition` 的预期行为；错的是把业务时序建立在"动画一定完整播一遍"上（例如认为某个状态只在动画终点才出现）。需要严格的顺序编排（播完再做事）应使用第 52 章的 `Animatable`。顺带澄清一个常见误解：`transitionSpec = { tween(400) }` 每次求值返回新对象并不会导致动画重启或抖动——是否启动新动画由 `targetState` 变化决定，相同参数的 tween 行为一致；把规格提取为常量只是减少对象创建、提高可读性的建议。

### 错误 4：把高频变化的值当 `targetState`

```kotlin
// 错误：每次重组 targetState 都不同，动画永远在重启
updateTransition(targetState = System.currentTimeMillis())
```

`targetState` 应是稳定的模式状态（枚举/密封类），不是连续变化的数值。

## 8. 本章检查题

1. `Transition` 的 `targetState` 应该是什么类型的值？
2. 子动画是如何保持同步的？
3. 与多个独立 `animate*AsState` 相比，`updateTransition` 的核心优势是什么？

答案：

1. 稳定的状态值，通常用枚举或密封类描述界面模式，不能是连续变化的值。
2. 状态切换时，`Transition` 记录上一状态与当前状态，所有子动画从同一时刻、从上一状态的目标值向新状态的目标值过渡，起点终点和触发时机对齐；若动画途中 `targetState` 再次变化，则从当前插值位置平滑转向新目标，不排队。
3. 单一状态源 + 同步编排：所有子动画共享一个 `targetState`，切换一次全部同步播放；状态增多时写法仍是清晰的"状态 → 各属性值"映射，而独立 AsState 的条件表达式会膨胀且难以保证同步。

## 9. 本章总结

`updateTransition` 把动画从"单个属性跟随单个条件"升级为"整组属性跟随一个状态机"：状态切换一次，所有子动画同步编排播放；动画途中再次切换则从当前值平滑转向新目标。它与 `animate*AsState` 的分工是"一个属性、简单切换"用后者，"多属性、多状态、要求同步"用前者。这是 Compose 声明式动画中组织能力最强的入口之一。

官方参考：

- [updateTransition（Animation in Compose）](https://developer.android.com/develop/ui/compose/animation)
- [updateTransition API](https://developer.android.com/reference/kotlin/androidx/compose/animation/core/updateTransition)
